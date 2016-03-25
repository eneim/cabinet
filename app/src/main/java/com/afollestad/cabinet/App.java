package com.afollestad.cabinet;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.glide.APKIconLoader;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.StorageUtils;
import com.afollestad.cabinet.utils.VectorDrawableMap;
import com.bumptech.glide.Glide;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * @author Aidan Follestad (afollestad)
 */
public class App extends Application {

    private static int mRootAvailable = -1;
    private static boolean mRootShellOpen;
    private static StorageUtils.SDCard mSdCard;
    private static VectorDrawableMap mVectorMap;
    private RefWatcher mRefWatcher;

    public static RefWatcher getRefWatcher(Context context) {
        App application = (App) context.getApplicationContext();
        return application.mRefWatcher;
    }

    /**
     * Execute on BackgroundThread only.
     */
    @NonNull
    public synchronized static List<String> runCommand(boolean su, final String... commands) throws RootDeniedException, IOException, TimeoutException {
        BackgroundThread.lock();
        for (String command : commands) {
            Log.e("cabinet-root-commands", "Attempt SU: " + su + " \t" + command);
        }
        try {
            RootTools.debugMode = BuildConfig.DEBUG;
            if (su && !isRootAvailable()) {
                throw new RootDeniedException("Root Access Denied");
            }

            final List<String> output = new ArrayList<>();
            Command command = new Command(0, false, commands) {
                @Override
                public void commandOutput(int id, String line) {
                    output.add(line);
                    super.commandOutput(id, line);
                }

                @Override
                public void commandTerminated(int id, String reason) {
                    super.commandTerminated(id, reason);
                    output.clear();
                }
            };
            su = mRootShellOpen || su;
            Shell shell = RootTools.getShell(su);
            if (shell.isClosed) return new ArrayList<>();
            shell.add(command);
            commandWait(shell, command);
            if (su) {
                mRootShellOpen = true;
                RootTools.closeShell(false);
            }
            return output;
        } finally {
            BackgroundThread.removeLock();
        }
    }

    private synchronized static void commandWait(Shell shell, final Command cmd) {
        while (!cmd.isFinished()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (cmd) {
                try {
                    if (!cmd.isFinished()) {
                        if (BackgroundThread.stopUntilNotLocked) {
                            cmd.terminate();
                        }
                        cmd.wait(50);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!cmd.isExecuting() && !cmd.isFinished()) {
                if (!shell.isExecuting && !shell.isReading) {
                    Exception e = new Exception();
                    e.setStackTrace(Thread.currentThread().getStackTrace());
                    e.printStackTrace();
                } else if (shell.isExecuting && !shell.isReading) {
                    Exception e = new Exception();
                    e.setStackTrace(Thread.currentThread().getStackTrace());
                    e.printStackTrace();
                } else {
                    Exception e = new Exception();
                    e.setStackTrace(Thread.currentThread().getStackTrace());
                    e.printStackTrace();
                }
            }
        }
    }

    public static StorageUtils.SDCard getSDCard() {
        return getSDCard(false);
    }

    public static StorageUtils.SDCard getSDCard(boolean forceRefresh) {
        if (mSdCard == null || forceRefresh)
            mSdCard = StorageUtils.getSDCard();
        return mSdCard;
    }

    public static VectorDrawableMap getVectorMap() {
        if (mVectorMap == null)
            mVectorMap = new VectorDrawableMap();
        return mVectorMap;
    }

    public static boolean isRootAvailable() {
        //TODO: Re-add "requesting root" dialog
        if (mRootAvailable == -1)
            mRootAvailable = (RootTools.isAccessGiven()) ? 1 : 0;
        return mRootAvailable == 1;
    }

    public static void invalidateRootAvailability() {
        mRootAvailable = -1;
    }

    public static void wipeDirectory(java.io.File dir) {
        java.io.File[] cache = dir.listFiles();
        if (cache != null) {
            for (java.io.File fi : cache) {
                if (fi.isDirectory()) {
                    wipeDirectory(fi);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    fi.delete();
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Glide.get(this).register(LocalFile.class, InputStream.class, new APKIconLoader.Loader());
        mRefWatcher = LeakCanary.install(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        try {
            RootTools.closeAllShells();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mVectorMap != null) {
            mVectorMap.clean();
            mVectorMap = null;
        }

        wipeDirectory(getCacheDir());
        wipeDirectory(getExternalCacheDir());
    }
}