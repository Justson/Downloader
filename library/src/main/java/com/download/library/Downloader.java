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

import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static com.download.library.FileComparator.COMPARE_RESULT_REDOWNLOAD_COVER;
import static com.download.library.FileComparator.COMPARE_RESULT_SUCCESSFUL;
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

/**
 * @author cenxiaozhong
 * @date 2017/5/13
 */
public class Downloader extends com.download.library.AsyncTask implements IDownloader<DownloadTask>, ExecuteTask {

    /**
     * 下载参数
     */
    protected volatile DownloadTask mDownloadTask;
    /**
     * 已经下载的大小
     */
    private volatile long mLoaded = 0L;
    /**
     * 总大小
     */
    protected volatile long mTotals = -1L;
    /**
     * 上一次下载，文件缓存长度
     */
    private long mLastLoaded = 0L;
    /**
     * 耗时
     */
    private long mUsedTime = 0L;
    /**
     * 上一次更新通知的时间
     */
    private long mLastTime = 0L;
    /**
     * 下载开始时间
     */
    private volatile long mBeginTime = 0L;
    /**
     * 当前下载平均速度
     */
    private volatile long mAverageSpeed = 0L;
    /**
     * 下载异常
     */
    protected volatile Throwable mThrowable;
    /**
     * 下载最大时长
     */
    protected long mDownloadTimeOut = Long.MAX_VALUE;
    /**
     * 连接超时
     */
    protected long mConnectTimeOut = 10000L;

    /**
     * log filter
     */
    private static final String TAG = Runtime.PREFIX + Downloader.class.getSimpleName();
    /**
     * Download read buffer size
     */
    private static final int BUFFER_SIZE = 1024 * 8;
    /**
     * 最多允许7次重定向
     */
    private static final int MAX_REDIRECTS = 7;
    private static final int HTTP_TEMP_REDIRECT = 307;
    public static final int ERROR_NETWORK_CONNECTION = 0x4000;
    public static final int ERROR_RESPONSE_STATUS = 0x4001;
    public static final int ERROR_STORAGE = 0x4002;
    public static final int ERROR_TIME_OUT = 0x4003;
    public static final int ERROR_USER_PAUSE = 0x4004;
    public static final int ERROR_USER_CANCEL = 0x4006;
    public static final int ERROR_SHUTDOWN = 0x4007;
    public static final int ERROR_TOO_MANY_REDIRECTS = 0x4008;
    public static final int ERROR_LOAD = 0x4009;
    public static final int ERROR_RESOURCE_NOT_FOUND = 0x4010;
    public static final int ERROR_MD5 = 0x4011;
    public static final int ERROR_SERVICE = 0x5003;
    public static final int SUCCESSFUL = 0x2000;
    public static final int HTTP_RANGE_NOT_SATISFIABLE = 4016;
    protected static final SparseArray<String> DOWNLOAD_MESSAGE = new SparseArray<>(13);
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    protected volatile boolean enableProgress = false;
    protected boolean mCallbackInMainThread = false;
    protected boolean quickProgress = false;

    private StringBuffer mDownloadMessage = new StringBuffer();

    static {
        DOWNLOAD_MESSAGE.append(ERROR_NETWORK_CONNECTION, "Network connection error . ");
        DOWNLOAD_MESSAGE.append(ERROR_RESPONSE_STATUS, "Response code non-200 or non-206 . ");
        DOWNLOAD_MESSAGE.append(ERROR_STORAGE, "Insufficient memory space . ");
        DOWNLOAD_MESSAGE.append(ERROR_SHUTDOWN, "Shutdown . ");
        DOWNLOAD_MESSAGE.append(ERROR_TIME_OUT, "Download time is overtime . ");
        DOWNLOAD_MESSAGE.append(ERROR_USER_CANCEL, "The user canceled the download . ");
        DOWNLOAD_MESSAGE.append(ERROR_RESOURCE_NOT_FOUND, "Resource not found . ");
        DOWNLOAD_MESSAGE.append(ERROR_USER_PAUSE, "paused . ");
        DOWNLOAD_MESSAGE.append(ERROR_LOAD, "IO Error . ");
        DOWNLOAD_MESSAGE.append(ERROR_SERVICE, "Service Unavailable . ");
        DOWNLOAD_MESSAGE.append(ERROR_TOO_MANY_REDIRECTS, "Too many redirects . ");
        DOWNLOAD_MESSAGE.append(ERROR_MD5, "Md5 check fails . ");
        DOWNLOAD_MESSAGE.append(SUCCESSFUL, "Download successful . ");
    }

