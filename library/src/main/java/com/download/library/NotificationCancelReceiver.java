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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

/**
 * @author cenxiaozhong
 * @date 2018/2/12
 */
public class NotificationCancelReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.download.cancelled";
    private static final String TAG = Runtime.PREFIX + NotificationCancelReceiver.class.getSimpleName();

    public NotificationCancelReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Runtime.getInstance().log(TAG, "action:" + action);
        if (Runtime.getInstance().append(context, ACTION).equals(action)) {
            try {
                String url = intent.getStringExtra("TAG");
                if (!TextUtils.isEmpty(url)) {
                    DownloadImpl.getInstance(context).cancel(url);
                } else {
                    Runtime.getInstance().logError(action, " error url empty");
                }
            } catch (Throwable ignore) {
                if (Runtime.getInstance().isDebug()) {
                    ignore.printStackTrace();
                }
            }

        }
    }
}