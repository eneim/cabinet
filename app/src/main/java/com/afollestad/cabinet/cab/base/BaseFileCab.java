package com.afollestad.cabinet.cab.base;

import android.app.Fragment;
import android.util.Log;
import android.view.Menu;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.content.DirectoryFragment;
import com.afollestad.materialcab.MaterialCab;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseFileCab extends BaseCab {

    private final static boolean DEBUG = false;

    public enum PasteMode {
        ENABLED,
        NOT_AVAILABLE,
        DISABLED
    }

    protected BaseFileCab() {
        super();
        mFiles = new ArrayList<>();
    }

    private File mDirectory;
    private final List<File> mFiles;
    public boolean overrideDestroy;

    protected abstract boolean canShowFab();

    public abstract void paste();

    protected abstract PasteMode canPaste();

    protected abstract boolean canPasteIntoSameDir();

    @Override
    public boolean onCabCreated(MaterialCab materialCab, Menu menu) {
        super.onCabCreated(materialCab, menu);
        invalidateFab();
        return true;
    }

    private void log(String message) {
        //noinspection PointlessBooleanExpression
        if (!DEBUG)
            return;
        Log.v("Fab", message);
    }

    private void invalidateFab() {
        log("invalidateFab()");
        boolean hide = false;
        if (!canShowFab() && isActive()) {
            log("Cannot use the FAB in the current mode.");
            hide = true;
        } else if (getMainActivity() != null) {
            log("Mode: " + canPaste());
            getMainActivity().fabPasteMode = canPaste();
            if (canPaste() != PasteMode.NOT_AVAILABLE) {
                if (isActive() && canPaste() == PasteMode.DISABLED) {
                    log("Can't paste");
                } else {
                    if (getFiles().size() == 0) Log.v("Fab", "No files are in the CAB");
                    for (File fi : getFiles()) {
                        if (!canPasteIntoSameDir()) {
                            log("Checking if " + fi.getParent().getUri() + " == " + getDirectory().getUri());
                            if (fi.getParent().equals(getDirectory())) {
                                log("They are equal");
                                hide = true;
                                break;
                            }
                        }
                        if (fi.isDirectory() && getDirectory().getUri().toString().startsWith(fi.getUri().toString())) {
                            log("Cannot paste into a selected directory");
                            hide = true;
                            break;
                        }
                    }
                }
                if (hide) log("Fab is disabled");
                else log("Fab is not disabled");
            } else log("Paste mode not available");
        }
        if (getMainActivity() != null) {
            getMainActivity().disableFab(hide, false);
            getMainActivity().setFabIcon(canPaste() == BaseFileCab.PasteMode.ENABLED ?
                    R.drawable.ic_fab_paste : R.drawable.ic_fab_new);
        }
    }

    @Override
    public BaseFileCab setFragment(Fragment fragment) {
        if (fragment instanceof DirectoryFragment) {
            mDirectory = ((DirectoryFragment) fragment).getDirectory();
            super.setFragment(fragment);
            invalidateFab();
        }
        return this;
    }

    public final void addFile(File file, boolean notifyAdapter) {
        if (notifyAdapter)
            getFragment().mAdapter.setItemChecked(file, true);
        mFiles.add(file);
        invalidate();
    }

    protected final void addFiles(List<File> files, boolean notifyAdapter) {
        if (notifyAdapter)
            getFragment().mAdapter.setItemsChecked(files, true);
        mFiles.addAll(files);
        invalidate();
    }

    public final int findFile(File file) {
        for (int i = 0; i < getFiles().size(); i++) {
            if (getFiles().get(i).equals(file)) {
                return i;
            }
        }
        return -1;
    }

    public final void setFile(int index, File file, boolean notifyAdapter) {
        // Uncheck old file
        if (notifyAdapter)
            getFragment().mAdapter.setItemChecked(getFiles().get(index), false);
        // Replace old file with new one
        getFiles().set(index, file);
        // Check new file
        if (notifyAdapter)
            getFragment().mAdapter.setItemChecked(file, true);
        invalidate();
    }

    public final void removeFile(File file, boolean notifyAdapter) {
        if (notifyAdapter)
            getFragment().mAdapter.setItemChecked(file, false);
        for (int i = 0; i < mFiles.size(); i++) {
            if (file.getUri().toString().equals(mFiles.get(i).getUri().toString())) {
                mFiles.remove(i);
                invalidate();
                break;
            }
        }
    }

    public final BaseFileCab setFile(File file, boolean notifyAdapter) {
        if (notifyAdapter) {
            getFragment().mAdapter.resetChecked();
            getFragment().mAdapter.setItemChecked(file, true);
        }
        clearFiles();
        mFiles.add(file);
        invalidate();
        return this;
    }

    public final BaseFileCab setFiles(List<File> files, boolean notifyAdapter) {
        if (notifyAdapter) {
            getFragment().mAdapter.resetChecked();
            getFragment().mAdapter.setItemsChecked(files, true);
        }
        clearFiles();
        mFiles.addAll(files);
        invalidate();
        return this;
    }

    private void clearFiles() {
        mFiles.clear();
    }

    public final boolean containsFile(File file) {
        for (File fi : mFiles) {
            if (fi.equals(file)) return true;
        }
        return false;
    }

    @Override
    protected final void invalidate() {
        if (getFiles().size() == 0) finish();
        else super.invalidate();
    }

    protected final File getDirectory() {
        return mDirectory;
    }

    public final List<File> getFiles() {
        return mFiles;
    }

    @Override
    public int getMenu() {
        return -1;
    }

    @Override
    public boolean onCabFinished(MaterialCab materialCab) {
        super.onCabFinished(materialCab);
        if (!overrideDestroy) {
            clearFiles();
            getFragment().mAdapter.resetChecked();
            if (canPaste() == PasteMode.ENABLED) {
                getMainActivity().fabPasteMode = PasteMode.DISABLED;
                getMainActivity().setFabIcon(R.drawable.ic_fab_new);
            }
        }
        getMainActivity().disableFab(false, false);
        return true;
    }
}