    protected Downloader() {
    }

    static ExecuteTask create(DownloadTask downloadTask) {
        Downloader downloader = new Downloader();
        downloader.mDownloadTask = downloadTask;
        downloader.mTotals = downloadTask.getTotalsLength();
        downloader.mDownloadTimeOut = downloadTask.getDownloadTimeOut();
        downloader.mConnectTimeOut = downloadTask.getConnectTimeOut();
        downloader.quickProgress = downloadTask.isQuickProgress();
        downloader.enableProgress = downloadTask.isEnableIndicator() || null != downloadTask.getDownloadingListener();
        return downloader;
    }

    void checkIsNullTask(DownloadTask downloadTask) {
        if (null == downloadTask) {
            throw new NullPointerException("downloadTask can't be null.");
        }
        if (null == downloadTask.getContext()) {
            throw new NullPointerException("context can't be null.");
        }
    }


    private boolean checkSpace() {
        DownloadTask downloadTask = this.mDownloadTask;
        if (downloadTask.getTotalsLength() - downloadTask.getFile().length() > (getFsAvailableSize(downloadTask.getFile().getParent()) - (100 * 1024 * 1024))) {
            Runtime.getInstance().logError(TAG, " 空间不足");
            return false;
        }
        return true;
    }

    public static long getFsAvailableSize(final String anyPathInFs) {
        if (TextUtils.isEmpty(anyPathInFs)) {
            return 0;
        }
        try {
            StatFs statFs = new StatFs(anyPathInFs);
            long blockSize;
            long availableSize;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blockSize = statFs.getBlockSizeLong();
                availableSize = statFs.getAvailableBlocksLong();
            } else {
                blockSize = statFs.getBlockSize();
                availableSize = statFs.getAvailableBlocks();
            }
            return blockSize * availableSize;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return 0L;
    }

    private boolean checkNet() {
        DownloadTask downloadTask = this.mDownloadTask;
        if (!downloadTask.isForceDownload()) {
            return Runtime.getInstance().checkWifi(downloadTask.getContext());
        } else {
            return Runtime.getInstance().checkNetwork(downloadTask.getContext());
        }
    }

