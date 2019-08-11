package com.download.library;

import java.io.File;

/**
 * @author cenxiaozhong
 * @date 2019-08-09
 * @since 1.0.0
 */
public interface FileComparator {

	int COMPARE_RESULT_SUCCESSFUL = 1;
	int COMPARE_RESULT_REDOWNLOAD_COVER = 2;
	int COMPARE_RESULT_REDOWNLOAD_RENAME = 3;

	int compare(String url, File originFile, String inputMD5, String originFileMD5);

	interface FileComparatorFactory {
		FileComparator newFileComparator();
	}
}
