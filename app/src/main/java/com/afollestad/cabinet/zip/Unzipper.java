package com.afollestad.cabinet.zip;

import android.os.Handler;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cram.Cram;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.List;

public class Unzipper {

    private static MaterialDialog mDialog;
    private static Handler mHandler;

    private static void unzip(final PluginActivity context, final File destinationFolder, final File zipFile) throws Exception {
        updateDialog(zipFile);
        Cram.with(context)
                .extractor(zipFile)
                .to(destinationFolder)
                .complete(context);
    }

    private static void updateDialog(final File currentFile) {
        if (mDialog == null) return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDialog.setContent(currentFile.getName());
            }
        });
    }

    private static void dismissProgressDialog() {
        if (mDialog == null) return;
        try {
            mDialog.dismiss();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public static void unzip(final PluginActivity context, final File destinationFolder, final List<File> files, final Zipper.ZipCallback callback) {
        mDialog = new MaterialDialog.Builder(context)
                .content(R.string.extracting)
                .progress(true, 0)
                .cancelable(true)
                .show();
        mHandler = new Handler();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    for (File fi : files) {
                        unzip(context, destinationFolder, fi);
                        if (mDialog == null || !mDialog.isShowing()) {
                            // Cancelled
                            break;
                        }
                    }
                    if (context.isFinishing() || mDialog == null) return;
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            if (callback != null)
                                callback.onComplete();
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    if (context.isFinishing()) return;
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            Utils.showErrorDialog(context, R.string.failed_extract_file, e);
                        }
                    });
                }
            }
        });
    }
}