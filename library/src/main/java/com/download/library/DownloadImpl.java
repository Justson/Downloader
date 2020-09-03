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
import android.content.IntentFilter;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;

/**
 * @author cenxiaozhong
 * @date 2019/2/9
 * @since 1.0.0
 */
public final class DownloadImpl {

    private static volatile DownloadImpl sInstance;
    private final ConcurrentHashMap<String, DownloadTask> mTasks = new ConcurrentHashMap<>();
    private volatile static Context mContext;
    public static final String TAG = Runtime.PREFIX + DownloadImpl.class.getSimpleName();

    private DownloadImpl(@NonNull Context context) {
        Context current = mContext;
        if (current == null) {
            synchronized (DownloadImpl.class) {
                if (mContext == null) {
                    current = mContext = context.getApplicationContext();
                    String action = Runtime.getInstance().append(context, NotificationCancelReceiver.ACTION);
                    current.registerReceiver(new NotificationCancelReceiver(), new IntentFilter(action));
                    Runtime.getInstance().log(TAG, "registerReceiver:" + action);

                }
            }
        }
    }

    public static DownloadImpl getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (DownloadImpl.class) {
                if (sInstance == null) {
                    sInstance = new DownloadImpl(context);
                }
            }
        }
        return sInstance;
    }

    public static ResourceRequest with(@NonNull Context context) {
        return getInstance(context).with0(context);
    }

    public ResourceRequest with(@NonNull String url) {
        return ResourceRequest.with(mContext).url(url);
    }

    public ResourceRequest url(@NonNull String url) {
        return ResourceRequest.with(mContext).url(url);
    }

    private ResourceRequest with0(@NonNull Context context) {
        return ResourceRequest.with(mContext);
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
        return DownloadSubmitterImpl.getInstance().submit(downloadTask);
    }

    public File call(@NonNull DownloadTask downloadTask) {
        safe(downloadTask);
        try {
            File file = DownloadSubmitterImpl.getInstance().submit0(downloadTask);
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public File callEx(@NonNull DownloadTask downloadTask) throws Exception {
        safe(downloadTask);
        File file = DownloadSubmitterImpl.getInstance().submit0(downloadTask);
        return file;
    }

    public synchronized DownloadTask cancel(@NonNull String url) {
        DownloadTask downloadTask = null;
        try {
            downloadTask = ExecuteTasksMap.getInstance().cancelTask(url);
        } finally {
            DownloadTask task = mTasks.get(url);
            if (task != null && task.getStatus() == DownloadTask.STATUS_PAUSED) {
                task.cancel();
                DownloadNotifier.cancel(task);
                downloadTask = task;
            }
            remove(url);
        }
        return downloadTask;
    }

    public synchronized List<DownloadTask> cancelAll() {
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
                        downloadTask.cancel();
                        DownloadNotifier.cancel(downloadTask);
                        downloadTasks.add(downloadTask);
                    }
                }
            }
            cleanTasksCache();
        }
        return downloadTasks;
    }

    public synchronized DownloadTask pause(@NonNull String url) {
        DownloadTask downloadTask = ExecuteTasksMap.getInstance().pauseTask(url);
        if (downloadTask != null) {
            mTasks.put(downloadTask.getUrl(), downloadTask);
        }
        return downloadTask;
    }

    public synchronized void resumeAll() {
        ConcurrentHashMap<String, DownloadTask> tasks = this.mTasks;
        if (tasks.size() <= 0) {
            return;
        }
        Set<Map.Entry<String, DownloadTask>> sets = tasks.entrySet();
        if (sets.size() > 0) {
            for (Map.Entry<String, DownloadTask> entry : sets) {
                DownloadTask downloadTask = entry.getValue();
                if (null == downloadTask || null == downloadTask.getContext() || TextUtils.isEmpty(downloadTask.getUrl())) {
                    Runtime.getInstance().logError(TAG, "downloadTask death .");
                    continue;
                }
                Runtime.getInstance().logError(TAG, "downloadTask:" + downloadTask.getUrl());
                enqueue(downloadTask);
            }
        }
        cleanTasksCache();

    }

    public synchronized boolean resume(@NonNull String url) {
        DownloadTask downloadTask = mTasks.remove(url);
        if (null == downloadTask || null == downloadTask.getContext() || TextUtils.isEmpty(downloadTask.getUrl())) {
            Runtime.getInstance().logError(TAG, "downloadTask death .");
            return false;
        }
        enqueue(downloadTask);
        return true;

    }

    private synchronized void cleanTasksCache() {
        ConcurrentHashMap<String, DownloadTask> tasks = this.mTasks;
        tasks.clear();
    }

    private synchronized void remove(@NonNull String url) {
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
