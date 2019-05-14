package com.monke.monkeybook.help;

import android.text.TextUtils;

import com.monke.monkeybook.bean.SearchBookBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchBookHelp {

    private final DataObserver observer;

    public SearchBookHelp(DataObserver observer) {
        this.observer = observer;
    }

    public synchronized void addSearchBooks(List<SearchBookBean> originalList, List<SearchBookBean> newDataS, String keyWord) {
        if (originalList == null) {
            return;
        }
        if (newDataS != null && newDataS.size() > 0) {
            List<SearchBookBean> searchBookBeansAdd = new ArrayList<>();
            if (originalList.size() == 0) {
                originalList.addAll(newDataS);
                sortSearchBooks(newDataS, keyWord);
                observer.onChanged(originalList.size());
            } else {
                //存在
                for (SearchBookBean temp : newDataS) {
                    boolean hasSame = false;
                    for (int i = 0, size = originalList.size(); i < size; i++) {
                        SearchBookBean searchBook = originalList.get(i);
                        if (temp.isSimilarTo(searchBook)) {
                            hasSame = true;
                            searchBook.addTag(temp.getTag());
                            observer.onItemChanged(i);
                            break;
                        }
                    }
                    if (!hasSame) {
                        searchBookBeansAdd.add(temp);
                    }
                }

                for (SearchBookBean temp : searchBookBeansAdd) {
                    if (TextUtils.equals(keyWord, temp.getName())) {
                        for (int i = 0; i < originalList.size(); i++) {
                            SearchBookBean searchBook = originalList.get(i);
                            if (!TextUtils.equals(keyWord, searchBook.getName())) {
                                originalList.add(i, temp);
                                observer.onItemInserted(i);
                                break;
                            }
                        }
                    } else if (TextUtils.equals(keyWord, temp.getAuthor())) {
                        for (int i = 0; i < originalList.size(); i++) {
                            SearchBookBean searchBook = originalList.get(i);
                            if (!TextUtils.equals(keyWord, searchBook.getName()) && !TextUtils.equals(keyWord, searchBook.getAuthor())) {
                                originalList.add(i, temp);
                                observer.onItemInserted(i);
                                break;
                            }
                        }
                    } else if (temp.getName().contains(keyWord) || temp.getAuthor().contains(keyWord)) {
                        for (int i = 0; i < originalList.size(); i++) {
                            SearchBookBean searchBook = originalList.get(i);
                            if (!TextUtils.equals(keyWord, searchBook.getName()) && !TextUtils.equals(keyWord, searchBook.getAuthor())) {
                                originalList.add(i, temp);
                                observer.onItemInserted(i);
                                break;
                            }
                        }
                    } else {
                        originalList.add(temp);
                        observer.onItemInserted(originalList.size() - 1);
                    }
                }
            }
        }
    }

    private void sortSearchBooks(List<SearchBookBean> searchBookBeans, String keyWord) {
        Collections.sort(searchBookBeans, (o1, o2) -> {
            if (TextUtils.equals(keyWord, o1.getName())
                    || TextUtils.equals(keyWord, o1.getAuthor())) {
                return -1;
            } else if (TextUtils.equals(keyWord, o2.getName())
                    || TextUtils.equals(keyWord, o2.getAuthor())) {
                return 1;
            } else if (o1.getName().contains(keyWord) || o1.getAuthor().contains(keyWord)) {
                return -1;
            } else if (o2.getName().contains(keyWord) || o2.getAuthor().contains(keyWord)) {
                return 1;
            } else {
                return 0;
            }
        });
    }

    public interface DataObserver {
        void onChanged(int count);

        void onItemInserted(int position);

        void onItemChanged(int position);
    }
}
