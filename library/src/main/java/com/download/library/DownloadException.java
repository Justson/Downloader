package com.download.library;

/**
 * @author cenxiaozhong
 * @date 2019/2/17
 * @since 1.0.0
 */
public class DownloadException extends RuntimeException {
	private int code;
	private String msg;

	public DownloadException(int code, String msg) {
		super(msg);
		this.code = code;
		this.msg = msg;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}
}
