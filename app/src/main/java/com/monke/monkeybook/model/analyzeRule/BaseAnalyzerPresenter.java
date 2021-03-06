package com.monke.monkeybook.model.analyzeRule;

import android.util.Log;

import androidx.annotation.NonNull;

import com.monke.monkeybook.bean.BookSourceBean;
import com.monke.monkeybook.bean.VariableStore;
import com.monke.monkeybook.model.analyzeRule.assit.AnalyzeGlobal;
import com.monke.monkeybook.model.analyzeRule.assit.Assistant;
import com.monke.monkeybook.model.analyzeRule.assit.JavaExecutor;
import com.monke.monkeybook.model.analyzeRule.assit.SimpleJavaExecutor;
import com.monke.monkeybook.model.analyzeRule.assit.SimpleJavaExecutorImpl;
import com.monke.monkeybook.utils.ListUtils;
import com.monke.monkeybook.utils.StringUtils;
import com.monke.monkeybook.utils.URLUtils;

import org.jsoup.nodes.Element;
import org.mozilla.javascript.NativeObject;
import org.seimicrawler.xpath.JXNode;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.text.TextUtils.isEmpty;

abstract class BaseAnalyzerPresenter<S> implements IAnalyzerPresenter, JavaExecutor {

    private OutAnalyzer<S> mAnalyzer;
    private SimpleJavaExecutor mSimpleJavaExecutor;

    BaseAnalyzerPresenter(OutAnalyzer<S> analyzer) {
        this.mAnalyzer = analyzer;
        mSimpleJavaExecutor = new SimpleJavaExecutorImpl();
    }

    final OutAnalyzer<S> getAnalyzer() {
        return mAnalyzer;
    }

    final SourceParser<S> getParser() {
        return mAnalyzer.getParser();
    }

    final String getBaseURL() {
        return mAnalyzer.getConfig().getBaseURL();
    }

    final VariableStore getVariableStore() {
        return mAnalyzer.getConfig().getVariableStore();
    }

    final BookSourceBean getBookSource() {
        return mAnalyzer.getConfig().getBookSource();
    }

    final void processContentListWithScript(@NonNull List<String> result, @NonNull RulePattern rulePattern) {
        if (!rulePattern.javaScripts.isEmpty()) {
            if (result.size() == 1) {
                Object object = evalObjectScript(result.get(0), rulePattern);
                if (object instanceof List) {
                    result.clear();
                    for (Object o : (List) object) {
                        result.add(StringUtils.valueOf(o));
                    }
                } else {
                    result.set(0, StringUtils.valueOf(object));
                }
            } else {
                ListIterator<String> iterator = result.listIterator();
                while (iterator.hasNext()) {
                    iterator.set(evalStringScript(iterator.next(), rulePattern));
                }
            }
        }

        processContentList(result, rulePattern);
    }


    final void processContentList(@NonNull List<String> list, @NonNull RulePattern rulePattern) {
        if (!isEmpty(rulePattern.replaceRegex)) {
            ListIterator<String> iterator = list.listIterator();
            while (iterator.hasNext()) {
                String string = iterator.next();
                iterator.set(string.replaceAll(rulePattern.replaceRegex, rulePattern.replacement));
            }
        }
    }


    final String processContentWithScript(@NonNull String result, @NonNull RulePattern rulePattern) {
        result = evalStringScript(result, rulePattern);

        result = processContent(result, rulePattern);

        return result;
    }