    protected Integer doInBackground() {
        int error = ERROR_LOAD;
        DownloadTask downloadTask = mDownloadTask;
        if (downloadTask.isPausing()) {
            downloadTask.pause();
            return ERROR_USER_PAUSE;
        }
        if (downloadTask.isPaused()) {
            return ERROR_USER_PAUSE;
        }
        if (downloadTask.isCanceled()) {
            return ERROR_USER_CANCEL;
        }

        if (downloadTask.isDataURI()) {
            error = transferDataFromUrl();
            return error;
        }

        this.mBeginTime = SystemClock.elapsedRealtime();
        if (!checkNet()) {
            Runtime.getInstance().logError(TAG, " Network error,isForceDownload:" + mDownloadTask.isForceDownload());
            downloadTask.error();
            return ERROR_NETWORK_CONNECTION;
        }
        mDownloadMessage.append("\r\n").append("=============").append("\n");
        mDownloadMessage.append("Download Message").append("\n");
        mDownloadMessage.append("downloadTask id=").append(downloadTask.getId()).append("\n");
        mDownloadMessage.append("url=").append(downloadTask.getUrl()).append("\n");
        try {
            mDownloadMessage.append("file=").append(downloadTask.getFile() == null ? "" : downloadTask.getFile().getCanonicalPath()).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String name = Thread.currentThread().getName();
        Thread.currentThread().setName("pool-download-thread-" + Runtime.getInstance().generateGlobalThreadId());
        try {
            downloadTask.setStatus(DownloadTask.STATUS_DOWNLOADING);
            IOException ioException = null;
            for (int i = 0; i <= downloadTask.retry; i++) {
                try {
                    error = doDownload();
                } catch (IOException e) {
                    this.mThrowable = ioException = e;
                    error = ERROR_LOAD;
                    if (Runtime.getInstance().isDebug()) {
                        e.printStackTrace();
                    }
                }
                if (ioException == null) {
                    break;
                } else {
                    if (i == downloadTask.retry) {
                        downloadTask.error();
                        mDownloadTask.setThrowable(ioException);
                    }
                    mDownloadMessage.append("download error message: ").append(ioException.getMessage()).append("\n");
                }
                if (i + 1 <= downloadTask.retry) {
                    mDownloadMessage.append("download error , retry ").append(i + 1).append("\n");
                }
            }
            try {
                mDownloadMessage.append("final output file=").append(downloadTask.getFile() == null ? "" : downloadTask.getFile().getCanonicalPath()).append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (downloadTask.getHeaders() != null && !downloadTask.getHeaders().isEmpty()) {
                mDownloadMessage.append("custom request headers=").append(downloadTask.getHeaders().toString()).append("\n");
            }
            mDownloadMessage.append("error=").append("0x" + Integer.toHexString(error)).append("\n");
            mDownloadMessage.append("error table: ERROR_NETWORK_CONNECTION = 0x4000,ERROR_RESPONSE_STATUS = 0x4001,ERROR_STORAGE = 0x4002,ERROR_TIME_OUT = 0x4003,ERROR_USER_PAUSE = 0x4004,ERROR_USER_CANCEL = 0x4006,ERROR_SHUTDOWN = 0x4007,ERROR_TOO_MANY_REDIRECTS = 0x4008,ERROR_LOAD = 0x4009,ERROR_RESOURCE_NOT_FOUND = 0x4010,ERROR_MD5 = 0x4011,ERROR_SERVICE = 0x5003,SUCCESSFUL = 0x2000,HTTP_RANGE_NOT_SATISFIABLE = 4016").append("\n");
            mDownloadMessage.append("error message=").append(DOWNLOAD_MESSAGE.get(error)).append("\n");
            mDownloadMessage.append("mLoaded=").append(mLoaded).append("\n");
            mDownloadMessage.append("mLastLoaded=").append(mLastLoaded).append("\n");
            mDownloadMessage.append("mLoaded+mLastLoaded=").append(mLoaded + mLastLoaded).append("\n");
            mDownloadMessage.append("totals=").append(this.mTotals).append("\n");
            if (downloadTask.getStatus() == DownloadTask.STATUS_SUCCESSFUL || error == ERROR_MD5) {
                mDownloadMessage.append("isCalculateMD5=").append(downloadTask.isCalculateMD5()).append("\n");
                if (!TextUtils.isEmpty(downloadTask.fileMD5)) {
                    mDownloadMessage.append("FileMD5=").append(downloadTask.fileMD5).append("\n");
                } else {
                    mDownloadMessage.append("FileMD5=").append("''").append("\n");
                }
            }
            if (!TextUtils.isEmpty(downloadTask.getTargetCompareMD5())) {
                mDownloadMessage.append("targetCompareMD5=").append(downloadTask.getTargetCompareMD5()).append("\n");
            }
            mDownloadMessage.append("current downloadTask status=").append(downloadTask.getStatus()).append("\n");
            mDownloadMessage.append("status table: STATUS_NEW = 1000,STATUS_PENDDING = 1001,STATUS_DOWNLOADING = 1002,STATUS_PAUSING = 1003,STATUS_PAUSED = 1004,STATUS_SUCCESSFUL = 1005,STATUS_CANCELED = 1006,STATUS_ERROR = 1007").append("\n");
            mDownloadMessage.append("used time=").append(downloadTask.getUsedTime()).append("ms").append("\n");
            mDownloadMessage.append("\r\n");
            Runtime.getInstance().log(TAG, "\r\n" + mDownloadMessage.toString());
        } finally {
            Thread.currentThread().setName(name);
        }
        return error;
    }

    private int transferDataFromUrl() {
        DownloadTask downloadTask = mDownloadTask;
        String url = downloadTask.getUrl();
        if (!url.startsWith("data")) {
            return ERROR_LOAD;
        }
        if (!url.contains(",")) {
            return ERROR_LOAD;
        }
        String base64EncodedString = extractContent();
        if (TextUtils.isEmpty(base64EncodedString)) {
            return ERROR_LOAD;
        }
        byte[] decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT);
        downloadTask.setContentLength(decodedBytes.length);
        downloadTask.setTotalsLength(decodedBytes.length);
        RandomAccessFile out = null;
        try {
            out = new LoadingRandomAccessFile(downloadTask.getFile());
            out.seek(0L);
            out.write(decodedBytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            progressFinaly();
            closeIO(out);
        }
        return SUCCESSFUL;
    }


    String extractContent() {
        DownloadTask downloadTask = mDownloadTask;
        if (!downloadTask.isDataURI()) {
            return "";
        }
        String url = downloadTask.mUrl;
        int end;
        if ((end = url.indexOf(",", 5)) <= 5) {
            return "";
        }
        int start = -1;
        for (int i = end; i >= 5; i--) {
            String alpha = String.valueOf(url.charAt(i));
            if (alpha.equals(";") || alpha.equals(":")) {
                start = i + 1;
                break;
            }
        }

        String chartset = url.substring(start, end);
        if (!chartset.equalsIgnoreCase("base64")) {
            Runtime.getInstance().log(TAG, "unsupport chartset:" + chartset);
            return "";
        }
        String base64EncodedString = url.substring(url.indexOf(",", 5) + 1);
        return base64EncodedString;
    }

    private int doDownload() throws IOException {
        DownloadTask downloadTask = this.mDownloadTask;
        downloadTask.updateTime(this.mBeginTime);
        downloadTask.resetConnectTimes();
        int redirectionCount = 0;
        URL url;
        if (TextUtils.isEmpty(downloadTask.redirect)) {
            url = new URL(downloadTask.getUrl());
        } else {
            url = new URL(downloadTask.getRedirect());
        }
        HttpURLConnection httpURLConnection = null;
        try {
            for (; redirectionCount++ <= MAX_REDIRECTS; ) {
                mDownloadMessage.append("redirectionCount=").append(redirectionCount).append("\n");
                if (null != httpURLConnection) {
                    httpURLConnection.disconnect();
                }
                if (downloadTask.connectTimes <= 0) {
                    httpURLConnection = createUrlConnection(url);
                    settingHeaders(downloadTask, httpURLConnection);
                    try {
                        httpURLConnection.connect();
                    } catch (IOException e) {
                        throw e;
                    }
                } else {
                    httpURLConnection = createUrlConnection(url);
                    settingHeaders(downloadTask, httpURLConnection);
                    rangeHeaders(downloadTask, httpURLConnection);
                    try {
                        httpURLConnection.connect();
                    } catch (IOException e) {
                        throw e;
                    }
                }

                if (downloadTask.isPausing()) {
                    downloadTask.pause();
                    return ERROR_USER_PAUSE;
                }

                if (downloadTask.isPaused()) {
                    return ERROR_USER_PAUSE;
                }
                if (downloadTask.isCanceled()) {
                    return ERROR_USER_CANCEL;
                }

                boolean isEncodingChunked = false;
                try {
                    isEncodingChunked = "chunked".equalsIgnoreCase(
                            httpURLConnection.getHeaderField("Transfer-Encoding"));
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
                long contentLength = -1;
                final boolean hasLength = ((contentLength = getHeaderFieldLong(httpURLConnection, "Content-Length")) > 0);
                // 获取不到文件长度
                final boolean finishKnown = (isEncodingChunked && hasLength || !isEncodingChunked && !hasLength);
                int responseCode = httpURLConnection.getResponseCode();
                Runtime.getInstance().log(TAG, "responseCode:" + responseCode);
                mDownloadMessage.append("responseCode=").append(responseCode).append("\n");
                if (responseCode == HTTP_PARTIAL && !hasLength) {
                    downloadTask.successful();
                    return SUCCESSFUL;
                }
                switch (responseCode) {
                    case HTTP_OK:
                        if (finishKnown) {
                            Runtime.getInstance().logError(TAG, " error , giving up ,"
                                    + "  EncodingChunked:" + isEncodingChunked
                                    + "  hasLength:" + hasLength + " response length:" + contentLength + " responseCode:" + responseCode);
                            downloadTask.error();
                            return ERROR_LOAD;
                        }
                        this.mTotals = contentLength;
                        if (downloadTask.connectTimes <= 0) {
                            start(httpURLConnection);
                            downloadTask.connectTimes++;
                            if (downloadTask.getFile().length() > 0 && !isEncodingChunked) {
                                if (downloadTask.getFile().length() == contentLength) {
                                    mDownloadMessage.append("file already exist, file name=").append(downloadTask.getFile().getName()).append(", file length==contentLength").append(",contentLength=").append(contentLength).append("\n");
                                    String fileMD5 = Runtime.getInstance().md5(downloadTask.getFile());
                                    int compareResult = Runtime.getInstance().getFileComparator().compare(downloadTask.getUrl(),
                                            downloadTask.getFile(), downloadTask.getTargetCompareMD5(), fileMD5);
                                    mDownloadMessage.append("compareResult=").append(compareResult).append("\n");
                                    mDownloadMessage.append("compare Result table:").append("COMPARE_RESULT_SUCCESSFUL = 1").append(",COMPARE_RESULT_REDOWNLOAD_COVER = 2").append(",COMPARE_RESULT_REDOWNLOAD_RENAME = 3").append("\n");
                                    if (compareResult == COMPARE_RESULT_SUCCESSFUL) {
                                        downloadTask.setFileMD5(fileMD5);
                                        mLastLoaded = contentLength;
                                        publishProgressUpdate(1);
                                        downloadTask.successful();
                                        return SUCCESSFUL;
                                    } else if (compareResult == COMPARE_RESULT_REDOWNLOAD_COVER) {
                                        downloadTask.getFile().delete();
                                        downloadTask.getFile().createNewFile();
                                    } else {
                                        for (int i = 1; i < Integer.MAX_VALUE; i++) {
                                            String fileName = "(" + i + ")" + downloadTask.getFile().getName();
                                            File targetFile = new File(downloadTask.getFile().getParent(), fileName);
                                            if (targetFile.exists()) {
                                                if (targetFile.length() >= contentLength) {
                                                    Runtime.getInstance().log(TAG, "rename download , targetFile exists:" + targetFile.getName());
                                                } else {
                                                    mDownloadMessage.append("origin file name=").append(downloadTask.getFile().getName()).append(" target file name=").append(targetFile.getName()).append(",current target file length=").append(targetFile.length()).append("\n");
                                                    downloadTask.setFileSafe(targetFile);
                                                    break;
                                                }
                                            } else {
                                                mDownloadMessage.append("target file is not exist, create new target file ,file name=").append(targetFile.getName()).append("\n");

                                                targetFile.createNewFile();
                                                downloadTask.setFileSafe(targetFile);
                                                break;
                                            }
                                        }
                                    }
                                } else if (downloadTask.getFile().length() >= contentLength) {
                                    mDownloadMessage.append("file length error .").append("\n");
                                    downloadTask.getFile().delete();
                                    downloadTask.getFile().createNewFile();
                                }
                                continue;
                            }
                        }
                        if (isEncodingChunked) {
                            this.mTotals = -1L;
                        } else if (downloadTask.getFile().length() >= contentLength) {
                            this.mTotals = contentLength;
                            downloadTask.successful();
                            return SUCCESSFUL;
                        }
                        downloadTask.setTotalsLength(this.mTotals);
                        if (!isEncodingChunked && !checkSpace()) {
                            downloadTask.error();
                            return ERROR_STORAGE;
                        }
                        saveEtag(httpURLConnection);
                        downloadTask.setTotalsLength(this.mTotals);
                        mDownloadMessage.append("totals=").append(this.mTotals).append("\n");
                        return transferData(getInputStream(httpURLConnection),
                                new LoadingRandomAccessFile(downloadTask.getFile()),
                                false);
                    case HTTP_PARTIAL:
                        if (finishKnown) {
                            Runtime.getInstance().logError(TAG, " error , giving up ,"
                                    + "  EncodingChunked:" + isEncodingChunked
                                    + "  hasLength:" + hasLength + " response length:" + contentLength + " responseCode:" + responseCode);
                            downloadTask.error();
                            return ERROR_LOAD;
                        }
                        if (this.mTotals <= 0L) {
                            this.mTotals = contentLength + downloadTask.getFile().length();
                        }
                        if (this.mTotals > 0L && contentLength + downloadTask.getFile().length() != this.mTotals) {  // 服务端响应文件长度不正确，或者本地文件长度被修改。
                            downloadTask.error();
                            Runtime.getInstance().logError(TAG, "length error, this.mTotals:" + this.mTotals + " contentLength:" + contentLength + " file length:" + downloadTask.getFile().length());
                            return ERROR_LOAD;
                        }
                        downloadTask.setTotalsLength(this.mTotals);
                        if (!checkSpace()) {
                            downloadTask.error();
                            return ERROR_STORAGE;
                        }
                        Runtime.getInstance().log(TAG, "last:" + mLastLoaded + " totals:" + this.mTotals);
                        mDownloadMessage.append("last=").append(mLastLoaded).append(" totals=").append(this.mTotals).append("\n");
                        return transferData(getInputStream(httpURLConnection),
                                new LoadingRandomAccessFile(downloadTask.getFile()),
                                true);
                    case HTTP_RANGE_NOT_SATISFIABLE:
                        if (null != downloadTask.getFile()) {
                            Runtime.getInstance().log(TAG, "range not satisfiable .");
                            mDownloadMessage.append("range not satisfiable .").append("\n");
                            downloadTask.getFile().delete();
                            downloadTask.getFile().createNewFile();
                        }
                        break;
                    case HTTP_MOVED_PERM:
                    case HTTP_MOVED_TEMP:
                    case HTTP_SEE_OTHER:
                    case HTTP_TEMP_REDIRECT:
                        final String location = httpURLConnection.getHeaderField("Location");
                        if (TextUtils.isEmpty(location)) {
                            downloadTask.error();
                            return ERROR_SERVICE;
                        } else {
                            mDownloadMessage.append("original url=").append(httpURLConnection.getURL().toString()).append("  ,redirect url=" + location).append("\n");
                        }
                        try {
                            url = new URL(url, location);
                        } catch (MalformedURLException exception) {
                            downloadTask.error();
                            return ERROR_SERVICE;
                        }
                        downloadTask.setRedirect(url.toString());
                        continue;
                    case HTTP_NOT_FOUND:
                        return ERROR_RESOURCE_NOT_FOUND;
                    case HTTP_UNAVAILABLE:
                    case HTTP_INTERNAL_ERROR:
                    case HTTP_NOT_IMPLEMENTED:
                    case HTTP_BAD_GATEWAY:
                        downloadTask.error();
                        return ERROR_SERVICE;
                    default:
                        downloadTask.error();
                        return ERROR_RESPONSE_STATUS;
                }
            }
            downloadTask.error();
            return ERROR_TOO_MANY_REDIRECTS;
        } finally {
            if (null != httpURLConnection) {
                httpURLConnection.disconnect();
            }
        }
    }

    private void rangeHeaders(DownloadTask downloadTask, HttpURLConnection httpURLConnection) {
        if (null != downloadTask.getFile() && downloadTask.getFile().length() > 0) {
            httpURLConnection.setRequestProperty("Range", "bytes=" + (mLastLoaded = downloadTask.getFile().length()) + "-");
        }
        mDownloadMessage.append("range=").append(mLastLoaded).append("\n");
        httpURLConnection.setRequestProperty("Connection", "close");
    }

    private final void start(HttpURLConnection httpURLConnection) throws IOException {
        DownloadTask downloadTask = this.mDownloadTask;
        if (TextUtils.isEmpty(downloadTask.getContentDisposition())) {
            downloadTask.setContentDisposition(httpURLConnection.getHeaderField("Content-Disposition"));
            String fileName = Runtime.getInstance().getFileNameByContentDisposition(downloadTask.getContentDisposition());
            if (!TextUtils.isEmpty(fileName) && !downloadTask.getFile().getName().equals(fileName)) {
                File renameTarget = new File(downloadTask.getFile().getParent(), fileName);
                if (renameTarget.exists()) {
                    downloadTask.setFileSafe(renameTarget);
                    updateNotifierTitle();
                } else {
                    File originFile = downloadTask.getFile();
                    boolean success = downloadTask.getFile().renameTo(renameTarget);
                    if (success) {
                        downloadTask.setFileSafe(renameTarget);
                        updateNotifierTitle();
                        mDownloadMessage.append("origin=").append(originFile.getName()).append(" rename=").append(renameTarget.getName()).append("\n");
                        originFile.delete();
                    } else {
                    }
                }
            }
        }
        if (TextUtils.isEmpty(downloadTask.getMimetype())) {
            downloadTask.setMimetype(httpURLConnection.getHeaderField("Content-Type"));
        }
        if (TextUtils.isEmpty(downloadTask.getUserAgent())) {
            String ua = httpURLConnection.getHeaderField("User-Agent");
            if (ua == null) {
                ua = "";
            }
            downloadTask.setUserAgent(ua);
        }
        downloadTask.setContentLength(getHeaderFieldLong(httpURLConnection, "Content-Length"));
        onStart();
    }

    private void updateNotifierTitle() {
        DownloadTask downloadTask = this.mDownloadTask;
        DownloadNotifier downloadNotifier = downloadTask.mDownloadNotifier;
        if (null != downloadNotifier) {
            downloadNotifier.updateTitle(downloadTask);
        }
    }

    protected void onStart() throws IOException {
        final DownloadTask downloadTask = this.mDownloadTask;
        if (null != downloadTask && null != downloadTask.getDownloadListener()) {
            HANDLER.post(new Runnable() {
                @Override
                public void run() {
                    downloadTask.getDownloadListener().onStart(downloadTask.mUrl
                            , downloadTask.mUserAgent
                            , downloadTask.mContentDisposition
                            , downloadTask.mMimetype
                            , downloadTask.mTotalsLength
                            , downloadTask);
                }
            });
        }
    }

    private InputStream getInputStream(HttpURLConnection httpURLConnection) throws IOException {
        if ("gzip".equalsIgnoreCase(httpURLConnection.getContentEncoding())) {
            return new GZIPInputStream(httpURLConnection.getInputStream());
        } else if ("deflate".equalsIgnoreCase(httpURLConnection.getContentEncoding())) {
            return new InflaterInputStream(httpURLConnection.getInputStream(), new Inflater(true));
        } else {
            return httpURLConnection.getInputStream();
        }
    }

    private long getHeaderFieldLong(HttpURLConnection httpURLConnection, String name) {
        String field = httpURLConnection.getHeaderField(name);
        try {
            return null == field ? -1L : Long.parseLong(field);
        } catch (NumberFormatException e) {
            if (Runtime.getInstance().isDebug()) {
                e.printStackTrace();
            }
        }
        return -1L;
    }

    private void saveEtag(HttpURLConnection httpURLConnection) {
        String etag = httpURLConnection.getHeaderField("ETag");
        if (TextUtils.isEmpty(etag)) {
            return;
        }
        String url = mDownloadTask.getUrl();
        String urlMD5 = Runtime.getInstance().md5(url);
        Runtime.getInstance().log(TAG, "save etag:" + etag);
        StorageEngine storageEngine = Runtime.getInstance().getStorageEngine(mDownloadTask.mContext);
        storageEngine.save(urlMD5, etag);
    }

    private String getEtag() {
        String url = mDownloadTask.getUrl();
        String urlMD5 = Runtime.getInstance().md5(url);
        String etag = Runtime.getInstance().getStorageEngine(mDownloadTask.mContext).get(urlMD5, "-1");
        if (!TextUtils.isEmpty(etag) && !"-1".equals(etag)) {
            return etag;
        } else {
            return null;
        }
    }

    private HttpURLConnection createUrlConnection(URL url) throws IOException {
        DownloadTask downloadTask = this.mDownloadTask;
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setConnectTimeout((int) mConnectTimeOut);
        httpURLConnection.setInstanceFollowRedirects(false);
        httpURLConnection.setReadTimeout((int) downloadTask.getBlockMaxTime());
        httpURLConnection.setRequestProperty("Accept", "*/*");
        httpURLConnection.setRequestProperty("Accept-Encoding", "deflate,gzip");
        return httpURLConnection;
    }

    private void settingHeaders(DownloadTask downloadTask, HttpURLConnection httpURLConnection) {
        Map<String, String> headers = null;
        if (null != (headers = downloadTask.getHeaders()) &&
                !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (TextUtils.isEmpty(entry.getKey()) || TextUtils.isEmpty(entry.getValue())) {
                    continue;
                }
                httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        String eTag = "";
        if (!TextUtils.isEmpty((eTag = getEtag()))) {
            Runtime.getInstance().log(TAG, "Etag:" + eTag);
            httpURLConnection.setRequestProperty("If-Match", getEtag());
        }
    }


    @Override
    protected void onProgressUpdate(Integer... values) {
        DownloadTask downloadTask = this.mDownloadTask;
        DownloadNotifier downloadNotifier = downloadTask.mDownloadNotifier;
        try {
            long currentTime = SystemClock.elapsedRealtime();
            this.mUsedTime = currentTime - this.mBeginTime;
            if (mUsedTime == 0) {
                this.mAverageSpeed = 0;
            } else {
                this.mAverageSpeed = mLoaded * 1000 / this.mUsedTime;
            }
            if (values != null && values.length > 0 && values[0] == 1) {
                if (null != downloadNotifier) {
                    if (mTotals > 0) {
                        int mProgress = (int) ((mLastLoaded + mLoaded) / Float.valueOf(mTotals) * 100);
                        downloadNotifier.onDownloading(mProgress);
                    } else {
                        downloadNotifier.onDownloaded((mLastLoaded + mLoaded));
                    }
                }
            }
            if (null != downloadTask.getDownloadListener()) {
                downloadTask
                        .getDownloadingListener()
                        .onProgress(downloadTask.getUrl(), (mLastLoaded + mLoaded), mTotals, downloadTask.getUsedTime());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    private int transferData(InputStream inputStream, RandomAccessFile randomAccessFile, boolean isSeek) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        BufferedInputStream bis = new BufferedInputStream(inputStream, BUFFER_SIZE);
        RandomAccessFile out = randomAccessFile;
        DownloadTask downloadTask = mDownloadTask;
        mLoaded = 0L;
        try {
            if (isSeek) {
                out.seek(out.length());
            } else {
                out.seek(0);
                mLastLoaded = 0L;
            }
            int bytes = 0;
            while (!downloadTask.isPausing() && !downloadTask.isCanceled() && !downloadTask.isPaused()) {
                int n = -1;
                try {
                    n = bis.read(buffer, 0, BUFFER_SIZE);
                } catch (IOException e) {
                    downloadTask.error();
                    throw e;
                }
                if (n == -1) {
                    break;
                }
                out.write(buffer, 0, n);
                bytes += n;
                if ((SystemClock.elapsedRealtime() - this.mBeginTime) > mDownloadTimeOut) {
                    mDownloadTask.error();
                    return ERROR_TIME_OUT;
                }
            }
            if (downloadTask.isPausing()) {
                downloadTask.pause();
                return ERROR_USER_PAUSE;
            }
            if (downloadTask.isPaused()) {
                return ERROR_USER_PAUSE;
            }
            if (downloadTask.isCanceled()) {
                return ERROR_USER_CANCEL;
            }
            if (downloadTask.isCalculateMD5()) {
                String md5 = Runtime.getInstance().md5(mDownloadTask.mFile);
                mDownloadTask.setFileMD5(md5);
            }
            if (!TextUtils.isEmpty(downloadTask.getTargetCompareMD5())) {
                if (TextUtils.isEmpty(downloadTask.fileMD5)) {
                    String md5 = Runtime.getInstance().md5(mDownloadTask.mFile);
                    mDownloadTask.setFileMD5(md5);
                }
                if (!downloadTask.getTargetCompareMD5().equalsIgnoreCase(downloadTask.getFileMD5())) {
                    downloadTask.error();
                    return ERROR_MD5;
                }
            }
            progressFinaly();
            downloadTask.successful();
            return SUCCESSFUL;
        } finally {
            closeIO(out);
            closeIO(bis);
            closeIO(inputStream);
        }
    }

    public void closeIO(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public final DownloadTask cancel() {
        DownloadTask downloadTask = this.mDownloadTask;
        try {
            return downloadTask;
        } finally {
            downloadTask.cancel();
        }
    }

    @Override
    public int status() {
        DownloadTask downloadTask = mDownloadTask;
        return downloadTask == null ? DownloadTask.STATUS_NEW : downloadTask.getStatus();
    }


    @Override
    public boolean download(DownloadTask downloadTask) {
        return true;
    }


    private final DownloadTask pause() {
        DownloadTask downloadTask = this.mDownloadTask;
        try {
            return downloadTask;
        } finally {
            downloadTask.pausing();
        }
    }

    @Override
    public DownloadTask cancelDownload() {
        return cancel();
    }

    @Override
    public DownloadTask pauseDownload() {
        return pause();
    }

    @Override
    public DownloadTask getDownloadTask() {
        return this.mDownloadTask;
    }

    private final class LoadingRandomAccessFile extends RandomAccessFile {
        public LoadingRandomAccessFile(File file) throws FileNotFoundException {
            super(file, "rw");
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            super.write(buffer, offset, count);
            mLoaded += count;
            DownloadTask downloadTask = mDownloadTask;
            if (null != downloadTask) {
                downloadTask.setLoaded(mLastLoaded + mLoaded);
            }
            onProgress();
        }
    }

    private void progressFinaly() {
        long currentTime = SystemClock.elapsedRealtime();
        mLastTime = currentTime;
        publishProgressUpdate(1);
    }

    private void onProgress() {
        if (!enableProgress) {
            return;
        }
        if (quickProgress) {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - mLastTime < 1200) {
                publishProgressUpdate(0);
            } else {
                mLastTime = currentTime;
                publishProgressUpdate(1);
            }
        } else {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - mLastTime < 1200L) {
                return;
            }
            mLastTime = currentTime;
            publishProgressUpdate(1);
        }
    }

    private void publishProgressUpdate(int i) {
        if (mCallbackInMainThread) {
            publishProgress(i);
        } else {
            onProgressUpdate(i);
        }
    }

}
