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

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;


/**
 * @author cenxiaozhong
 * @date 2018/2/12
 */
public final class ExecuteTasksMap {
	
	private ConcurrentHashMap<String, ExecuteTask> mTasks;

	private ExecuteTasksMap() {
		mTasks = new ConcurrentHashMap<>();
	}

	static ExecuteTasksMap getInstance() {
		return ExecuteTaskHolder.INSTANCE;
	}

	DownloadTask cancelTask(String url) {
		ExecuteTask mExecuteTask = mTasks.get(url);
		if (null != mExecuteTask) {
			return mExecuteTask.cancelDownload();
		}
		return null;
	}

	DownloadTask pauseTask(String url) {
		ExecuteTask mExecuteTask = mTasks.get(url);
		if (null != mExecuteTask) {
			DownloadTask downloadTask = mExecuteTask.getDownloadTask();
			if (null != downloadTask && downloadTask.getStatus() == DownloadTask.STATUS_DOWNLOADING) {
				return mExecuteTask.pauseDownload();
			}
		}
		return null;
	}

	List<DownloadTask> cancelTasks() {
		Set<Map.Entry<String, ExecuteTask>> sets = mTasks.entrySet();
		if (sets.size() > 0) {
			ArrayList<DownloadTask> downloadTasks = new ArrayList<>(sets.size());
			for (Map.Entry<String, ExecuteTask> entry : sets) {
				DownloadTask downloadTask = entry.getValue().cancelDownload();
				if (null != downloadTask) {
					downloadTasks.add(downloadTask);
				}
			}
			return downloadTasks;
		}
		return null;
	}

	List<DownloadTask> pauseTasks() {
		Set<Map.Entry<String, ExecuteTask>> sets = mTasks.entrySet();
		if (sets.size() > 0) {
			ArrayList<DownloadTask> downloadTasks = new ArrayList<>(sets.size());
			for (Map.Entry<String, ExecuteTask> entry : sets) {
				DownloadTask downloadTask = entry.getValue().pauseDownload();
				if (null != downloadTask) {
					downloadTasks.add(downloadTask);
				}
			}
			return downloadTasks;
		}
		return null;
	}

	void addTask(@NonNull String url, @NonNull ExecuteTask recipient) {
		if (null != url && null != recipient) {
			mTasks.put(url, recipient);
		}
	}

	void removeTask(@NonNull String url) {
		if (null != url) {
			this.mTasks.remove(url);
		}
	}

	boolean exist(@NonNull String url) {
		return !TextUtils.isEmpty(url) && null != mTasks.get(url);
	}

	private static class ExecuteTaskHolder {
		private static final ExecuteTasksMap INSTANCE = new ExecuteTasksMap();
	}
}
