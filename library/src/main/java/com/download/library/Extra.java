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


import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.DrawableRes;

/**
 * @author cenxiaozhong
 * @date 2019/2/8
 * @since 1.0.0
 */
public abstract class Extra implements Serializable, Cloneable {

    /**
     * 强制下载
     */
    protected boolean mIsForceDownload = false;
    /**
     * 显示系统通知
     */
    protected boolean mEnableIndicator = true;
    /**
     * 通知icon
     */
    @DrawableRes
    protected int mDownloadIcon = android.R.drawable.stat_sys_download;
    @DrawableRes
    protected int mDownloadDoneIcon = android.R.drawable.stat_sys_download_done;
    /**
     * 并行下载
     */
    protected boolean mIsParallelDownload = true;
    /**
     * 断点续传，分块传输该字段无效
     */
    protected boolean mIsBreakPointDownload = true;
    /**
     * 当前下载链接
     */
    protected String mUrl;
    /**
     * ContentDisposition ，提取文件名 ，如果ContentDisposition不指定文件名，则从url中提取文件名
     */
    protected String mContentDisposition;
    /**
     * 文件大小
     */
    protected long mContentLength;
    /**
     * 文件类型
     */
    protected String mMimetype;
    /**
     * UA
     */
    protected String mUserAgent = "";
    /**
     * Header
     */
    protected HashMap<String, String> mHeaders;
    /**
     * 下载文件完成，是否自动打开该文件
     */
    protected boolean mAutoOpen = false;
    /**
     * 超时时长默认为两小时
     */
    protected long downloadTimeOut = Long.MAX_VALUE;
    /**
     * 连接超时， 默认10s
     */
    protected long connectTimeOut = 10L * 1000L;
    /**
     * 以8KB位单位，默认60s ，如果60s内无法从网络流中读满8KB数据，则抛出异常 。
     */
    protected long blockMaxTime = 10L * 60L * 1000L;

    /**
     * 快速回调进度,默认1200毫秒回调一次
     */
    protected boolean quickProgress = false;
    /**
     * 文件md5值，当targetCompareMD5与fileMD5一致时执行auto open
     */
    protected String targetCompareMD5 = "";
    /**
     * 文件md5值，当targetCompareMD5与fileMD5一致时执行auto open
     */
    protected String fileMD5 = "";
    /**
     * 下载失败重试次数
     */
    protected int retry = 3;

    /**
     * 是否计算md5
     */
    protected boolean calculateMD5;


    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    protected Extra() {

    }

    public long getBlockMaxTime() {
        return blockMaxTime;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public String getContentDisposition() {
        return mContentDisposition;
    }

    public String getMimetype() {
        return mMimetype;
    }

    public long getContentLength() {
        return mContentLength;
    }

    public boolean isForceDownload() {
        return mIsForceDownload;
    }

    public boolean isEnableIndicator() {
        return mEnableIndicator;
    }

    public long getDownloadTimeOut() {
        return downloadTimeOut;
    }

    public long getConnectTimeOut() {
        return connectTimeOut;
    }

    public int getDownloadIcon() {
        return mDownloadIcon;
    }

    public boolean isParallelDownload() {
        return mIsParallelDownload;
    }

    public boolean isBreakPointDownload() {
        return mIsBreakPointDownload;
    }

    public boolean isAutoOpen() {
        return mAutoOpen;
    }

    public boolean isQuickProgress() {
        return quickProgress;
    }

    public int getDownloadDoneIcon() {
        return mDownloadDoneIcon;
    }

    public String getTargetCompareMD5() {
        return targetCompareMD5 == null ? "" : targetCompareMD5;
    }

    public String getFileMD5() {
        return fileMD5;
    }

    public int getRetry() {
        return retry;
    }

    public boolean isCalculateMD5() {
        return calculateMD5;
    }



    protected Extra copy(Extra copy) {
        copy.mIsForceDownload = this.mIsForceDownload;
        copy.mEnableIndicator = this.mEnableIndicator;
        copy.mDownloadIcon = this.mDownloadIcon;
        copy.mDownloadDoneIcon = this.mDownloadDoneIcon;
        copy.mIsParallelDownload = this.mIsParallelDownload;
        copy.mIsBreakPointDownload = this.mIsBreakPointDownload;
        copy.mUrl = this.mUrl;
        copy.mContentDisposition = this.mContentDisposition;
        copy.mContentLength = this.mContentLength;
        copy.mMimetype = this.mMimetype;
        copy.mUserAgent = this.mUserAgent;
        if (null != mHeaders) {
            try {
                copy.mHeaders = (HashMap<String, String>) this.mHeaders.clone();
            } catch (Throwable ignored) {
                ignored.printStackTrace();
            }
        } else {
            copy.mHeaders = null;
        }
        copy.mAutoOpen = this.mAutoOpen;
        copy.downloadTimeOut = this.downloadTimeOut;
        copy.connectTimeOut = this.connectTimeOut;
        copy.blockMaxTime = this.blockMaxTime;
        copy.quickProgress = this.quickProgress;
        copy.targetCompareMD5 = this.targetCompareMD5;
        copy.fileMD5 = this.fileMD5;
        copy.calculateMD5 = this.calculateMD5;
        return copy;
    }
}
