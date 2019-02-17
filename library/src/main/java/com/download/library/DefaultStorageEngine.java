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
import android.content.SharedPreferences;

/**
 * @author cenxiaozhong
 * @date 2019/2/16
 * @since 1.0.0
 */
public class DefaultStorageEngine implements StorageEngine {
	Context mContext;

	DefaultStorageEngine(Context context) {
		this.mContext = context;
	}

	@Override
	public void save(String key, String value) {
		SharedPreferences mSharedPreferences = mContext.getSharedPreferences(Runtime.getInstance().getIdentify(), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.putString(key, value);
		editor.apply();
	}

	@Override
	public String get(String key, String defaultValue) {
		SharedPreferences mSharedPreferences = mContext.getSharedPreferences(Runtime.getInstance().getIdentify(), Context.MODE_PRIVATE);
		String value = mSharedPreferences.getString(key, defaultValue);
		return value;
	}

	public static class DefaultStorageEngineFactory implements StorageEngine.StorageEngineFactory {

		@Override
		public StorageEngine newStoraEngine(Context context) {
			return new DefaultStorageEngine(context);
		}
	}
}
