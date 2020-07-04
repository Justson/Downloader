package com.download.library.queue;

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

/**
 * @author cenxiaozhong
 * @date 2019/2/15
 * @since 1.0.0
 */
public class SameThreadExchanger<V> extends Exchanger<V> {

	private V v;

	public SameThreadExchanger() {
	}

	void setV(V v) {
		this.v = v;
	}

	@Override
	public V exchange(V x, long timeout, TimeUnit unit) {
		return exchange(v);
	}

	@Override
	public V exchange(V x) {
		try {
			V v = this.v;
			return v;
		} finally {
			this.v = null;
		}
	}
}
