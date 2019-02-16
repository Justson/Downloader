package com.download.library;

import android.content.Context;

/**
 * @author cenxiaozhong
 * @date 2019/2/16
 * @since 1.0.0
 */
public interface StorageEngine {
	void save(String key, String value);

	String get(String key, String defaultValue);


	public interface StorageEngineFactory {
		StorageEngine newStoraEngine(Context context);
	}
}
