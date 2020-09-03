package com.download.library;

/**
 * @author cenxiaozhong
 * @date 2020/7/5
 * @since 1.0.0
 */
public interface DownloadStatusListener {

    /**
     * status 改变回调
     * @param extra
     * @param status
     */
    void onDownloadStatusChanged(Extra extra,@DownloadTask.DownloadTaskStatus int status);

}
