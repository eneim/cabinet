package com.afollestad.cabinet.cab;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cab.base.BaseCab;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.materialcab.MaterialCab;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;

public class PickerCab extends BaseCab {

    private final boolean mExtractMode;
    private final boolean mRingtoneMode;
    private final ArrayList<String> mSaveFiles;
    private transient MaterialDialog mDialog;

    public PickerCab(boolean extractMode, boolean ringtoneMode) {
        mExtractMode = extractMode;
        mRingtoneMode = ringtoneMode;
        mSaveFiles = null;
    }

    public PickerCab(ArrayList<Uri> saveFiles) {
        mExtractMode = false;
        mRingtoneMode = false;
        mSaveFiles = new ArrayList<>();
        for (Uri uri : saveFiles)
            mSaveFiles.add(uri.toString());
    }

    public PickerCab(Uri saveFile) {
        this(new ArrayList<Uri>());
        mSaveFiles.add(saveFile.toString());
    }

    public boolean isExtractMode() {
        return mExtractMode;
    }

    public boolean isRingtoneMode() {
        return mRingtoneMode;
    }

    @Override
    public int getMenu() {
        return -1;
    }

    @Override
    public CharSequence getTitle() {
        if (mRingtoneMode)
            return getMainActivity().getString(R.string.pick_ringtone);
        else if (mSaveFiles != null)
            return getMainActivity().getString(R.string.save_to);
        else
            return getMainActivity().getString(mExtractMode ? R.string.choose_destination : R.string.pick_a_file);
    }

    @Override
    public BaseCab setFragment(Fragment fragment) {
        super.setFragment(fragment);
        if (getCab() != null) {
            Menu menu = getCab().getMenu();
            if (menu != null) {
                MenuItem item = menu.findItem(R.id.add);
                if (item != null)
                    item.setVisible(getFragment() != null);
            }
        }
        return this;
    }

    @Override
    public boolean onCabCreated(MaterialCab materialCab, Menu menu) {
        super.onCabCreated(materialCab, menu);
        if (mExtractMode || mSaveFiles != null)
            getCab().setMenu(R.menu.pick_cab);
        return true;
    }

    @Override
    public boolean onCabItemClicked(MenuItem menuItem) {
        if (getFragment() != null) {
            if (menuItem.getItemId() == R.id.add) {
                getFragment().onFabPressed(1);
                return false;
            } else if (menuItem.getItemId() == R.id.done && getMainActivity() != null) {
                if (mSaveFiles != null) {
                    mDialog = new MaterialDialog.Builder(getMainActivity())
                            .content(R.string.copying)
                            .progress(true, -1)
                            .show();
                    final Handler uiHandler = new Handler();
                    BackgroundThread.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mSaveFiles) {
                                for (final String uriStr : mSaveFiles) {
                                    final Uri uri = Uri.parse(uriStr);
                                    if (mDialog.isCancelled()) break;
                                    final File fi = File.fromUri(getMainActivity(), uri, true);
                                    if (fi == null) {
                                        uiHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                showToast(getMainActivity().getString(R.string.failed_copy_x_error,
                                                        uri.toString(), "unknown URI."));
                                            }
                                        });
                                        return;
                                    }
                                    final File dest = File.getNewFile(getMainActivity(),
                                            getFragment().getDirectory(), fi.getName(), false, true);
                                    if (dest == null) {
                                        uiHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                showToast(getMainActivity().getString(R.string.failed_copy_x_error,
                                                        fi.getName(), "destination file was null."));
                                            }
                                        });
                                        return;
                                    }
                                    try {
                                        final File result = fi.copy(getMainActivity(), dest, mDialog);
                                        if (result instanceof LocalFile) {
                                            ((LocalFile) result).initFileInfo();
                                        }
                                        getMainActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                getFragment().getAdapter().addIfNotExists(result);
                                                Toast.makeText(getMainActivity(), getMainActivity().getString(R.string.copied_to,
                                                        dest.getPath()), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } catch (Exception e) {
                                        if (mDialog.isCancelled()) break;
                                        e.printStackTrace();
                                        showToast(getMainActivity().getString(R.string.failed_copy_x_error,
                                                fi.getName(), e.getLocalizedMessage()));
                                    }
                                }
                                if (getMainActivity() != null) {
                                    getMainActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mDialog != null && mDialog.isShowing())
                                                mDialog.dismiss();
                                            finish();
                                        }
                                    });
                                }
                            }
                        }
                    });
                } else {
                    Intent intent = getMainActivity().getIntent()
                            .setData(getFragment().getDirectory().getUri());
                    getMainActivity().setResult(Activity.RESULT_OK, intent);
                    getMainActivity().finish();
                }
                return true;
            }
        }
        return super.onCabItemClicked(menuItem);
    }

    @Override
    public boolean onCabFinished(MaterialCab materialCab) {
        getMainActivity().pickMode = false;
        if (!getMainActivity().isFinishing() && mSaveFiles == null)
            getMainActivity().finish();
        if (mSaveFiles != null)
            mSaveFiles.clear();
        return super.onCabFinished(materialCab);
    }
}