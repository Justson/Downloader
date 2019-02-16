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

import android.support.annotation.DrawableRes;

import java.io.Serializable;
import java.util.Map;

/**
 * @author cenxiaozhong
 * @date 2019/2/8
 * @since 1.0.0
 */
public class Extra implements Serializable, Cloneable {

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
	protected int mIcon = R.drawable.ic_file_download_black_24dp;
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
	protected String mUserAgent;
	/**
	 * Header
	 */
	protected Map<String, String> mHeaders;
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
	protected int connectTimeOut = 10 * 1000;
	/**
	 * 以8KB位单位，默认60s ，如果60s内无法从网络流中读满8KB数据，则抛出异常 。
	 */
	protected int blockMaxTime = 10 * 60 * 1000;

	public Map<String, String> getHeaders() {
		return mHeaders;
	}

	protected Extra() {

	}

	public int getBlockMaxTime() {
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

	public int getConnectTimeOut() {
		return connectTimeOut;
	}

	public int getIcon() {
		return mIcon;
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

	@Override
	protected Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return new Extra();
	}
}
