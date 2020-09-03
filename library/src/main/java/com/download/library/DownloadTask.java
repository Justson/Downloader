/*
 * Copyright (C)  Justson(https://github.com/Justson/Downloader)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.download.library;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import com.queue.library.GlobalQueue;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;


/**
 * @author cenxiaozhong
 * @date 2017/5/13
 */
public class DownloadTask extends Extra implements Serializable, Cloneable {

    static final String TAG = Runtime.PREFIX + DownloadTask.class.getSimpleName();
    int mId = Runtime.getInstance().generateGlobalId();
    long mTotalsLength;
    protected Context mContext;
    protected File mFile;
    protected DownloadListener mDownloadListener;
    protected DownloadingListener mDownloadingListener;
    protected String authority = "";
    public static final int STATUS_NEW = 1000;
    public static final int STATUS_PENDDING = 1001;
    public static final int STATUS_DOWNLOADING = 1002;
    public static final int STATUS_PAUSING = 1003;
    public static final int STATUS_PAUSED = 1004;
    public static final int STATUS_SUCCESSFUL = 1005;
    public static final int STATUS_CANCELED = 1006;
    public static final int STATUS_ERROR = 1007;
    long beginTime = 0L;
    long pauseTime = 0L;
    long endTime = 0L;
    long detalTime = 0L;
    boolean isCustomFile = false;
    boolean uniquePath = true;
    int connectTimes = 0;
    volatile long loaded = 0L;
    String redirect = "";
    DownloadStatusListener mDownloadStatusListener;
    Throwable mThrowable;
    Lock mutex = null;
    Condition mCondition = null;
    volatile boolean isAWait = false;
    protected DownloadNotifier mDownloadNotifier;


    protected synchronized void setup() {
        if (mutex == null) {
            mutex = new ReentrantLock();
            mCondition = mutex.newCondition();
        }
    }

    void resetConnectTimes() {
        connectTimes = 0;
    }


    @IntDef({STATUS_NEW, STATUS_PENDDING, STATUS_DOWNLOADING, STATUS_PAUSING, STATUS_PAUSED, STATUS_SUCCESSFUL, STATUS_CANCELED, STATUS_ERROR})
    @interface DownloadTaskStatus {
    }

    private volatile int status = STATUS_NEW;

    public DownloadTask() {
        super();
    }

    public synchronized int getStatus() {
        return status;
    }

