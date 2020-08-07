package com.download.library;

import android.os.Looper;

import com.queue.library.DispatchThread;

/**
 * @author cenxiaozhong
 * @date 2020/7/5
 * @since 1.0.0
 */
public class AsyncTask {


    private static final DispatchThread MAIN_QUEUE = new DispatchThread(Looper.getMainLooper());

    protected void publishProgress(final Integer... values) {
        MAIN_QUEUE.post(new Runnable() {
            @Override
            public void run() {
                onProgressUpdate(values);
            }
        });
    }


    protected void onProgressUpdate(Integer... values) {

    }

}
