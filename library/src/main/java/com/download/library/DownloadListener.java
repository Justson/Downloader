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

import android.net.Uri;

import androidx.annotation.MainThread;

/**
 * @author cenxiaozhong
 * @date 2018/6/21
 * @update 4.0.0
 * @since 1.0.0
 */
public interface DownloadListener {


	/**
	 * @param url                下载链接
	 * @param userAgent          UserAgent
	 * @param contentDisposition ContentDisposition
	 * @param mimetype           资源的媒体类型
	 * @param contentLength      文件长度
	 * @param extra              下载配置
	 */
	@MainThread
	void onStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength, Extra extra);

	/**
	 * @param throwable 如果异常，返回给异常
	 * @param path      文件的绝对路径
	 * @param url       下载的地址
	 * @return true     处理了下载完成后续的事件 ，false 默认交给Downloader 处理
	 */
	@MainThread
	boolean onResult(Throwable throwable, Uri path, String url, Extra extra);


}