    synchronized void setStatus(@DownloadTaskStatus final int status) {
        this.status = status;
        final DownloadStatusListener downloadStatusListener = mDownloadStatusListener;
        final DownloadTask downloadTask = this;
        if (null != downloadStatusListener) {
            GlobalQueue.getMainQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    downloadStatusListener.onDownloadStatusChanged(downloadTask.clone(), status);
                }
            });
        }
    }


    void resetTime() {
        beginTime = 0L;
        pauseTime = 0L;
        endTime = 0L;
        detalTime = 0L;
    }

    public int getId() {
        return this.mId;
    }

    public Context getContext() {
        return mContext;
    }

    protected DownloadTask setContext(Context context) {
        mContext = context.getApplicationContext();
        return this;
    }

    protected DownloadTask setEnableIndicator(boolean enableIndicator) {
        if (enableIndicator && mFile != null && TextUtils.isEmpty(authority)) {
            Runtime.getInstance().logError(TAG, "Custom file path, you must specify authority, otherwise the notification should not be turned on. ");
            this.mEnableIndicator = false;
        } else {
            this.mEnableIndicator = enableIndicator;
        }
        return this;
    }

    public File getFile() {
        return mFile;
    }

    public Uri getFileUri() {
        return Uri.fromFile(this.mFile);
    }

    String getAuthority() {
        return authority;
    }

    DownloadTask setFileSafe(@NonNull File file) {
        mFile = file;
        return this;
    }

    protected DownloadTask setFile(@NonNull File file) {
        if (!file.exists() && file.isFile()) {
            try {
                String parentPath = file.getParent();
                File parent = new File(parentPath);
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Runtime.getInstance().logError(TAG, "create file error .");
                return this;
            }
        }
        mFile = file;
        this.authority = "";
        checkCustomFilePath(file);
        return this;
    }

    private void checkCustomFilePath(File file) {
        if (file == null || file.getAbsolutePath().startsWith(Runtime.getInstance().getDefaultDir(this.getContext()).getAbsolutePath())) {
            isCustomFile = false;
        } else if (!TextUtils.isEmpty(this.authority)) {
            setEnableIndicator(true);
            isCustomFile = true;
        } else {
            setEnableIndicator(false);
            isCustomFile = true;
        }
    }

    boolean isCustomFile() {
        return isCustomFile;
    }

    protected DownloadTask setFile(@NonNull File file, @NonNull String authority) {
        if (!file.exists() && file.isFile()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Runtime.getInstance().logError(TAG, "create file error .");
                return this;
            }
        }
        this.mFile = file;
        this.authority = authority;
        checkCustomFilePath(file);
        return this;
    }

    void updateTime(long beginTime) {
        if (this.beginTime == 0L) {
            this.beginTime = beginTime;
            return;
        }
        if (this.beginTime != beginTime) {
            detalTime += Math.abs(beginTime - this.pauseTime);
        }
    }

    public long getUsedTime() {
        if (status == STATUS_DOWNLOADING) {
            return beginTime > 0L ? SystemClock.elapsedRealtime() - beginTime - detalTime : 0L;
        } else if (status == STATUS_CANCELED) {
            return endTime - beginTime - detalTime;
        } else if (status == STATUS_PENDDING) {
            return pauseTime > 0L ? pauseTime - beginTime - detalTime : 0L;
        } else if (status == STATUS_PAUSED || status == STATUS_PAUSING) {
            return pauseTime - beginTime - detalTime;
        } else if (status == STATUS_NEW) {
            return pauseTime > 0L ? pauseTime - beginTime - detalTime : 0L;
        } else if (status == STATUS_SUCCESSFUL || status == STATUS_ERROR) {
            return endTime - beginTime - detalTime;
        } else {
            return 0L;
        }
    }

    public boolean isPausing() {
        return getStatus() == STATUS_PAUSING;
    }

    public void pausing() {
        setStatus(STATUS_PAUSING);
        pauseTime = SystemClock.elapsedRealtime();
    }

    public boolean isPaused() {
        return getStatus() == STATUS_PAUSED;
    }

    public boolean isCanceled() {
        return getStatus() == STATUS_CANCELED;
    }


    public long getBeginTime() {
        return beginTime;
    }

    protected void pause() {
        pauseTime = SystemClock.elapsedRealtime();
        connectTimes = 0;
        setStatus(STATUS_PAUSED);
    }

    protected void cancel() {
        endTime = SystemClock.elapsedRealtime();
        setStatus(STATUS_CANCELED);
    }

    protected void error() {
        endTime = SystemClock.elapsedRealtime();
        setStatus(STATUS_ERROR);
    }

    protected void successful() {
        endTime = SystemClock.elapsedRealtime();
        setStatus(STATUS_SUCCESSFUL);
    }

   protected void setCalculateMD5(boolean calculateMD5) {
        this.calculateMD5 = calculateMD5;
    }

    boolean isSuccessful() {
        return getStatus() == STATUS_SUCCESSFUL;
    }

    protected void completed() {
        endTime = SystemClock.elapsedRealtime();
    }

    protected void destroy() {
        this.mId = -1;
        this.mUrl = null;
        this.mContext = null;
        this.mFile = null;
        this.mIsParallelDownload = false;
        mIsForceDownload = false;
        mEnableIndicator = true;
        mDownloadIcon = android.R.drawable.stat_sys_download;
        mDownloadDoneIcon = android.R.drawable.stat_sys_download_done;
        mIsParallelDownload = true;
        mIsBreakPointDownload = true;
        mUserAgent = "";
        mContentDisposition = "";
        mMimetype = "";
        mContentLength = -1L;
        if (mHeaders != null) {
            mHeaders.clear();
            mHeaders = null;
        }
        retry = 3;
        fileMD5 = "";
        targetCompareMD5 = "";
        this.calculateMD5 = false;
//		status.set(STATUS_NEW);
    }

    DownloadingListener getDownloadingListener() {
        return mDownloadingListener;
    }

    protected DownloadTask
    setDownloadingListener(DownloadingListener downloadingListener) {
        mDownloadingListener = downloadingListener;
        return this;
    }

    public DownloadListener getDownloadListener() {
        return mDownloadListener;
    }


    protected DownloadTask setDownloadListener(DownloadListener downloadListener) {
        mDownloadListener = downloadListener;
        return this;
    }

    protected DownloadTask setDownloadListenerAdapter(DownloadListenerAdapter downloadListenerAdapter) {
        setDownloadListener(downloadListenerAdapter);
        setDownloadingListener(downloadListenerAdapter);
        setDownloadStatusListener(downloadListenerAdapter);
        return this;
    }

    void setTotalsLength(long totalsLength) {
        mTotalsLength = totalsLength;
    }

    public long getTotalsLength() {
        return mTotalsLength;
    }

    public long getLoaded() {
        return loaded;
    }

    void setLoaded(long loaded) {
        this.loaded = loaded;
    }

    protected DownloadTask setBreakPointDownload(boolean breakPointDownload) {
        mIsBreakPointDownload = breakPointDownload;
        return this;
    }

    protected DownloadTask setForceDownload(boolean force) {
        mIsForceDownload = force;
        return this;
    }

    protected DownloadTask setIcon(@DrawableRes int icon) {
        this.mDownloadIcon = icon;
        return this;
    }

    protected DownloadTask setParallelDownload(boolean parallelDownload) {
        mIsParallelDownload = parallelDownload;
        return this;
    }

    protected DownloadTask addHeader(String key, String value) {
        if (this.mHeaders == null) {
            this.mHeaders = new HashMap<>();
        }
        this.mHeaders.put(key, value);
        return this;
    }

    protected DownloadTask autoOpenIgnoreMD5() {
        mAutoOpen = true;
        if (mFile != null && TextUtils.isEmpty(authority)) {
            Runtime.getInstance().logError(TAG, "Custom file path, you must specify authority, otherwise the auto open should be closed. ");
            this.mAutoOpen = false;
        }
        return this;
    }

    protected DownloadTask autoOpenWithMD5(String md5) {
        if (TextUtils.isEmpty(md5)) {
            return this;
        }
        mAutoOpen = true;
        if (mFile != null && TextUtils.isEmpty(authority)) {
            Runtime.getInstance().logError(TAG, "Custom file path, you must specify authority, otherwise the auto open should be closed. ");
            this.mAutoOpen = false;
        }
        this.targetCompareMD5 = md5;
        this.calculateMD5 = true;
        return this;
    }

    protected DownloadTask closeAutoOpen() {
        mAutoOpen = false;
        return this;
    }

    protected DownloadTask setDownloadTimeOut(long downloadTimeOut) {
        this.downloadTimeOut = downloadTimeOut;
        return this;
    }

    protected DownloadTask setConnectTimeOut(long connectTimeOut) {
        this.connectTimeOut = connectTimeOut;
        return this;
    }

    protected DownloadTask setBlockMaxTime(long blockMaxTime) {
        this.blockMaxTime = blockMaxTime;
        return this;
    }

    protected DownloadTask setUserAgent(String userAgent) {
        this.mUserAgent = userAgent;
        return this;
    }

    DownloadTask setContentLength(long contentLength) {
        this.mContentLength = contentLength;
        return this;
    }

    DownloadTask setMimetype(String mimetype) {
        this.mMimetype = mimetype;
        return this;
    }

    DownloadTask setContentDisposition(String contentDisposition) {
        this.mContentDisposition = contentDisposition;
        return this;
    }

    protected DownloadTask setUrl(String url) {
        this.mUrl = url;
        return this;
    }

    protected DownloadTask setDownloadDoneIcon(@DrawableRes int icon) {
        this.mDownloadDoneIcon = icon;
        return this;
    }

    protected DownloadTask setQuickProgress(boolean quickProgress) {
        this.quickProgress = quickProgress;
        return this;
    }

    protected DownloadTask setTargetCompareMD5(String targetCompareMD5) {
        this.targetCompareMD5 = targetCompareMD5;
        if (!TextUtils.isEmpty(this.targetCompareMD5)) {
            calculateMD5 = true;
        }
        return this;
    }

    DownloadTask setFileMD5(String fileMD5) {
        this.fileMD5 = fileMD5;
        return this;
    }


    @Override
    public String getFileMD5() {
        if (TextUtils.isEmpty(fileMD5)) {
            this.fileMD5 = Runtime.getInstance().md5(mFile);
            if (fileMD5 == null) {
                fileMD5 = "";
            }
        }
        return super.getFileMD5();
    }

    protected DownloadTask setRetry(int retry) {
        if (retry > 5) {
            retry = 5;
        }
        if (retry < 0) {
            retry = 0;
        }
        this.retry = retry;
        return this;
    }

    protected void createNotifier() {
        if (mDownloadNotifier != null) {
            mDownloadNotifier.initBuilder(this);
        } else {
            Context mContext = this.getContext().getApplicationContext();
            if (null != mContext && this.isEnableIndicator()) {
                mDownloadNotifier = new DownloadNotifier(mContext, this.getId());
                mDownloadNotifier.initBuilder(this);
            }
        }
        if (null != this.mDownloadNotifier) {
            mDownloadNotifier.onPreDownload();
        }
    }

    String getRedirect() {
        return redirect;
    }

    void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    @Override
    public DownloadTask clone() {
        try {
            DownloadTask downloadTask = new DownloadTask();
            this.copy(downloadTask);
            return downloadTask;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return new DownloadTask();
        }
    }

    boolean isCompleted() {
        int status = getStatus();
        return status == STATUS_CANCELED || status == STATUS_PAUSED || status == STATUS_SUCCESSFUL || status == STATUS_ERROR;
    }

    public boolean isUniquePath() {
        return uniquePath;
    }

    protected void setUniquePath(boolean uniquePath) {
        this.uniquePath = uniquePath;
    }


    public DownloadStatusListener getDownloadStatusListener() {
        return mDownloadStatusListener;
    }

    void setDownloadStatusListener(DownloadStatusListener downloadStatusListener) {
        mDownloadStatusListener = downloadStatusListener;
    }

    Throwable getThrowable() {
        return mThrowable;
    }

    void setThrowable(Throwable throwable) {
        mThrowable = throwable;
    }

    void await() throws InterruptedException {
        if (mutex == null) {
            return;
        }
        mutex.lock();
        try {
            while (!isCompleted()) {
                isAWait = true;
                mCondition.await();
            }
        } finally {
            mutex.unlock();
            isAWait = false;
        }
    }

    void anotify() {
        if (mutex == null) {
            return;
        }
        mutex.lock();
        try {
            mCondition.signalAll();
        } finally {
            mutex.unlock();
        }
    }
}
