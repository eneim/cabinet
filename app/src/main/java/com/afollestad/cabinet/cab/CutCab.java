package com.afollestad.cabinet.cab;

import android.os.Handler;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cab.base.BaseFileCab;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialcab.MaterialCab;
import com.afollestad.materialdialogs.MaterialDialog;

public class CutCab extends BaseFileCab {

    private transient MaterialDialog mDialog;

    public CutCab() {
        super();
    }

    @Override
    public Spanned getTitle() {
        if (getFiles().size() == 1)
            return Html.fromHtml(getMainActivity().getString(R.string.move_x, getFiles().get(0).getName()));
        return Html.fromHtml(getMainActivity().getString(R.string.move_x, getFiles().size() + ""));
    }

    @Override
    public boolean canShowFab() {
        return true;
    }

    @Override
    public int getMenu() {
        return R.menu.copycut_cab;
    }

    @Override
    public boolean onCabCreated(MaterialCab materialCab, Menu menu) {
        super.onCabCreated(materialCab, menu);
        if (getFragment() != null && getFragment().getDirectory() != null) {
            menu.findItem(R.id.add).setVisible(getFragment().mQuery == null);
            menu.findItem(R.id.goUp).setVisible(getFragment().mQuery == null &&
                    getFragment().getDirectory().getParent() != null);
        }
        return true;
    }

    @Override
    public void paste() {
        Utils.lockOrientation(getMainActivity());

        final MaterialDialog.Builder builder = new MaterialDialog.Builder(getMainActivity())
                .content(R.string.moving);
        if (getFiles().size() > 1) {
            builder.progress(false, getFiles().size());
        } else builder.progress(true, 0);
        mDialog = builder.cancelable(true).show();

        final Handler uiHandler = new Handler();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < getFiles().size(); i++) {
                    if (mDialog == null || mDialog.isCancelled())
                        break;
                    else if (!mDialog.isIndeterminateProgress())
                        mDialog.setProgress(i);
                    final File file = getFiles().get(i);
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mDialog.setContent(file.getName());
                        }
                    });
                    final File newFile = File.getNewFile(getMainActivity(), getDirectory(), file.getName(),
                            file.isDirectory(), !file.isDirectory());
                    if (newFile == null) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showToast(getMainActivity().getString(R.string.failed_move_x_error,
                                        file.getName(), "destination file was null."));
                            }
                        });
                        return;
                    }
                    final File result;
                    try {
                        result = file.move(getMainActivity(), newFile, mDialog);
                    } catch (final Exception e) {
                        e.printStackTrace();
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                e.printStackTrace();
                                if (getMainActivity() != null)
                                    Utils.unlockOrientation(getMainActivity());
                                if (mDialog != null && mDialog.isShowing()) {
                                    try {
                                        mDialog.dismiss();
                                    } catch (Exception ignored) {
                                    }
                                }
                                showToast(getMainActivity().getString(R.string.failed_move_x_error,
                                        file.getName(), e.getLocalizedMessage()));
                            }
                        });
                        continue;
                    }
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            getFragment().getAdapter().addIfNotExists(result);
                            getFragment().setListShown(true);
                            Toast.makeText(getMainActivity(), getMainActivity().getString(R.string.moved_to,
                                    getDirectory().getPath()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Utils.unlockOrientation(getMainActivity());
                        finish();
                    }
                });
            }
        });
    }

    @Override
    public boolean onCabFinished(MaterialCab materialCab) {
        if (mDialog != null && mDialog.isShowing()) {
            try {
                mDialog.dismiss();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return super.onCabFinished(materialCab);
    }

    @Override
    public PasteMode canPaste() {
        return isActive() ? PasteMode.ENABLED : PasteMode.DISABLED;
    }

    @Override
    public boolean canPasteIntoSameDir() {
        return false;
    }

    @Override
    public boolean onCabItemClicked(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.add) {
            getFragment().onFabPressed(1);
        } else if (menuItem.getItemId() == R.id.goUp) {
            getMainActivity().switchDirectory(getFragment().getDirectory().getParent());
            return true;
        }
        return super.onCabItemClicked(menuItem);
    }
}
