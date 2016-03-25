package com.afollestad.cabinet.zip;

import android.os.Handler;
import android.view.View;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cram.Cram;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.content.DirectoryFragment;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.List;

public class Zipper {

    private static MaterialDialog mDialog;

    private static void performZip(final String ext,
                                   final DirectoryFragment fragment,
                                   final List<File> files,
                                   final ZipCallback callback) {
        final File dest = File.getNewFile(fragment.getActivity(), fragment.getDirectory(),
                files.get(0).getNameNoExtension() + ext, false, true);
        final Handler uiHandler = new Handler();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mDialog = new MaterialDialog.Builder(fragment.getActivity())
                                    .content(R.string.archiving)
                                    .progress(true, 0)
                                    .cancelable(true)
                                    .show();
                        }
                    });

                    try {
                        Cram.with((PluginActivity) fragment.getActivity())
                                .writer(dest)
                                .put(files)
                                .complete();
                        fragment.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dismissProgressDialog();
                                fragment.reload(true);
                                if (callback != null)
                                    callback.onComplete();
                            }
                        });
                    } catch (final Exception e) {
                        e.printStackTrace();
                        if (fragment.getActivity() == null)
                            return;
                        try {
                            if (dest.exists())
                                dest.delete(fragment.getActivity());
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                        fragment.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dismissProgressDialog();
                                Utils.showErrorDialog(fragment.getActivity(), R.string.failed_archive_files, e);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void zip(final DirectoryFragment fragment, final List<File> files, final ZipCallback callback) {
        new MaterialDialog.Builder(fragment.getActivity())
                .title(R.string.archive_format)
                .items(R.array.archive_formats)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                        String ext;
                        switch (i) {
                            case 1:
                                ext = ".tar.gz";
                                break;
                            case 2:
                                ext = ".7z";
                                break;
                            default:
                                ext = ".zip";
                                break;
                        }
                        performZip(ext, fragment, files, callback);
                    }
                }).show();
    }

    private static void dismissProgressDialog() {
        if (mDialog == null) return;
        try {
            mDialog.dismiss();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public interface ZipCallback {
        void onComplete();
    }
}