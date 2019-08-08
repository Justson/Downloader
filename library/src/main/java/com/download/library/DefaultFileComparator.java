package com.download.library;

import java.io.File;

/**
 * @author cenxiaozhong
 * @date 2019-08-09
 * @since 1.0.0
 */
public class DefaultFileComparator implements FileComparator {

	@Override
	public int compare(String url, File originFile, String inputMD5, String originFileMD5) {
		if (inputMD5 == null) {
			inputMD5 = "";
		}
		if (inputMD5.trim().equalsIgnoreCase(originFileMD5)) {
			return FileComparator.COMPARE_RESULT_SUCCESSFUL;
		} else {
			return FileComparator.COMPARE_RESULT_REDOWNLOAD_RENAME;
		}
	}

	static class DefaultFileComparatorFactory implements FileComparatorFactory {
		@Override
		public FileComparator newFileComparator() {
			return new DefaultFileComparator();
		}
	}
}
