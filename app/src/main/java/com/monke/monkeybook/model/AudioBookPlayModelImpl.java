package com.monke.monkeybook.model;

import android.text.TextUtils;

import com.hwangjr.rxbus.RxBus;
import com.monke.monkeybook.base.observer.SimpleObserver;
import com.monke.monkeybook.bean.BookShelfBean;
import com.monke.monkeybook.bean.ChapterBean;
import com.monke.monkeybook.bean.SearchBookBean;
import com.monke.monkeybook.dao.DbHelper;
import com.monke.monkeybook.help.BookshelfHelp;
import com.monke.monkeybook.help.RxBusTag;
import com.monke.monkeybook.model.content.BookException;
import com.monke.monkeybook.model.impl.IAudioBookPlayModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class AudioBookPlayModelImpl implements IAudioBookPlayModel {

    private static final int RETRY_COUNT = 2;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private Disposable mPlayDisposable;
    private Disposable mChapterDisposable;

    private PlayCallback mPlayCallback;

    private boolean isPrepared;
    private BookShelfBean bookShelf;

    private int mPlayIndex;
    private int mRetryCount;

    public AudioBookPlayModelImpl(BookShelfBean bookShelf) {
        this.bookShelf = bookShelf;
    }

    @Override
    public void registerPlayCallback(PlayCallback callback) {
        this.mPlayCallback = callback;
    }

    @Override
    public void ensureChapterList(Callback<BookShelfBean> callback) {
        if (mPlayCallback != null) {
            mPlayCallback.onStart();
        }

        if (mChapterDisposable != null) {
            disposables.remove(mChapterDisposable);
        }

        if (!bookShelf.realChapterListEmpty()) {
            isPrepared = true;
            if (callback != null) {
                callback.onSuccess(bookShelf);
            }
            mPlayIndex = bookShelf.getDurChapter();
            ChapterBean chapterBean = bookShelf.getChapter(mPlayIndex);
            playChapter(chapterBean, false);
            saveBookShelf(bookShelf);
        } else {
            Observable.create((ObservableOnSubscribe<List<ChapterBean>>) emitter -> {
                List<ChapterBean> chapterBeans = BookshelfHelp.queryChapterList(bookShelf.getNoteUrl());
                if (chapterBeans != null) {
                    emitter.onNext(chapterBeans);
                } else {
                    emitter.onNext(new ArrayList<>());
                }
                emitter.onComplete();
            })
                    .subscribeOn(Schedulers.single())
                    .flatMap((Function<List<ChapterBean>, ObservableSource<BookShelfBean>>) chapterBeans -> {
                        if (chapterBeans.isEmpty()) {
                            return getChapterList(bookShelf);
                        }
                        bookShelf.setChapterList(chapterBeans);
                        return Observable.just(bookShelf);
                    })
                    .doAfterNext(this::saveBookShelf)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SimpleObserver<BookShelfBean>() {

                        @Override
                        public void onSubscribe(Disposable d) {
                            disposables.add(mChapterDisposable = d);
                        }

                        @Override
                        public void onNext(BookShelfBean bookShelfBean) {
                            bookShelf = bookShelfBean;
                            onSuccess(callback);
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (callback != null) {
                                callback.onError(e);
                            }
                        }
                    });
        }
    }

    private void onSuccess(Callback<BookShelfBean> callback) {
        if (bookShelf.realChapterListEmpty()) {
            if (callback != null) {
                callback.onError(new BookException("目录获取失败"));
            }
        } else {
            isPrepared = true;
            if (callback != null) {
                callback.onSuccess(bookShelf);
            }

            mPlayIndex = bookShelf.getDurChapter();
            ChapterBean chapterBean = bookShelf.getChapter(mPlayIndex);
            playChapter(chapterBean, false);
        }
    }

    @Override
    public void changeSource(SearchBookBean searchBookBean, Callback<BookShelfBean> callback) {
        if (bookShelf == null) return;

        if (mPlayCallback != null) {
            mPlayCallback.onStart();
        }

        if (mChapterDisposable != null) {
            disposables.remove(mChapterDisposable);
        }

        BookShelfBean target = BookshelfHelp.getBookFromSearchBook(searchBookBean);
        target.setSerialNumber(bookShelf.getSerialNumber());
        target.setDurChapterName(bookShelf.getDurChapterName());
        target.setDurChapter(bookShelf.getDurChapter());
        target.setDurChapterPage(bookShelf.getDurChapterPage());
        target.setFinalDate(bookShelf.getFinalDate());
        WebBookModelImpl.getInstance().getBookInfo(target)
                .subscribeOn(Schedulers.io())
                .flatMap(bookShelfBean -> WebBookModelImpl.getInstance().getChapterList(bookShelfBean))
                .timeout(30, TimeUnit.SECONDS)
                .map(bookShelfBean -> {
                    bookShelfBean.setGroup(bookShelf.getGroup());
                    bookShelfBean.setUpdateOff(bookShelf.getUpdateOff());
                    bookShelfBean.setNewChapters(0);
                    return bookShelfBean;
                })
                .doOnNext(bookShelfBean -> {
                    BookshelfHelp.removeFromBookShelf(bookShelf);
                    BookshelfHelp.saveBookToShelf(bookShelfBean);
                    bookShelf.setChangeSource(true);
                    RxBus.get().post(RxBusTag.HAD_REMOVE_BOOK, bookShelf);
                    RxBus.get().post(RxBusTag.HAD_ADD_BOOK, bookShelfBean);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<BookShelfBean>() {
                    @Override
                    public void onNext(BookShelfBean bookShelfBean) {
                        bookShelf = bookShelfBean;
                        onSuccess(callback);
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (callback != null) {
                            callback.onError(e);
                        }
                    }
                });
    }

    @Override
    public void updateBookShelf(BookShelfBean bookShelfBean) {
        if (bookShelfBean != null && !bookShelfBean.realChapterListEmpty()) {
            this.bookShelf = bookShelfBean;
        }
    }

    @Override
    public void addToShelf() {
        if (bookShelf != null) {
            saveBookShelf(bookShelf, true);
        }
    }

    @Override
    public boolean inBookShelf() {
        if (bookShelf == null) {
            return false;
        }
        return BookshelfHelp.isInBookShelf(bookShelf.getNoteUrl());
    }

    @Override
    public void playNext() {
        if (!isPrepared) return;

        mRetryCount = 0;

        if (hasNext()) {
            mPlayIndex += 1;
            ChapterBean chapterBean = bookShelf.getChapter(mPlayIndex);
            playChapter(chapterBean, true);
        }

    }

    @Override
    public void playPrevious() {
        if (!isPrepared) return;

        mRetryCount = 0;

        if (hasPrevious()) {
            mPlayIndex -= 1;
            ChapterBean chapterBean = bookShelf.getChapter(mPlayIndex);
            playChapter(chapterBean, true);
        }

    }

    @Override
    public boolean hasNext() {
        return mPlayIndex < bookShelf.getChapterList().size() - 1;
    }

    @Override
    public boolean hasPrevious() {
        return mPlayIndex > 0;
    }

    @Override
    public boolean isPrepared() {
        return isPrepared;
    }

    @Override
    public ChapterBean getDurChapter() {
        if (bookShelf != null && !bookShelf.realChapterListEmpty()) {
            return bookShelf.getChapter(mPlayIndex);
        }
        return null;
    }

    @Override
    public void playChapter(ChapterBean chapter, boolean reset) {
        if (!isPrepared) return;

        if (mPlayCallback != null) {
            mPlayCallback.onStart();
        }

        if (mPlayDisposable != null) {
            disposables.remove(mPlayDisposable);
        }

        if (mPlayCallback != null) {
            mPlayCallback.onPrepare(chapter);
        }

        Observable.just(chapter)
                .subscribeOn(Schedulers.single())
                .map(chapterBean -> {
                    if (reset) {
                        chapterBean.setStart(0);
                    }
                    return chapterBean;
                })
                .doOnNext(chapterBean -> {
                    mPlayIndex = chapterBean.getDurChapterIndex();
                    bookShelf.setDurChapter(chapterBean.getDurChapterIndex());
                    bookShelf.setDurChapterName(chapterBean.getDurChapterName());
                    saveBookShelf(bookShelf);
                })
                .flatMap((Function<ChapterBean, ObservableSource<ChapterBean>>) chapterBean -> {
                    if (!TextUtils.isEmpty(chapter.getDurChapterPlayUrl())) {
                        return Observable.just(chapterBean);
                    }
                    return WebBookModelImpl.getInstance()
                            .processAudioChapter(bookShelf.getTag(), chapterBean);
                })
                .timeout(20, TimeUnit.SECONDS)
                .retry(RETRY_COUNT)
                .flatMap((Function<ChapterBean, ObservableSource<ChapterBean>>) chapterBean -> {
                    if (TextUtils.isEmpty(chapterBean.getDurChapterPlayUrl())) {
                        return Observable.error(new NullPointerException("audio play url is null"));
                    }
                    return Observable.just(chapterBean);
                })
                .doAfterNext(chapterBean -> {
                    try {
                        bookShelf.getChapterList().set(chapterBean.getDurChapterIndex(), chapterBean);
                        if (inBookShelf()) {
                            DbHelper.getInstance().getDaoSession().getChapterBeanDao().insertOrReplace(chapterBean);
                        }
                    } catch (Exception ignore) {
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<ChapterBean>() {

                    @Override
                    public void onSubscribe(Disposable d) {
                        disposables.add(mPlayDisposable = d);
                    }

                    @Override
                    public void onNext(ChapterBean chapterBean) {
                        if (mPlayCallback != null) {
                            mPlayCallback.onPlay(chapterBean);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (mPlayCallback != null) {
                            mPlayCallback.onError(e);
                        }
                    }
                });

    }

    @Override
    public boolean retryPlay(boolean reset) {
        if (!isPrepared) return false;

        ChapterBean chapterBean = getDurChapter();
        if (reset || (chapterBean != null && mRetryCount < RETRY_COUNT)) {
            if (!reset) {
                mRetryCount += 1;
            }
            chapterBean.setDurChapterPlayUrl(null);
            playChapter(chapterBean, true);
            return true;
        }
        return false;
    }

    @Override
    public void saveProgress(int progress, int duration) {
        Observable.create((ObservableOnSubscribe<Boolean>) emitter -> {
            if (inBookShelf()) {
                ChapterBean chapterBean = bookShelf.getChapter(mPlayIndex);
                chapterBean.setStart(progress);
                chapterBean.setEnd(duration);
                DbHelper.getInstance().getDaoSession().getChapterBeanDao().insertOrReplace(chapterBean);
            }
            emitter.onNext(true);
            emitter.onComplete();
        }).subscribeOn(Schedulers.single())
                .subscribe(new SimpleObserver<Boolean>() {
                    @Override
                    public void onNext(Boolean bool) {
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
    }

    private void saveBookShelf(BookShelfBean bookShelfBean) {
        saveBookShelf(bookShelfBean, false);
    }

    private void saveBookShelf(BookShelfBean bookShelfBean, boolean forceSave) {
        Observable.create((ObservableOnSubscribe<BookShelfBean>) emitter -> {
            bookShelfBean.setFinalDate(System.currentTimeMillis());
            bookShelfBean.setHasUpdate(false);
            if (forceSave || inBookShelf()) {
                BookshelfHelp.saveBookToShelf(bookShelfBean);
            }
            emitter.onNext(bookShelfBean);
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .subscribe(new SimpleObserver<BookShelfBean>() {
                    @Override
                    public void onNext(BookShelfBean bookShelfBean) {
                        RxBus.get().post(RxBusTag.UPDATE_BOOK_SHELF, bookShelfBean);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
    }

    private Observable<BookShelfBean> getChapterList(BookShelfBean bookShelf) {
        return WebBookModelImpl.getInstance().getChapterList(bookShelf)
                .map(bookShelfBean -> {
                    bookShelfBean.setHasUpdate(false);
                    bookShelfBean.setNewChapters(0);
                    bookShelfBean.setFinalRefreshData(System.currentTimeMillis());
                    return bookShelfBean;
                });
    }

    public void destroy() {
        disposables.dispose();
    }

}
