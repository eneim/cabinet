package com.afollestad.cabinet.cab;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.bookmarks.BookmarkProvider;
import com.afollestad.cabinet.cab.base.BaseFileCab;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.zip.Unzipper;
import com.afollestad.cabinet.zip.Zipper;
import com.afollestad.materialcab.MaterialCab;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

public class MainCab extends BaseFileCab {

    public final static int CHOOSE_DESTINATION_REQUEST = 8041;

    public MainCab() {
        super();
    }

    @Override
    public void paste() {
        // Not implemented for the main cab
    }

    @Override
    public boolean canShowFab() {
        return false;
    }

    @Override
    public PasteMode canPaste() {
        return PasteMode.NOT_AVAILABLE;
    }

    @Override
    public boolean canPasteIntoSameDir() {
        return false;
    }

    @Override
    public CharSequence getTitle() {
        if (getFiles().size() == 1)
            return getFiles().get(0).getName();
        return "" + getFiles().size();
    }

    @Override
    public final int getMenu() {
        return R.menu.main_cab;
    }

    @Override
    public boolean onCabCreated(MaterialCab materialCab, Menu menu) {
        super.onCabCreated(materialCab, menu);
        boolean showUnzip = true;
        boolean showShare = true;
        boolean showPin = true;
        for (File fi : getFiles()) {
            if (fi.isDirectory()) {
                showShare = false;
                showUnzip = false;
                if (showPin && getMainActivity() != null)
                    showPin = !BookmarkProvider.contains(getMainActivity(), new BookmarkProvider.Item(fi));
            } else {
                showPin = false;
                if (!fi.isArchiveOrInArchive(getMainActivity()))
                    showUnzip = false;
            }
        }
        menu.findItem(R.id.archive).setTitle(showUnzip ? R.string.extract_to : R.string.archive);
        menu.findItem(R.id.share).setVisible(showShare);
        menu.findItem(R.id.bookmark).setVisible(showPin);
        if (getFragment() != null && getFragment().getDirectory() != null) {
            menu.findItem(R.id.goUp).setVisible(getFragment().mQuery == null &&
                    getFragment().getDirectory().getParent() != null);
        }
        return true;
    }

    public static void shareFile(Context context, File fi) {
        shareFile(context, fi.getUri(), fi.getMimeType(), fi.isDocumentTreeFile());
    }

