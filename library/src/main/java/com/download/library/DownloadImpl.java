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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author cenxiaozhong
 * @date 2019/2/9
 * @since 1.0.0
 */
public final class DownloadImpl {

	private static final DownloadImpl sInstance = new DownloadImpl();
	private final ConcurrentHashMap<String, DownloadTask> mTasks = new ConcurrentHashMap<>();
	private static Context mContext;
	public static final String TAG = DownloadImpl.class.getSimpleName();

	private DownloadImpl() {
	}

	public static DownloadImpl getInstance() {
		return sInstance;
	}

	public ResourceRequest with(@NonNull Context context) {
		if (null != context) {
			mContext = context.getApplicationContext();
		}
		return ResourceRequest.with(mContext);
	}

	public ResourceRequest with(@NonNull String url) {
		if (null == mContext) {
			throw new NullPointerException("Context can't be null . ");
		}
		return ResourceRequest.with(mContext).url(url);
	}

	public ResourceRequest with(@NonNull Context context, @NonNull String url) {
		if (null != context) {
			mContext = context.getApplicationContext();
		}
		return ResourceRequest.with(mContext).url(url);
	}

	private void safe(@NonNull DownloadTask downloadTask) {
		if (null == downloadTask.getContext()) {
			throw new NullPointerException("context can't be null .");
		}
		if (TextUtils.isEmpty(downloadTask.getUrl())) {
			throw new NullPointerException("url can't be empty .");
		}
	}

	public boolean enqueue(@NonNull DownloadTask downloadTask) {
		safe(downloadTask);
		return new Downloader().download(downloadTask);
	}

	public File call(@NonNull DownloadTask downloadTask) {
		safe(downloadTask);
		Callable<File> callable = new SyncDownloader(downloadTask);
		try {
			return callable.call();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public File callEx(@NonNull DownloadTask downloadTask) throws Exception {
		safe(downloadTask);
		Callable<File> callable = new SyncDownloader(downloadTask);
		return callable.call();
	}

	public DownloadTask cancel(@NonNull String url) {
		DownloadTask downloadTask = null;
		try {
			downloadTask = ExecuteTasksMap.getInstance().cancelTask(url);
		} finally {
			DownloadTask task = mTasks.get(url);
			if (task != null && task.getStatus() == DownloadTask.STATUS_PAUSED) {
				task.setStatus(DownloadTask.STATUS_CANCELED);
				DownloadNotifier.cancel(task);
				downloadTask = task;
			}
			remove(url);
		}
		return downloadTask;
	}

	public List<DownloadTask> cancelAll() {
		List<DownloadTask> downloadTasks = new ArrayList<>();
		try {
			List<DownloadTask> runningTask = ExecuteTasksMap.getInstance().cancelTasks();
			if (null != runningTask) {
				downloadTasks.addAll(runningTask);
			}
		} finally {
			ConcurrentHashMap<String, DownloadTask> tasks = this.mTasks;
			if (!tasks.isEmpty()) {
				Set<Map.Entry<String, DownloadTask>> sets = tasks.entrySet();
				for (Map.Entry<String, DownloadTask> entry : sets) {
					DownloadTask downloadTask = entry.getValue();
					if (downloadTask != null && downloadTask.getStatus() == DownloadTask.STATUS_PAUSED) {
						downloadTask.setStatus(DownloadTask.STATUS_CANCELED);
						DownloadNotifier.cancel(downloadTask);
						downloadTasks.add(downloadTask);
					}
				}
			}
			cleanTasksCache();
		}
		return downloadTasks;
	}

	public DownloadTask pause(@NonNull String url) {
		DownloadTask downloadTask = ExecuteTasksMap.getInstance().pauseTask(url);
		if (downloadTask != null) {
			mTasks.put(downloadTask.getUrl(), downloadTask);
		}
		return downloadTask;
	}

	public void resumeAll() {
		ConcurrentHashMap<String, DownloadTask> tasks = this.mTasks;
		if (tasks.size() <= 0) {
			return;
		}
		Set<Map.Entry<String, DownloadTask>> sets = tasks.entrySet();
		if (sets != null && sets.size() > 0) {
			for (Map.Entry<String, DownloadTask> entry : sets) {
				DownloadTask downloadTask = entry.getValue();
				if (null == downloadTask || null == downloadTask.getContext() || TextUtils.isEmpty(downloadTask.getUrl())) {
					Runtime.getInstance().logError(TAG, "downloadTask death .");
					continue;
				}
				enqueue(downloadTask);
			}
		}
		cleanTasksCache();
	}

	public boolean resume(@NonNull String url) {
		DownloadTask downloadTask = mTasks.remove(url);
		if (null == downloadTask || null == downloadTask.getContext() || TextUtils.isEmpty(downloadTask.getUrl())) {
			Runtime.getInstance().logError(TAG, "downloadTask death .");
			return false;
		}
		enqueue(downloadTask);
		return true;
	}

	private void cleanTasksCache() {
		ConcurrentHashMap<String, DownloadTask> tasks = this.mTasks;
		tasks.clear();
	}

	private void remove(@NonNull String url) {
		ConcurrentHashMap<String, DownloadTask> tasks = this.mTasks;
		tasks.remove(url);
	}

	public boolean exist(@NonNull String url) {
		return ExecuteTasksMap.getInstance().exist(url) || mTasks.contains(url);
	}

	public boolean isPaused(@NonNull String url) {
		DownloadTask downloadTask = mTasks.get(url);
		return downloadTask != null && downloadTask.getStatus() == DownloadTask.STATUS_PAUSED;
	}

	public int pausedTasksTotals() {
		return mTasks.size();
	}

	public boolean isRunning(@NonNull String url) {
		return ExecuteTasksMap.getInstance().exist(url);
	}
}
