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
		SharedPreferences mSharedPreferences = mContext.getSharedPreferences(Rumtime.getInstance().getIdentify(), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.putString(key, value);
		editor.apply();
	}

	@Override
	public String get(String key, String defaultValue) {
		SharedPreferences mSharedPreferences = mContext.getSharedPreferences(Rumtime.getInstance().getIdentify(), Context.MODE_PRIVATE);
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
