package com.download.library;

import android.os.AsyncTask;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

/**
 * @author xiaozhongcen
 * @date 20-7-9
 * @since 1.0.0
 */
public final class Executors {

    private volatile static Executor IO;
    private volatile static Executor TASK_ENQUEUE_DISPATCH;
    private volatile static Executor TASK_QUEUEDUP_DISPATCH;
    private static final String TAG = Executors.class.getSimpleName();
    protected static final Executor SERIAL_EXECUTOR = new SerialExecutor();

    public static Executor io() {
        if (IO != null) {
            return IO;
        }
        synchronized (Executors.class) {
            if (IO == null) {
                ThreadPoolExecutor service = new ThreadPoolExecutor(4, 4, 30L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        return new Thread(r);
                    }
                });
                service.allowCoreThreadTimeOut(true);
                IO = service;
            }

        }
        return IO;
    }

    public static Executor getSerialExecutor() {
        return SERIAL_EXECUTOR;
    }

    public static Executor taskEnqueueDispatchExecutor() {
        if (TASK_ENQUEUE_DISPATCH != null) {
            return TASK_ENQUEUE_DISPATCH;
        }
        synchronized (Executors.class) {
            if (TASK_ENQUEUE_DISPATCH == null) {
                ThreadPoolExecutor service = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        return new Thread(r);
                    }
                });
                service.allowCoreThreadTimeOut(true);
                TASK_ENQUEUE_DISPATCH = service;
            }

        }
        return TASK_ENQUEUE_DISPATCH;
    }


    public static Executor taskQueuedUpDispatchExecutor() {
        if (TASK_QUEUEDUP_DISPATCH != null) {
            return TASK_QUEUEDUP_DISPATCH;
        }
        synchronized (Executors.class) {
            if (TASK_QUEUEDUP_DISPATCH == null) {
                ThreadPoolExecutor service = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        return new Thread(r);
                    }
                });
                service.allowCoreThreadTimeOut(true);
                TASK_QUEUEDUP_DISPATCH = service;
            }

        }
        return TASK_QUEUEDUP_DISPATCH;
    }

    public static void setTaskEnqueueDispatchExecutor(Executor executor) {
        if (executor == null) {
            Runtime.getInstance().logError(TAG, "executor is null");
            return;
        }
        synchronized (Executors.class) {
            Executor taskEnqueueDispatch = TASK_ENQUEUE_DISPATCH;
            try {
                TASK_ENQUEUE_DISPATCH = executor;
            } finally {
                if (null != taskEnqueueDispatch) {
                    if (taskEnqueueDispatch instanceof ExecutorService) {
                        ((ExecutorService) taskEnqueueDispatch).shutdown();
                    }
                }
            }
        }
    }

    public static void setTaskQueuedupDispatchExecutor(Executor executor) {
        if (executor == null) {
            Runtime.getInstance().logError(TAG, "executor is null");
            return;
        }
        synchronized (Executors.class) {
            Executor queuedupDispatch = TASK_QUEUEDUP_DISPATCH;
            try {
                TASK_QUEUEDUP_DISPATCH = executor;
            } finally {
                if (null != queuedupDispatch) {
                    if (queuedupDispatch instanceof ExecutorService) {
                        ((ExecutorService) queuedupDispatch).shutdown();
                    }
                }
            }
        }
    }

    public static void setIO(Executor executor) {
        if (executor == null) {
            Runtime.getInstance().logError(TAG, "executor is null");
            return;
        }
        synchronized (Executors.class) {
            Executor io = IO;
            try {
                IO = executor;
            } finally {
                if (null != io) {
                    if (io != AsyncTask.THREAD_POOL_EXECUTOR) {
                        if (io instanceof ExecutorService) {
                            ((ExecutorService) io).shutdown();
                        }
                    }
                }
            }
        }
    }
}
