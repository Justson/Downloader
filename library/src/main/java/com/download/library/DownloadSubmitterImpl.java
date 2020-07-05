package com.download.library;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.Process;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.download.library.queue.Dispatch;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.download.library.DownloadTask.STATUS_PAUSED;
import static com.download.library.Downloader.DOWNLOAD_MESSAGE;
import static com.download.library.Downloader.ERROR_LOAD;
import static com.download.library.Downloader.ERROR_USER_CANCEL;
import static com.download.library.Downloader.ERROR_USER_PAUSE;
import static com.download.library.Downloader.SERIAL_EXECUTOR;
import static com.download.library.Downloader.SUCCESSFUL;

/**
 * @author cenxiaozhong
 * @date 2020/7/4
 * @since 1.0.0
 */
public class DownloadSubmitterImpl implements DownloadSubmitter {

    private static final String TAG = DownloadSubmitterImpl.class.getSimpleName();
    private final Executor mExecutor;
    private final Executor mExecutor0;
    private volatile Dispatch mMainQueue = null;
    private final Object mLock = new Object();

    private DownloadSubmitterImpl() {
        ThreadPoolExecutor service = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r);
            }
        });
        service.allowCoreThreadTimeOut(true);
        this.mExecutor = service;
        service = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r);
            }
        });
        service.allowCoreThreadTimeOut(true);
        this.mExecutor0 = service;
    }

    static DownloadSubmitterImpl getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public boolean submit(DownloadTask downloadTask) {
        synchronized (mLock) {
            if (TextUtils.isEmpty(downloadTask.getUrl())) {
                return false;
            }
            if (ExecuteTasksMap.getInstance().exist(downloadTask.getUrl())) {
                return false;
            }
            Downloader downloader = (Downloader) Downloader.create(downloadTask);
            ExecuteTasksMap.getInstance().addTask(downloadTask.getUrl(), downloader);
            execute(new DownloadStartTask(downloadTask, downloader));
        }
        return true;
    }

    @Override
    public File submit0(final DownloadTask downloadTask) throws Exception {
//        RunnableFuture<File> future = new FutureTask<File>(new Callable<File>() {
//            @Override
//            public File call() throws Exception {
//                synchronized (Downloader.class) {
//                    if (TextUtils.isEmpty(downloadTask.getUrl())) {
//                        return null;
//                    }
//                    if (ExecuteTasksMap.getInstance().exist(downloadTask.getUrl())) {
//                        return null;
//                    }
//                    Downloader downloader = (Downloader) Downloader.create(downloadTask);
//                    ExecuteTasksMap.getInstance().addTask(downloadTask.getUrl(), downloader);
//                    new DownloadStartTask(downloadTask, downloader).run();
//                }
//                return null;
//            }
//        });
        return null;
    }

    void execute(@NonNull final Runnable command) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                command.run();
            }
        });
    }

    void execute0(@NonNull final Runnable command) {
        mExecutor0.execute(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                command.run();
            }
        });
    }


    Dispatch getMainQueue() {
        if (mMainQueue == null) {
            synchronized (DownloadSubmitterImpl.this) {
                if (mMainQueue == null) {
                    mMainQueue = new Dispatch(Looper.getMainLooper());
                }
            }
        }
        return mMainQueue;
    }


    private class DownloadStartTask implements Runnable {

        private final DownloadTask mDownloadTask;
        private final Downloader mDownloader;

        public DownloadStartTask(DownloadTask downloadTask, Downloader downloader) {
            this.mDownloadTask = downloadTask;
            this.mDownloader = downloader;
        }

        @Override
        public void run() {
            try {
                if (null != mDownloadTask.getDownloadingListener()) {
                    try {
                        Annotation annotation = mDownloadTask.getDownloadingListener().getClass().getDeclaredMethod("onProgress", String.class, long.class, long.class, long.class).getAnnotation(DownloadingListener.MainThread.class);
                        boolean mCallbackInMainThread = null != annotation;
                        mDownloader.mCallbackInMainThread = mCallbackInMainThread;
                        Runtime.getInstance().log(TAG, " callback in main-Thread:" + mCallbackInMainThread);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (mDownloadTask.getStatus() != STATUS_PAUSED) {
                    mDownloadTask.resetTime();
                }
                mDownloadTask.setStatus(DownloadTask.STATUS_PENDDING);

                if (null == mDownloadTask.getFile()) {
                    File file = mDownloadTask.isUniquePath()
                            ? Runtime.getInstance().uniqueFile(mDownloadTask, null)
                            : Runtime.getInstance().createFile(mDownloadTask.mContext, mDownloadTask);
                    mDownloadTask.setFileSafe(file);
                } else if (mDownloadTask.getFile().isDirectory()) {
                    File file = mDownloadTask.isUniquePath()
                            ? Runtime.getInstance().uniqueFile(mDownloadTask, mDownloadTask.getFile())
                            : Runtime.getInstance().createFile(mDownloadTask.mContext, mDownloadTask, mDownloadTask.getFile());
                    mDownloadTask.setFileSafe(file);
                } else if (!mDownloadTask.getFile().exists()) {
                    try {
                        mDownloadTask.getFile().createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        mDownloadTask.setFileSafe(null);
                    }
                }
                if (null == mDownloadTask.getFile()) {
                    throw new RuntimeException("target file can't be created . ");
                }
                mDownloadTask.createNotifier();

                if (this.mDownloadTask.isParallelDownload()) {
                    executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    executeOnExecutor(SERIAL_EXECUTOR);
                }
            } catch (Throwable throwable) {
                if (!TextUtils.isEmpty(mDownloadTask.getUrl())) {
                    synchronized (mLock) {
                        if (!TextUtils.isEmpty(mDownloadTask.getUrl())) {
                            ExecuteTasksMap.getInstance().removeTask(mDownloadTask.getUrl());
                        }
                    }
                }
                throwable.printStackTrace();
                throw throwable;
            }
        }

        private void executeOnExecutor(Executor threadPoolExecutor) {
            threadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    int result = mDownloader.doInBackground();
                    DownloadSubmitterImpl.getInstance().execute0(new DownloadTaskOver(result, mDownloader, mDownloadTask));
                }
            });
        }

    }

    private final class DownloadTaskOver implements Runnable {

        private final int mResult;
        private final Downloader mDownloader;
        private final DownloadTask mDownloadTask;
        private final DownloadNotifier mDownloadNotifier;

        DownloadTaskOver(int result, Downloader downloader, DownloadTask downloadTask) {
            this.mResult = result;
            this.mDownloader = downloader;
            this.mDownloadTask = downloadTask;
            this.mDownloadNotifier = downloadTask.mDownloadNotifier;
        }

        @Override
        public void run() {
            DownloadTask downloadTask = this.mDownloadTask;
            try {
                if (mResult == ERROR_USER_PAUSE) {
                    if (null != downloadTask.getDownloadListener()) {
                        doCallback(mResult);
                    }
                    if (null != mDownloadNotifier) {
                        mDownloadNotifier.onDownloadPaused();
                    }
                    return;
                } else if (mResult == ERROR_USER_CANCEL) {
                    downloadTask.completed();
                } else if (mResult == ERROR_LOAD) {
                    downloadTask.completed();
                } else {
                    downloadTask.completed();
                }
                boolean isCancelDispose = doCallback(mResult);
                // Error
                if (mResult > SUCCESSFUL) {
                    if (null != mDownloadNotifier) {
                        mDownloadNotifier.cancel();
                    }
                    return;
                }
                if (downloadTask.isEnableIndicator()) {
                    if (isCancelDispose) {
                        mDownloadNotifier.cancel();
                        return;
                    }
                    if (null != mDownloadNotifier) {
                        mDownloadNotifier.onDownloadFinished();
                    }
                }
                // auto open file
                if (!downloadTask.isAutoOpen()) {
                    return;
                }
                autoOpen();
            } catch (Throwable throwable) {
                if (Runtime.getInstance().isDebug()) {
                    throwable.printStackTrace();
                }
            } finally {
                synchronized (mLock) {
                    ExecuteTasksMap.getInstance().removeTask(downloadTask.getUrl());
                }
                destroyTask();
            }
        }

        void destroyTask() {
            DownloadTask downloadTask = mDownloadTask;
            if (downloadTask.getStatus() == DownloadTask.STATUS_CANCELED || downloadTask.getStatus() == STATUS_PAUSED) {
                return;
            }
            downloadTask.destroy();
        }

        private void autoOpen() {
            getInstance().getMainQueue().postRunnableScissors(new Runnable() {
                @Override
                public void run() {
                    Intent mIntent = Runtime.getInstance().getCommonFileIntentCompat(mDownloadTask.getContext(), mDownloadTask);
                    if (!(mDownloadTask.getContext() instanceof Activity)) {
                        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    mDownloadTask.getContext().startActivity(mIntent);
                }
            });
        }

        private boolean doCallback(final Integer code) {
            final DownloadTask downloadTask = this.mDownloadTask;
            final DownloadListener mDownloadListener = downloadTask.getDownloadListener();
            if (null == mDownloadListener) {
                return false;
            }
            return getInstance().getMainQueue().call(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return mDownloadListener.onResult(code <= SUCCESSFUL ? null
                                    : new DownloadException(code, "failed , cause:" + DOWNLOAD_MESSAGE.get(code)), downloadTask.getFileUri(),
                            downloadTask.getUrl(), mDownloadTask);
                }
            });

        }
    }

    private static class Holder {
        private static final DownloadSubmitterImpl INSTANCE = new DownloadSubmitterImpl();
    }

}
