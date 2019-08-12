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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.download.library.Downloader.DOWNLOAD_MESSAGE;

/**
 * @author cenxiaozhong
 * @date 2018/5/13
 */
public class DownloadNotifier {

	private static final int FLAG = Notification.FLAG_INSISTENT;
	int requestCode = (int) SystemClock.uptimeMillis();
	private int mNotificationId;
	private NotificationManager mNotificationManager;
	private Notification mNotification;
	private NotificationCompat.Builder mBuilder;
	private Context mContext;
	private String mChannelId = "";
	private volatile boolean mAddedCancelAction = false;
	private static final String TAG = Runtime.PREFIX + DownloadNotifier.class.getSimpleName();
	private NotificationCompat.Action mAction;
	private DownloadTask mDownloadTask;
	private String mContent = "";
	private static long sLastUpdateNoticationTime = SystemClock.elapsedRealtime();
	private static final Handler NOTIFICATION_UPDATE_QUEUE = new Handler(Looper.getMainLooper());

	DownloadNotifier(Context context, int id) {
		this.mNotificationId = id;
		Runtime.getInstance().log(TAG, " DownloadNotifier:" + (mNotificationId));
		mContext = context;
		mNotificationManager = (NotificationManager) mContext
				.getSystemService(NOTIFICATION_SERVICE);
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				mBuilder = new NotificationCompat.Builder(mContext,
						mChannelId = mContext.getPackageName().concat(Runtime.getInstance().getVersion()));
				NotificationChannel mNotificationChannel = new NotificationChannel(mChannelId,
						Runtime.getInstance().getApplicationName(context),
						NotificationManager.IMPORTANCE_LOW);
				NotificationManager mNotificationManager = (NotificationManager) mContext
						.getSystemService(NOTIFICATION_SERVICE);
				mNotificationManager.createNotificationChannel(mNotificationChannel);
				mNotificationChannel.enableLights(false);
				mNotificationChannel.enableVibration(false);
				mNotificationChannel.setSound(null, null);
			} else {
				mBuilder = new NotificationCompat.Builder(mContext);
			}
		} catch (Throwable ignore) {
			if (Runtime.getInstance().isDebug()) {
				ignore.printStackTrace();
			}
		}
	}

	void initBuilder(DownloadTask downloadTask) {
		String title = getTitle(downloadTask);
		this.mDownloadTask = downloadTask;
		mBuilder.setContentIntent(PendingIntent.getActivity(mContext, 200, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT));
		mBuilder.setSmallIcon(mDownloadTask.getDownloadIcon());
		mBuilder.setTicker(mContext.getString(R.string.download_trickter));
		mBuilder.setContentTitle(title);
		mBuilder.setContentText(mContext.getString(R.string.download_coming_soon_download));
		mBuilder.setWhen(System.currentTimeMillis());
		mBuilder.setAutoCancel(true);
		mBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
		int defaults = 0;
		mBuilder.setDeleteIntent(buildCancelContent(mContext, downloadTask.getId(), downloadTask.getUrl()));
		mBuilder.setDefaults(defaults);
	}

	void updateTitle(DownloadTask downloadTask) {
		String title = getTitle(downloadTask);
		mBuilder.setContentTitle(title);
	}

	@NonNull
	private String getTitle(DownloadTask downloadTask) {
		String title = (null == downloadTask.getFile() || TextUtils.isEmpty(downloadTask.getFile().getName())) ?
				mContext.getString(R.string.download_file_download) :
				downloadTask.getFile().getName();

		if (title.length() > 20) {
			title = "..." + title.substring(title.length() - 20, title.length());
		}
		return title;
	}

	private PendingIntent buildCancelContent(Context context, int id, String url) {
		Intent intentCancel = new Intent(context, NotificationCancelReceiver.class);
		intentCancel.setAction(NotificationCancelReceiver.ACTION);
		intentCancel.putExtra("TAG", url);
		PendingIntent pendingIntentCancel = PendingIntent.getBroadcast(context, id * 1000, intentCancel, PendingIntent.FLAG_UPDATE_CURRENT);
		Runtime.getInstance().log(TAG, "buildCancelContent id:" + (id * 1000));
		return pendingIntentCancel;
	}

	private void setProgress(int maxprogress, int currentprogress, boolean exc) {
		mBuilder.setProgress(maxprogress, currentprogress, exc);
		sent();
	}


	private boolean hasDeleteContent() {
		return mBuilder.getNotification().deleteIntent != null;
	}

	private void setDelecte(PendingIntent intent) {
		mBuilder.getNotification().deleteIntent = intent;
	}

	/**
	 * 发送通知
	 */
	private void sent() {
		mNotification = mBuilder.build();
		mNotificationManager.notify(mNotificationId, mNotification);
	}

	void onPreDownload() {
		sent();
	}

	void onDownloading(int progress) {
		if (!this.hasDeleteContent()) {
			this.setDelecte(buildCancelContent(mContext, mNotificationId, mDownloadTask.mUrl));
		}
		if (!mAddedCancelAction) {
			mAddedCancelAction = true;
			mAction = new NotificationCompat.Action(android.R.color.transparent,
					mContext.getString(android.R.string.cancel),
					buildCancelContent(mContext, mNotificationId, mDownloadTask.mUrl));
			mBuilder.addAction(mAction);

		}
		mBuilder.setContentText(this.mContent = mContext.getString(R.string.download_current_downloading_progress, (progress + "%")));
		this.setProgress(100, progress, false);
		sent();
	}

	void onDownloaded(long loaded) {
		if (!this.hasDeleteContent()) {
			this.setDelecte(buildCancelContent(mContext, mNotificationId, mDownloadTask.mUrl));
		}
		if (!mAddedCancelAction) {
			mAddedCancelAction = true;
			mAction = new NotificationCompat.Action(mDownloadTask.getDownloadIcon(),
					mContext.getString(android.R.string.cancel),
					buildCancelContent(mContext, mNotificationId, mDownloadTask.mUrl));
			mBuilder.addAction(mAction);
		}
		mBuilder.setContentText(this.mContent = mContext.getString(R.string.download_current_downloaded_length, byte2FitMemorySize(loaded)));
		this.setProgress(100, 20, true);
		sent();
	}

	private long getDelayTime() {
		synchronized (DownloadNotifier.class) {
			long current = SystemClock.elapsedRealtime();
			if (current >= (sLastUpdateNoticationTime + 500)) {
				sLastUpdateNoticationTime = current;
				return 0;
			} else {
				long detal = 500 - (current - sLastUpdateNoticationTime);
				sLastUpdateNoticationTime += detal;
				return detal;
			}
		}
	}

	private static String byte2FitMemorySize(final long byteNum) {
		if (byteNum < 0) {
			return "shouldn't be less than zero!";
		} else if (byteNum < 1024) {
			return String.format(Locale.getDefault(), "%.1fB", (double) byteNum);
		} else if (byteNum < 1048576) {
			return String.format(Locale.getDefault(), "%.1fKB", (double) byteNum / 1024);
		} else if (byteNum < 1073741824) {
			return String.format(Locale.getDefault(), "%.1fMB", (double) byteNum / 1048576);
		} else {
			return String.format(Locale.getDefault(), "%.1fGB", (double) byteNum / 1073741824);
		}
	}

	void onDownloadPaused() {
		Runtime.getInstance().log(TAG, " onDownloadPaused:" + mDownloadTask.getUrl());
		if (!this.hasDeleteContent()) {
			this.setDelecte(buildCancelContent(mContext, mNotificationId, mDownloadTask.mUrl));
		}
		if (TextUtils.isEmpty(this.mContent)) {
			this.mContent = "";
		}
		mBuilder.setContentText(this.mContent.concat("(").concat(mContext.getString(R.string.download_paused)).concat(")"));
		mBuilder.setSmallIcon(mDownloadTask.getDownloadDoneIcon());
		removeCancelAction();
		mAddedCancelAction = false;
		NOTIFICATION_UPDATE_QUEUE.postDelayed(new Runnable() {
			@Override
			public void run() {
				sent();
			}
		}, getDelayTime());
	}

	void onDownloadFinished() {
		removeCancelAction();
		Intent mIntent = Runtime.getInstance().getCommonFileIntentCompat(mContext, mDownloadTask);
		setDelecte(null);
		if (null != mIntent) {
			if (!(mContext instanceof Activity)) {
				mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			PendingIntent rightPendIntent = PendingIntent
					.getActivity(mContext,
							mNotificationId * 10000, mIntent,
							PendingIntent.FLAG_UPDATE_CURRENT);
			mBuilder.setSmallIcon(mDownloadTask.getDownloadDoneIcon());
			mBuilder.setContentText(mContext.getString(R.string.download_click_open));
			mBuilder.setProgress(100, 100, false);
			mBuilder.setContentIntent(rightPendIntent);
			NOTIFICATION_UPDATE_QUEUE.postDelayed(new Runnable() {
				@Override
				public void run() {
					sent();
				}
			}, getDelayTime());
		}
	}

	private void removeCancelAction() {
		try {
			/**
			 * 用反射获取 mActions 该 Field , mBuilder.mActions 防止迭代该Field域访问不到，或者该Field
			 * 改名导致程序崩溃。
			 */
			Class<? extends NotificationCompat.Builder> clazz = mBuilder.getClass();
			Field mField = clazz.getDeclaredField("mActions");
			ArrayList<NotificationCompat.Action> mActions = null;
			if (null != mField) {
				mActions = (ArrayList<NotificationCompat.Action>) mField.get(mBuilder);
			}
			int index = -1;
			if (null != mActions && (index = mActions.indexOf(mAction)) != -1) {
				mActions.remove(index);
			}

		} catch (Throwable ignore) {
			if (Runtime.getInstance().isDebug()) {
				ignore.printStackTrace();
			}
		}
	}

	/**
	 * 根据id清除通知
	 */
	void cancel() {
		mNotificationManager.cancel(mNotificationId);
	}

	static void cancel(DownloadTask downloadTask) {
		NotificationManager notificationManager = (NotificationManager) downloadTask.getContext()
				.getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(downloadTask.mId);
		if (null != downloadTask.getDownloadListener()) {
			downloadTask.getDownloadListener().onResult(new DownloadException(Downloader.ERROR_USER_CANCEL, DOWNLOAD_MESSAGE.get(Downloader.ERROR_USER_CANCEL)), downloadTask.getFileUri(), downloadTask.getUrl(), downloadTask);
		}
	}
}