    private static void shareFile(Context context, Uri uri, String mime, boolean shouldGrant) {
        if (mime == null || !mime.startsWith("audio/"))
            mime = "*/*";
        Intent intent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri);
        if (shouldGrant)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_apps_for_sharing, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e2) {
            Toast.makeText(context, e2.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareFiles(List<File> send) {
        if (send.size() == 1) {
            File fi = send.get(0);
            shareFile(getMainActivity(), fi);
        } else {
            Intent intent = new Intent()
                    .setAction(Intent.ACTION_SEND_MULTIPLE)
                    .setType("*/*");
            ArrayList<Uri> files = new ArrayList<>();
            boolean shouldGrant = false;
            for (File fi : send) {
                if (fi.isDocumentTreeFile() && !shouldGrant)
                    shouldGrant = true;
                files.add(fi.getUri());
            }
            if (shouldGrant)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
            try {
                getMainActivity().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getMainActivity(), R.string.no_apps_for_sharing, Toast.LENGTH_SHORT).show();
            } catch (SecurityException e2) {
                Toast.makeText(getMainActivity(), e2.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void shareNext(final List<File> from, List<File> to) {
        if (to == null) to = new ArrayList<>();
        if (from.size() == 0) {
            // All files have been moved into 'to'
            shareFiles(to);
            return;
        }
        final File next = from.get(0);
        from.remove(0);
        to.add(next);
        shareNext(from, to);
    }

    @Override
    public boolean onCabItemClicked(final MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.copy) {
            if (getMainActivity().getCab() instanceof BaseFileCab)
                ((BaseFileCab) getMainActivity().getCab()).overrideDestroy = true;
            getMainActivity().setCab(new CopyCab()
                    .setFragment(getFragment()).setFiles(getFiles(), true).start());
            return true;
        } else if (menuItem.getItemId() == R.id.move) {
            if (getMainActivity().getCab() instanceof BaseFileCab)
                ((BaseFileCab) getMainActivity().getCab()).overrideDestroy = true;
            getMainActivity().setCab(new CutCab()
                    .setFragment(getFragment()).setFiles(getFiles(), true).start());
            return true;
        } else if (menuItem.getItemId() == R.id.delete) {
            Utils.showConfirmDialog(getMainActivity(), R.string.delete,
                    getFiles().size() == 1 ? R.string.confirm_delete_prompt : R.string.confirm_delete_xfiles,
                    getFiles().size() == 1 ? getFiles().get(0).getName() : getFiles().size(),
                    new Utils.ClickListener() {
                        @Override
                        public void onPositive(int which, View view) {
                            Utils.lockOrientation(getMainActivity());
                            deleteNextFile();
                        }
                    }
            );
            return true;
        } else if (menuItem.getItemId() == R.id.selectAll) {
            List<File> newSelected = getFragment().mAdapter.checkAll();
            addFiles(newSelected, true);
            invalidate();
            return true;
        } else if (menuItem.getItemId() == R.id.share) {
            shareNext(getFiles(), null);
        } else if (menuItem.getItemId() == R.id.archive) {
            if (menuItem.getTitle().toString().equals(getMainActivity().getString(R.string.extract_to))) {
                getMainActivity().startActivityForResult(new Intent(getMainActivity(), MainActivity.class)
                        .setAction(Intent.ACTION_GET_CONTENT)
                        .putExtra("extract_mode", true), CHOOSE_DESTINATION_REQUEST);
            } else {
                Zipper.zip(getFragment(), getFiles(), new Zipper.ZipCallback() {
                    @Override
                    public void onComplete() {
                        MainCab.super.onCabItemClicked(menuItem);
                    }
                });
            }
            return true;
        } else if (menuItem.getItemId() == R.id.bookmark) {
            for (File fi : getFiles())
                BookmarkProvider.addItem(getMainActivity(), new BookmarkProvider.Item(fi));
            getMainActivity().reloadNavDrawer(true);
            finish();
        } else if (menuItem.getItemId() == R.id.goUp) {
            getMainActivity().switchDirectory(getFragment().getDirectory().getParent());
            return true;
        }
        return super.onCabItemClicked(menuItem);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == CHOOSE_DESTINATION_REQUEST) {
            final File dest = new LocalFile(new java.io.File(data.getData().getPath()));
            Unzipper.unzip(getMainActivity(), dest, getFiles(), new Zipper.ZipCallback() {
                @Override
                public void onComplete() {
                    if (getMainActivity() != null)
                        getMainActivity().switchDirectory(dest);
                    finish();
                }
            });
        }
    }

    private transient MaterialDialog mDialog;

    private void deleteNextFile() {
        Log.v("FabDelete", "Deleting next file...");
        if (getFiles() == null || getFiles().size() == 0) {
            try {
                if (mDialog != null)
                    mDialog.dismiss();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            mDialog = null;
            Log.v("FabDelete", "No files left in CAB, invalidating CAB.");
            invalidate();
            if (getMainActivity() != null)
                Utils.unlockOrientation(getMainActivity());
            return;
        }

        if (mDialog == null) {
            mDialog = new MaterialDialog.Builder(getMainActivity())
                    .content(R.string.deleting)
                    .progress(getFiles().size() == 1, getFiles().size(), true)
                    .cancelable(true)
                    .show();
        } else if (mDialog.isCancelled()) {
            return;
        }

        final File fi = getFiles().get(0);
        Log.v("FabDelete", "Deleting: " + fi);
        final Handler uiHandler = new Handler();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    fi.delete(getMainActivity());
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            getMainActivity().notifyDeleted(fi);
                            if (mDialog != null && !mDialog.isIndeterminateProgress())
                                mDialog.incrementProgress(1);
                            if (getFiles() != null && getFiles().size() > 0) {
                                if (getFragment() != null && getFragment().mAdapter != null)
                                    getFragment().mAdapter.remove(getFiles().get(0));
                                if (getFiles() != null)
                                    getFiles().remove(0);
                            }
                            deleteNextFile();
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (mDialog != null && mDialog.isShowing())
                                    mDialog.dismiss();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                            Utils.showErrorDialog(getMainActivity(), R.string.failed_delete_file, e);
                            mDialog = null;
                        }
                    });
                }
            }
        });
    }
}
