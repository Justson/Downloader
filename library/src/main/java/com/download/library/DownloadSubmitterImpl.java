package com.download.library;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.queue.library.DispatchThread;
import com.queue.library.GlobalQueue;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;

import static com.download.library.DownloadTask.STATUS_PAUSED;
import static com.download.library.Downloader.DOWNLOAD_MESSAGE;
import static com.download.library.Downloader.ERROR_LOAD;
import static com.download.library.Downloader.ERROR_USER_CANCEL;
import static com.download.library.Downloader.ERROR_USER_PAUSE;
import static com.download.library.Downloader.SUCCESSFUL;

/**
 * @author cenxiaozhong
 * @date 2020/7/4
 * @since 1.0.0
 */
public class DownloadSubmitterImpl implements DownloadSubmitter {

    private static final String TAG = Runtime.PREFIX + DownloadSubmitterImpl.class.getSimpleName();
    private final Executor mExecutor;
    private final Executor mExecutor0;
    private volatile DispatchThread mMainQueue = null;
    private final Object mLock = new Object();

    private DownloadSubmitterImpl() {
        this.mExecutor = Executors.taskEnqueueDispatchExecutor();
        this.mExecutor0 = Executors.taskQueuedUpDispatchExecutor();
    }

    static DownloadSubmitterImpl getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public boolean submit(DownloadTask downloadTask) {
        if (TextUtils.isEmpty(downloadTask.getUrl())) {
            return false;
        }
        synchronized (mLock) {
            if (ExecuteTasksMap.getInstance().exist(downloadTask.getUrl())) {
                Log.e(TAG, "task exists:" + downloadTask.getUrl());
                return false;
            }
            Downloader downloader = (Downloader) Downloader.create(downloadTask);
            ExecuteTasksMap.getInstance().addTask(downloadTask.getUrl(), downloader);
            execute(new DownloadStartTask(downloadTask, downloader));
        }
        return true;
    }

    @Override
    public File submit0(@NonNull final DownloadTask downloadTask) throws Exception {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw new RuntimeException("Sync download must call it in the non main-Thread ");
        }
        boolean submit = submit(downloadTask);
        if (!submit) {
            return null;
        }
        downloadTask.setup();
        downloadTask.await();
        if (null != downloadTask.getThrowable()) {
            throw (Exception) downloadTask.getThrowable();
        }
        try {
            File file = downloadTask.isSuccessful() ? downloadTask.getFile() : null;
            return file;
        } finally {
            downloadTask.destroy();
        }
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


    DispatchThread getMainQueue() {
        if (mMainQueue == null) {
            mMainQueue = GlobalQueue.getMainQueue();
        }
        return mMainQueue;
    }

    private void releaseTask(final DownloadTask downloadTask) {
        if (!TextUtils.isEmpty(downloadTask.getUrl())) {
            synchronized (mLock) {
                if (!TextUtils.isEmpty(downloadTask.getUrl())) {
                    ExecuteTasksMap.getInstance().removeTask(downloadTask.getUrl());
                }
            }
        }
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
                    executeOnExecutor(Executors.io());
                } else {
                    executeOnExecutor(Executors.getSerialExecutor());
                }
            } catch (Throwable throwable) {
                releaseTask(mDownloadTask);
                throwable.printStackTrace();
                throw throwable;
            }
        }


        private void executeOnExecutor(Executor threadPoolExecutor) {
            threadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        int result = mDownloader.doInBackground();
                        DownloadSubmitterImpl.getInstance().execute0(new DownloadTaskOver(result, mDownloader, mDownloadTask));
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        mDownloadTask.error();
                        releaseTask(mDownloadTask);
                    }
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
                        if (null != mDownloadNotifier) {
                            mDownloadNotifier.cancel();
                        }
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
                releaseTask(downloadTask);
                destroyTask();
                downloadTask.anotify();
            }
        }

        void destroyTask() {
            DownloadTask downloadTask = mDownloadTask;
            if (!downloadTask.isSuccessful()) {
                return;
            }
            if (!downloadTask.isAWait) {
                Runtime.getInstance().log(TAG, "destroyTask:" + downloadTask.getUrl());
                downloadTask.destroy();
            }

        }

        private void autoOpen() {
            getMainQueue().postRunnableScissors(new Runnable() {
                @Override
                public void run() {
                    Intent mIntent = Runtime.getInstance().getCommonFileIntentCompat(mDownloadTask.getContext(), mDownloadTask);
                    if (!(mDownloadTask.getContext() instanceof Activity)) {
                        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    try {
                        mDownloadTask.getContext().startActivity(mIntent);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
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
