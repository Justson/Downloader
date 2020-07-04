package com.download.library;

import java.io.File;

/**
 * @author cenxiaozhong
 * @date 2020/7/4
 * @since 1.0.0
 */
public interface DownloadSubmitter {

    /**
     * submit the download task
     *
     * @param downloadTask
     */
    boolean submit(DownloadTask downloadTask);


    File submit0(DownloadTask downloadTask) throws Exception;

}
