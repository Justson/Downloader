## Downloader
Downloader 是一个非常轻巧以及功能强大快速下载库，只有50KB 左右大小，对于大多数应用来说，性价比最高的一个下载库， 相比系统提供DownloadManager、FileDownloader 来说，Downloader 提供的API更简单、轻量且Downloader提供同步下载，系统通知进度等功能。

## 特性

* 支持串行，多线程并行下载
* 支持断点续传
* 支持分块传输
* 支持系统通知进度
* 支持同步,异步下载
* 支持自义定路径
* 支持添加请求头
* 支持超时配置
* 提供简易的Api
* 支持重定向下载
* 支持进度回调


## 例子

```java
File file = DownloadImpl.getInstance()
				.with(getApplicationContext())
				.url("http://shouji.360tpcdn.com/170918/93d1695d87df5a0c0002058afc0361f1/com.ss.android.article.news_636.apk")
				.get();
```

```java
DownloadImpl.getInstance()
                .with(getApplicationContext())
                .url("http://shouji.360tpcdn.com/170918/f7aa8587561e4031553316ada312ab38/com.tencent.qqlive_13049.apk")
                .enqueue();
```



