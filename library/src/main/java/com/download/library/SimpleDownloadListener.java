package com.download.library;

import android.net.Uri;

/**
 * @author cenxiaozhong
 * @date 2019/2/16
 * @since 1.0.0
 */
public class SimpleDownloadListener implements DownloadListener {
	@Override
	public void onStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength, Extra extra) {

	}
	@Override
	public boolean onResult(Throwable throwable, Uri path, String url, Extra extra) {
		return false;
	}
}
