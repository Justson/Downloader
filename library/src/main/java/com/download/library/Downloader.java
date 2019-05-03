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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static com.download.library.DownloadTask.STATUS_PAUSED;
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
public class Downloader extends AsyncTask<Void, Integer, Integer> implements IDownloader<DownloadTask>, ExecuteTask {

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
	protected int mConnectTimeOut = 10000;
	/**
	 * 通知
	 */
	private DownloadNotifier mDownloadNotifier;
	/**
	 * log filter
	 */
	private static final String TAG = Runtime.PREFIX + Downloader.class.getSimpleName();
	/**
	 * true 已经取消下载
	 */
	protected AtomicBoolean mIsCanceled = new AtomicBoolean(false);
	/**
	 * true 已经暂停下载
	 */
	protected AtomicBoolean mIsPaused = new AtomicBoolean(false);
	/**
	 * true 终止下载
	 */
	protected AtomicBoolean mIsShutdown = new AtomicBoolean(false);
	/**
	 * Download read buffer size
	 */
	private static final int BUFFER_SIZE = 1024 * 8;
	/**
	 * 最多允许7次重定向
	 */
	private static final int MAX_REDIRECTS = 7;
	private static final int HTTP_TEMP_REDIRECT = 307;
	public static final int ERROR_NETWORK_CONNECTION = 0x400;
	public static final int ERROR_RESPONSE_STATUS = 0x401;
	public static final int ERROR_STORAGE = 0x402;
	public static final int ERROR_TIME_OUT = 0x403;
	public static final int ERROR_USER_PAUSE = 0x404;
	public static final int ERROR_USER_CANCEL = 0x406;
	public static final int ERROR_SHUTDOWN = 0x407;
	public static final int ERROR_TOO_MANY_REDIRECTS = 0x408;
	public static final int ERROR_LOAD = 0x409;
	public static final int ERROR_RESOURCE_NOT_FOUND = 0x410;
	public static final int ERROR_SERVICE = 0x503;
	public static final int SUCCESSFUL = 0x200;
	public static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
	protected static final SparseArray<String> DOWNLOAD_MESSAGE = new SparseArray<>(11);
	protected static final Executor SERIAL_EXECUTOR = new SerialExecutor();
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());
	protected volatile boolean enableProgress = false;
	protected boolean mCallbackInMainThread = false;

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
		DOWNLOAD_MESSAGE.append(SUCCESSFUL, "Download successful . ");
	}

	protected Downloader() {
	}

	void checkIsNullTask(DownloadTask downloadTask) {
		if (null == downloadTask) {
			throw new NullPointerException("downloadTask can't be null.");
		}
		if (null == downloadTask.getContext()) {
			throw new NullPointerException("context can't be null.");
		}
	}

	@Override
	protected void onPreExecute() {
		DownloadTask downloadTask = this.mDownloadTask;
		if (null == downloadTask) {
			throw new NullPointerException("DownloadTask can't be null ");
		}
		if (null == downloadTask.getFile()) {
			File file = downloadTask.isUniquePath()
					? Runtime.getInstance().uniqueFile(downloadTask, null)
					: Runtime.getInstance().createFile(downloadTask.mContext, downloadTask);
			downloadTask.setFile(file);
			Runtime.getInstance().log(TAG, " file path:" + file.getAbsolutePath() + " isEnableIndicator:" + downloadTask.isEnableIndicator());
		} else if (downloadTask.getFile().isDirectory()) {
			File file = downloadTask.isUniquePath()
					? Runtime.getInstance().uniqueFile(downloadTask, downloadTask.getFile())
					: Runtime.getInstance().createFile(downloadTask.mContext, downloadTask, downloadTask.getFile());
			downloadTask.setFile(file);
			Runtime.getInstance().log(TAG, "uniqueFile");
		} else if (!downloadTask.getFile().exists()) {
			try {
				downloadTask.getFile().createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				downloadTask.setFile(null);
			}
		}
		if (null == downloadTask.getFile()) {
			throw new RuntimeException("target file can't be created . ");
		}
		downloadTask.setStatus(DownloadTask.STATUS_DOWNLOADING);
		createNotifier();
		if (null != this.mDownloadNotifier) {
			mDownloadNotifier.onPreDownload();
		}
	}

	private boolean checkSpace() {
		DownloadTask downloadTask = this.mDownloadTask;
		if (downloadTask.getTotalsLength() - downloadTask.getFile().length() > (getAvailableStorage() - (100 * 1024 * 1024))) {
			Runtime.getInstance().logError(TAG, " 空间不足");
			return false;
		}
		return true;
	}

	private long getAvailableStorage() {
		try {
			StatFs stat = new StatFs(Environment.getExternalStorageDirectory().toString());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
				return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
			} else {
				return (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
			}
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	private boolean checkNet() {
		DownloadTask downloadTask = this.mDownloadTask;
		if (!downloadTask.isForceDownload()) {
			return Runtime.getInstance().checkWifi(downloadTask.getContext());
		} else {
			return Runtime.getInstance().checkNetwork(downloadTask.getContext());
		}
	}

	@Override
	protected Integer doInBackground(Void... params) {
		int result = ERROR_LOAD;
		String name = Thread.currentThread().getName();
		Thread.currentThread().setName("pool-download-thread-" + Runtime.getInstance().generateGlobalThreadId());
		try {
			this.mBeginTime = SystemClock.elapsedRealtime();
			if (!checkNet()) {
				Runtime.getInstance().logError(TAG, " Network error,isForceDownload:" + mDownloadTask.isForceDownload());
				return ERROR_NETWORK_CONNECTION;
			}
			result = doDownload();
		} catch (IOException e) {
			this.mThrowable = e;
			if (Runtime.getInstance().isDebug()) {
				e.printStackTrace();
			}
		} finally {
			Thread.currentThread().setName(name);
		}
		return result;
	}

	private int doDownload() throws IOException {
		DownloadTask downloadTask = this.mDownloadTask;
		downloadTask.updateTime(this.mBeginTime);
		int redirectionCount = 0;
		URL url = new URL(downloadTask.getUrl());
		HttpURLConnection httpURLConnection = null;
		try {
			for (; redirectionCount++ <= MAX_REDIRECTS; ) {
				if (null != httpURLConnection) {
					httpURLConnection.disconnect();
				}
				if (downloadTask.connectTimes <= 0) {
					httpURLConnection = createUrlConnection(url);
					httpURLConnection.connect();
				} else {
					httpURLConnection = createUrlConnection(url);
					settingHeaders(downloadTask, httpURLConnection);
					httpURLConnection.connect();
				}

				final boolean isEncodingChunked = "chunked".equalsIgnoreCase(
						httpURLConnection.getHeaderField("Transfer-Encoding"));
				long contentLength = -1;
				final boolean hasLength = ((contentLength = getHeaderFieldLong(httpURLConnection, "Content-Length")) > 0);
				// 获取不到文件长度
				final boolean finishKnown = (isEncodingChunked && hasLength || !isEncodingChunked && !hasLength);
				int responseCode = httpURLConnection.getResponseCode();
				if (responseCode == HTTP_PARTIAL && !hasLength) {
					return SUCCESSFUL;
				}
				switch (responseCode) {
					case HTTP_OK:
						if (finishKnown) {
							Runtime.getInstance().logError(TAG, " error , giving up ,"
									+ "  EncodingChunked:" + isEncodingChunked
									+ "  hasLength:" + hasLength + " response length:" + contentLength + " responseCode:" + responseCode);
							return ERROR_LOAD;
						}
						this.mTotals = contentLength;
						if (downloadTask.connectTimes <= 0) {
							start(httpURLConnection);
							downloadTask.connectTimes++;
							if (downloadTask.getFile().length() > 0 && !isEncodingChunked) {
								continue;
							}
						}
						if (isEncodingChunked) {
							this.mTotals = -1L;
						} else if (downloadTask.getFile().length() >= contentLength) {
							this.mTotals = contentLength;
							return SUCCESSFUL;
						}
						downloadTask.setTotalsLength(this.mTotals);
						if (!isEncodingChunked && !checkSpace()) {
							return ERROR_STORAGE;
						}
						saveEtag(httpURLConnection);
						downloadTask.setTotalsLength(this.mTotals);
						return transferData(getInputStream(httpURLConnection),
								new LoadingRandomAccessFile(downloadTask.getFile()),
								false);
					case HTTP_PARTIAL:
						if (finishKnown) {
							Runtime.getInstance().logError(TAG, " error , giving up ,"
									+ "  EncodingChunked:" + isEncodingChunked
									+ "  hasLength:" + hasLength + " response length:" + contentLength + " responseCode:" + responseCode);
							return ERROR_LOAD;
						}
						if (isEncodingChunked) {
							this.mTotals = -1L;
						} else if (this.mTotals > 0L && contentLength + downloadTask.getFile().length() != this.mTotals) {  // 服务端响应文件长度不正确，或者本地文件长度被修改。
							return ERROR_LOAD;
						} else if (this.mTotals <= 0L) {
							this.mTotals = contentLength + downloadTask.getFile().length();
						}
						downloadTask.setTotalsLength(this.mTotals);
						if (!isEncodingChunked && !checkSpace()) {
							return ERROR_STORAGE;
						}
						return transferData(getInputStream(httpURLConnection),
								new LoadingRandomAccessFile(downloadTask.getFile()),
								!isEncodingChunked);
					case HTTP_RANGE_NOT_SATISFIABLE:
						if (null != downloadTask.getFile()) {
							Runtime.getInstance().log(TAG, " range not satisfiable .");
							downloadTask.getFile().delete();
							downloadTask.getFile().createNewFile();
						}
						break;
					case HTTP_MOVED_PERM:
					case HTTP_MOVED_TEMP:
					case HTTP_SEE_OTHER:
					case HTTP_TEMP_REDIRECT:
						final String location = httpURLConnection.getHeaderField("Location");
						url = new URL(url, location);
						continue;
					case HTTP_NOT_FOUND:
						return ERROR_RESOURCE_NOT_FOUND;
					case HTTP_UNAVAILABLE:
					case HTTP_INTERNAL_ERROR:
					case HTTP_NOT_IMPLEMENTED:
					case HTTP_BAD_GATEWAY:
						return ERROR_SERVICE;
					default:
						return ERROR_RESPONSE_STATUS;
				}
			}
			return ERROR_TOO_MANY_REDIRECTS;
		} finally {
			if (null != httpURLConnection) {
				httpURLConnection.disconnect();
			}
		}
	}

	private final void start(HttpURLConnection httpURLConnection) throws IOException {
		DownloadTask downloadTask = this.mDownloadTask;
		if (TextUtils.isEmpty(downloadTask.getContentDisposition())) {
			Runtime.getInstance().log(TAG, "response headers:" + httpURLConnection.getHeaderFields());
			downloadTask.setContentDisposition(httpURLConnection.getHeaderField("Content-Disposition"));
			String fileName = Runtime.getInstance().getFileNameByContentDisposition(downloadTask.getContentDisposition());
			Runtime.getInstance().log(TAG, " ContentDisposition file name:" + fileName + "  file:" + downloadTask.getFile().getName() + " getContentDisposition:" + downloadTask.getContentDisposition());
			if (!TextUtils.isEmpty(fileName) && !downloadTask.getFile().getName().equals(fileName)) {
				Runtime.getInstance().log(TAG, " new File(downloadTask.getFile().getParent(), fileName):" + new File(downloadTask.getFile().getParent(), fileName).getAbsolutePath());
				File renameTarget = new File(downloadTask.getFile().getParent(), fileName);
				if (renameTarget.exists()) {
					downloadTask.setFile(renameTarget);
					updateNotifierTitle();
				} else {
					boolean success = downloadTask.getFile().renameTo(renameTarget);
					if (success) {
						downloadTask.setFile(renameTarget);
						updateNotifierTitle();
					}
				}
				Runtime.getInstance().log(TAG, " rename:" + downloadTask.getFile().getAbsolutePath() + " exist:" + downloadTask.getFile().exists());
			}
		}
		if (TextUtils.isEmpty(downloadTask.getMimetype())) {
			downloadTask.setMimetype(httpURLConnection.getHeaderField("Content-Type"));
		}
		downloadTask.setContentLength(getHeaderFieldLong(httpURLConnection, "Content-Length"));
		onStart();
	}

	private void updateNotifierTitle() {
		DownloadTask downloadTask = this.mDownloadTask;
		if (null != mDownloadNotifier && null != downloadTask) {
			mDownloadNotifier.updateTitle(downloadTask);
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
							, downloadTask.clone());
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
		String mEtag = Runtime.getInstance().getStorageEngine(mDownloadTask.mContext).get(urlMD5, "-1");
		if (!TextUtils.isEmpty(mEtag) && !"-1".equals(mEtag)) {
			return mEtag;
		} else {
			return null;
		}
	}

	private HttpURLConnection createUrlConnection(URL url) throws IOException {
		DownloadTask downloadTask = this.mDownloadTask;
		HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
		httpURLConnection.setConnectTimeout(mConnectTimeOut);
		httpURLConnection.setInstanceFollowRedirects(false);
		httpURLConnection.setReadTimeout(downloadTask.getBlockMaxTime());
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
		if (null != downloadTask.getFile() && downloadTask.getFile().length() > 0) {
			String mEtag = "";
			if (!TextUtils.isEmpty((mEtag = getEtag()))) {
				Runtime.getInstance().log(TAG, "Etag:" + mEtag);
				httpURLConnection.setRequestProperty("If-Match", getEtag());
			}
			httpURLConnection.setRequestProperty("Range", "bytes=" + (mLastLoaded = downloadTask.getFile().length()) + "-");
		}
		httpURLConnection.setRequestProperty("Connection", "close");
		Runtime.getInstance().log(TAG, "settingHeaders");
	}


	@Override
	protected void onProgressUpdate(Integer... values) {
		DownloadTask downloadTask = this.mDownloadTask;
		try {
			long currentTime = SystemClock.elapsedRealtime();
//			Runtime.getInstance().log(TAG, " currentTime:" + currentTime);
			this.mUsedTime = currentTime - this.mBeginTime;
			if (mUsedTime == 0) {
				this.mAverageSpeed = 0;
			} else {
				this.mAverageSpeed = mLoaded * 1000 / this.mUsedTime;
			}
			if (null != mDownloadNotifier) {
				if (mTotals > 0) {
					int mProgress = (int) ((mLastLoaded + mLoaded) / Float.valueOf(mTotals) * 100);
					mDownloadNotifier.onDownloading(mProgress);
				} else {
					mDownloadNotifier.onDownloaded((mLastLoaded + mLoaded));
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

	@Override
	protected void onPostExecute(Integer integer) {
		DownloadTask downloadTask = this.mDownloadTask;
		try {
			if (null != downloadTask.getDownloadingListener()) {
				downloadTask
						.getDownloadingListener()
						.onProgress(downloadTask.getUrl(), (mLastLoaded + mLoaded), mTotals, mUsedTime);

			}
			if (integer == ERROR_USER_PAUSE) {
				downloadTask.setStatus(STATUS_PAUSED);
				downloadTask.pause();
				if (null != downloadTask.getDownloadListener()) {
					doCallback(integer);
				}
				if (null != mDownloadNotifier) {
					mDownloadNotifier.onDownloadPaused();
				}
				return;
			} else {
				downloadTask.completed();
			}
			Runtime.getInstance().log(TAG, "onPostExecute:" + DOWNLOAD_MESSAGE.get(integer));
			downloadTask.setStatus(DownloadTask.STATUS_COMPLETED);
			boolean isCancelDispose = doCallback(integer);
			// Error
			if (integer > SUCCESSFUL) {
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
			Intent mIntent = Runtime.getInstance().getCommonFileIntentCompat(downloadTask.getContext(), downloadTask);
			if (null == mIntent) {
				return;
			}
			if (!(downloadTask.getContext() instanceof Activity)) {
				mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			downloadTask.getContext().startActivity(mIntent);
		} catch (Throwable throwable) {
			if (Runtime.getInstance().isDebug()) {
				throwable.printStackTrace();
			}
		} finally {
			synchronized (Downloader.class) {
				ExecuteTasksMap.getInstance().removeTask(downloadTask.getUrl());
			}
			destroyTask();
		}
	}

	protected void destroyTask() {
		if (mIsCanceled.get() || mIsPaused.get()) {
			return;
		}
		DownloadTask downloadTask = mDownloadTask;
		if (null != downloadTask) {
			downloadTask.destroy();
		}
	}

	private boolean doCallback(Integer code) {
		DownloadListener mDownloadListener = null;
		DownloadTask downloadTask = this.mDownloadTask;
		if (null == (mDownloadListener = downloadTask.getDownloadListener())) {
			return false;
		}
		if (Runtime.getInstance().isDebug() && null != this.mThrowable) {
			this.mThrowable.printStackTrace();
		}
		return mDownloadListener.onResult(code <= SUCCESSFUL ? null
						: new DownloadException(code, "Download failed ， cause:" + DOWNLOAD_MESSAGE.get(code)), downloadTask.getFileUri(),
				downloadTask.getUrl(), mDownloadTask);
	}


	private void createNotifier() {
		DownloadTask downloadTask = this.mDownloadTask;
		Context mContext = downloadTask.getContext().getApplicationContext();
		Runtime.getInstance().log(TAG, " downloadTask.isEnableIndicator()):" + downloadTask.isEnableIndicator() + " file:" + downloadTask.getFile().getAbsolutePath());
		if (null != mContext && downloadTask.isEnableIndicator()) {
			mDownloadNotifier = new DownloadNotifier(mContext, downloadTask.getId());
			mDownloadNotifier.initBuilder(downloadTask);
		}
	}

	private int transferData(InputStream inputStream, RandomAccessFile randomAccessFile, boolean isSeek) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		BufferedInputStream bis = new BufferedInputStream(inputStream, BUFFER_SIZE);
		RandomAccessFile out = randomAccessFile;
		try {
			if (isSeek) {
				out.seek(out.length());
			} else {
				out.seek(0);
				mLastLoaded = 0L;
			}
			int bytes = 0;
			while (!mIsCanceled.get() && !mIsShutdown.get() && !mIsPaused.get()) {
				int n = bis.read(buffer, 0, BUFFER_SIZE);
				if (n == -1) {
					break;
				}
				out.write(buffer, 0, n);
				bytes += n;
				if ((SystemClock.elapsedRealtime() - this.mBeginTime) > mDownloadTimeOut) {
					return ERROR_TIME_OUT;
				}
			}
			if (mIsPaused.get()) {
				return ERROR_USER_PAUSE;
			}
			if (mIsCanceled.get()) {
				return ERROR_USER_CANCEL;
			}
			if (mIsShutdown.get()) {
				return ERROR_SHUTDOWN;
			}
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
		try {
			return mDownloadTask;
		} finally {
			mIsCanceled.set(true);
		}
	}

	@Override
	public int status() {
		DownloadTask downloadTask = mDownloadTask;
		return downloadTask == null ? DownloadTask.STATUS_NEW : downloadTask.getStatus();
	}


	@Override
	public boolean download(DownloadTask downloadTask) {
		return downloadInternal(downloadTask);
	}

	private final boolean downloadInternal(final DownloadTask downloadTask) {
		synchronized (Downloader.class) {
			if (ExecuteTasksMap.getInstance().exist(downloadTask.mUrl)) {
				return false;
			}
			ExecuteTasksMap.getInstance().addTask(downloadTask.getUrl(), this);
		}
		if (Looper.getMainLooper() != Looper.myLooper()) {
			HANDLER.post(new Runnable() {
				@Override
				public void run() {
					Downloader.this.run(downloadTask);
				}
			});
			return true;
		}
		run(downloadTask);
		return true;
	}

	private void run(DownloadTask downloadTask) {
		checkIsNullTask(downloadTask);
		try {
			this.mDownloadTask = downloadTask;
			this.mTotals = mDownloadTask.getTotalsLength();
			mDownloadTimeOut = mDownloadTask.getDownloadTimeOut();
			mConnectTimeOut = mDownloadTask.getConnectTimeOut();
			enableProgress = mDownloadTask.isEnableIndicator() || null != mDownloadTask.getDownloadingListener();
			Runtime.getInstance().log(TAG, " enableProgress:" + enableProgress);
			if (null != mDownloadTask.getDownloadingListener()) {
				try {
					Annotation annotation = mDownloadTask.getDownloadingListener().getClass().getDeclaredMethod("onProgress", String.class, long.class, long.class, long.class).getAnnotation(DownloadingListener.MainThread.class);
					mCallbackInMainThread = null != annotation;
					Runtime.getInstance().log(TAG, " callback in main-Thread:" + mCallbackInMainThread);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (downloadTask.getStatus() != STATUS_PAUSED) {
				downloadTask.resetTime();
			}
			downloadTask.setStatus(DownloadTask.STATUS_PENDDING);
			if (downloadTask.isParallelDownload()) {
				this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
			} else {
				this.executeOnExecutor(SERIAL_EXECUTOR);
			}
		} catch (Throwable throwable) {
			if (null != downloadTask && !TextUtils.isEmpty(downloadTask.getUrl())) {
				synchronized (Downloader.class) {
					ExecuteTasksMap.getInstance().removeTask(downloadTask.getUrl());
				}
			}
			throwable.printStackTrace();
			throw throwable;
		}
	}

	private final DownloadTask pause() {
		try {
			return mDownloadTask;
		} finally {
			mIsPaused.set(true);
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
			if (!enableProgress) {
				return;
			}
			long currentTime = SystemClock.elapsedRealtime();
			if (currentTime - mLastTime < 450) {
				return;
			}
			mLastTime = currentTime;
			if (mCallbackInMainThread) {
				publishProgress(0);
			} else {
				onProgressUpdate(0);
			}
		}
	}

}
