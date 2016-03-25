package com.afollestad.cabinet.utils;

import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;

/**
 * Background HandlerThread
 */
public class BackgroundThread {
    private static HandlerThread thread;
    private static Handler handler;

    private static boolean locked;
    public static boolean stopUntilNotLocked;
    private static CountDownLatch latch;

    public static void lock() {
        locked = true;
    }

    public static void removeLock() {
        locked = false;
        if (latch != null) {
            latch.countDown();
        }
    }

    public synchronized static Handler getHandler() {
        if (thread == null || !thread.isAlive() || handler == null) {
            thread = new HandlerThread("Background");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        if (locked && stopUntilNotLocked) {
            latch = new CountDownLatch(1);
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        stopUntilNotLocked = false;
        return handler;
    }

    public static void reset() {
        stopUntilNotLocked = true;
        if (handler != null)
            handler.removeCallbacksAndMessages(null);
    }
}