    final String processContent(@NonNull String result, @NonNull RulePattern rulePattern) {
        if (!isEmpty(rulePattern.replaceRegex)) {
            if (rulePattern.replaceGroup > -1) {
                Pattern pattern = Pattern.compile(rulePattern.replaceRegex);
                Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    result = matcher.group(rulePattern.replaceGroup).replaceFirst(rulePattern.replaceRegex, rulePattern.replacement);
                } else {
                    result = AnalyzeGlobal.EMPTY;
                }
            } else {
                result = result.replaceAll(rulePattern.replaceRegex, rulePattern.replacement);
            }
        }
        return result;
    }

    final String evalStringScript(@NonNull Object result, @NonNull RulePattern rulePattern) {
        if (!rulePattern.javaScripts.isEmpty()) {
            for (String javaScript : rulePattern.javaScripts) {
                result = Assistant.evalObjectScript(javaScript, this, result, getBaseURL());
            }
        }
        return StringUtils.valueOf(result);
    }

    final Object evalObjectScript(@NonNull Object result, @NonNull RulePattern rulePattern) {
        if (!rulePattern.javaScripts.isEmpty()) {
            for (String javaScript : rulePattern.javaScripts) {
                result = Assistant.evalObjectScript(javaScript, this, result, getBaseURL());
            }
        }
        return result;
    }

    final List<String> evalStringArrayScript(@NonNull Object result, @NonNull RulePattern rulePattern) {
        if (!rulePattern.javaScripts.isEmpty()) {
            for (String javaScript : rulePattern.javaScripts) {
                result = Assistant.evalArrayScript(javaScript, this, result, getBaseURL());
            }
        }
        return ListUtils.toStringList(result);
    }


    final List<Object> evalObjectArrayScript(@NonNull Object result, @NonNull RulePattern rulePattern) {
        if (!rulePattern.javaScripts.isEmpty()) {
            for (String javaScript : rulePattern.javaScripts) {
                result = Assistant.evalArrayScript(javaScript, this, result, getBaseURL());
            }
        }
        return ListUtils.toObjectList(result);
    }

    RulePatterns fromRule(String rawRule, boolean withVariableStore) {
        final RulePatterns patterns;
        RuleMode mode = RuleMode.fromRuleType(mAnalyzer.getRuleType());
        if (withVariableStore) {
            patterns = RulePatterns.fromRule(rawRule, getBaseURL(), getVariableStore(), mode);
        } else {
            patterns = RulePatterns.fromRule(rawRule, getBaseURL(), mode);
        }
        return patterns;
    }


    RulePattern fromSingleRule(String rawRule, boolean withVariableStore) {
        final RulePattern pattern;
        RuleMode mode = RuleMode.fromRuleType(mAnalyzer.getRuleType());
        if (withVariableStore) {
            pattern = RulePattern.fromRule(rawRule, getVariableStore(), mode);
        } else {
            pattern = RulePattern.fromRule(rawRule, mode);
        }
        return pattern;
    }

    @Override
    public final void processUrlList(List<String> result) {
        if (result == null) return;
        ListIterator<String> iterator = result.listIterator();
        while (iterator.hasNext()) {
            iterator.set(URLUtils.getAbsUrl(getBaseURL(), iterator.next()));
        }
    }

    @Override
    public final String processUrl(@NonNull String result) {
        return URLUtils.getAbsUrl(getBaseURL(), result);
    }

    @Override
    public final String getTextDirectly(String rule) {
        final Object object = getParser().getPrimitive();
        if (object instanceof NativeObject) {
            return StringUtils.valueOf(((NativeObject) object).get(rule));
        } else if (object instanceof Element) {
            Element element = (Element) object;
            if (StringUtils.isBlank(rule)) {
                return element.text();
            }
            Element find = element.selectFirst(rule);
            return StringUtils.checkNull(find == null ? null : find.text(), element.text());
        } else if (object instanceof JXNode) {
            return StringUtils.valueOf(((JXNode) object).selOne(rule));
        }
        return getText(rule);
    }

    @Override
    public final String getRawUrlDirectly(String rule) {
        final Object object = getParser().getPrimitive();
        if (object instanceof NativeObject) {
            return StringUtils.valueOf(((NativeObject) object).get(rule));
        } else if (object instanceof Element) {
            Element element = (Element) object;
            return element.attr(rule);
        } else if (object instanceof JXNode) {
            return StringUtils.valueOf(((JXNode) object).selOne(rule));
        }
        return getRawUrl(rule);
    }

    @Override
    public final String getAbsUrlDirectly(String rule) {
        String url = getRawUrlDirectly(rule);
        return processUrl(url);
    }

    @Override
    public final Map<String, String> putVariableMapDirectly(String rule, int flag) {
        if (getParser().isSourceEmpty() || isEmpty(rule)) {
            return getVariableStore().getVariableMap();
        }
        final Map<String, String> resultMap = new HashMap<>();
        final VariablesPattern variablesPattern = VariablesPattern.fromPutterRule(rule, flag);
        if (variablesPattern.map.isEmpty()) {
            return resultMap;
        } else {
            for (Map.Entry<String, String> entry : variablesPattern.map.entrySet()) {
                String value = getTextDirectly(entry.getValue());
                if (!isEmpty(value)) {
                    resultMap.put(entry.getKey(), value);
                }
            }
        }
        return getVariableStore().putVariableMap(resultMap);
    }

    /////////////////////////////////////////////////////////JS执行/////////////////////////////////////////////////////////////////

    @Override
    public final String putVariable(String key, String val) {
        return getVariableStore().putVariable(key, val);
    }

    @Override
    public final String getVariable(String key) {
        return getVariableStore().getVariable(key);
    }

    @Override
    public final String ajax(String urlStr) {
        return mSimpleJavaExecutor.ajax(urlStr);
    }

    @Override
    public final String base64Decode(String string) {
        return mSimpleJavaExecutor.base64Decode(string);
    }

    @Override
    public final String base64Encode(String string) {
        return mSimpleJavaExecutor.base64Encode(string);
    }

    @Override
    public final String formatHtml(String string) {
        return mSimpleJavaExecutor.formatHtml(string);
    }

    @Override
    public String unescapeHtml3(String string) {
        return mSimpleJavaExecutor.unescapeHtml3(string);
    }

    @Override
    public String unescapeHtml4(String string) {
        return mSimpleJavaExecutor.unescapeHtml4(string);
    }
}
