package com.download.library.queue;

import android.os.Looper;

/**
 * @author cenxiaozhong
 * @date 2020/7/5
 * @since 1.0.0
 */
public final class GlobalQueue {
    private static volatile Dispatch mMainQueue = null;

    public static Dispatch getMainQueue() {
        if (mMainQueue == null) {
            synchronized (GlobalQueue.class) {
                if (mMainQueue == null) {
                    mMainQueue = new Dispatch(Looper.getMainLooper());
                }
            }
        }
        return mMainQueue;
    }
}
