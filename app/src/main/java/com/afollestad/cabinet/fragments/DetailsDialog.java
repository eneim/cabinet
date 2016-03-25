package com.afollestad.cabinet.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.base.LeakDetectDialogFragment;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Perm;
import com.afollestad.cabinet.utils.TimeUtils;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stericson.RootShell.exceptions.RootDeniedException;

import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeoutException;

public class DetailsDialog extends LeakDetectDialogFragment implements CompoundButton.OnCheckedChangeListener {

    private boolean mViewOnly;

    public DetailsDialog() {
    }

    public static DetailsDialog create(File file, boolean viewOnly) {
        DetailsDialog dialog = new DetailsDialog();
        Bundle args = new Bundle();
        args.putSerializable("file", file);
        args.putBoolean("view_only", viewOnly);
        dialog.setArguments(args);
        return dialog;
    }

    private View view;
    private TextView body;
    private File file;
    private Handler handler;

    public CheckBox ownerR;
    public CheckBox ownerW;
    public CheckBox ownerX;
    public CheckBox groupR;
    public CheckBox groupW;
    public CheckBox groupX;
    public CheckBox otherR;
    public CheckBox otherW;
    public CheckBox otherX;

    private String initialPermission;
    private String permissionsString;
    private String sizeString;

    private Spanned getBody() {
        if (getActivity() == null) return null;

        String content;
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(file.lastModified());

        if (permissionsString == null) {
            permissionsString = getString(R.string.loading);
            setPermissionCheckboxesEnabled(false);

            BackgroundThread.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    String perm = file.getPermissions(getActivity());
                    if (!perm.trim().isEmpty()) {
                        initialPermission = Perm.parse(perm, DetailsDialog.this);
                        permissionsString = initialPermission;
                        if (mViewOnly) {
                            initialPermission = null;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (view != null)
                                    view.findViewById(R.id.permissionsGroup).setVisibility(View.VISIBLE);
                            }
                        });
                    } else {
                        initialPermission = null;
                        permissionsString = getString(R.string.superuser_not_available);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ownerR.setOnCheckedChangeListener(DetailsDialog.this);
                            ownerW.setOnCheckedChangeListener(DetailsDialog.this);
                            ownerX.setOnCheckedChangeListener(DetailsDialog.this);
                            groupR.setOnCheckedChangeListener(DetailsDialog.this);
                            groupW.setOnCheckedChangeListener(DetailsDialog.this);
                            groupX.setOnCheckedChangeListener(DetailsDialog.this);
                            otherR.setOnCheckedChangeListener(DetailsDialog.this);
                            otherW.setOnCheckedChangeListener(DetailsDialog.this);
                            otherX.setOnCheckedChangeListener(DetailsDialog.this);
                        }
                    });

                    invalidatePerms();
                }
            });
        }
        if (sizeString == null) {
            sizeString = getString(R.string.loading);
            BackgroundThread.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        sizeString = file.getSizeString(getActivity());
                    } catch (Exception e) {
                        sizeString = e.getLocalizedMessage();
                    }
                    invalidateBody();
                }
            });
        }

        content = getString(R.string.details_body_file,
                file.getName(), file.getPath(), sizeString,
                TimeUtils.toStringLong(getActivity(), cal), permissionsString);
        return Html.fromHtml(content);
    }

    @SuppressLint("InflateParams")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        file = (File) getArguments().getSerializable("file");
        mViewOnly = getArguments().getBoolean("view_only");
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        view = layoutInflater.inflate(R.layout.dialog_custom, null);
        handler = new Handler();

        ownerR = (CheckBox) view.findViewById(R.id.ownerR);
        ownerW = (CheckBox) view.findViewById(R.id.ownerW);
        ownerX = (CheckBox) view.findViewById(R.id.ownerX);
        groupR = (CheckBox) view.findViewById(R.id.groupR);
        groupW = (CheckBox) view.findViewById(R.id.groupW);
        groupX = (CheckBox) view.findViewById(R.id.groupX);
        otherR = (CheckBox) view.findViewById(R.id.otherR);
        otherW = (CheckBox) view.findViewById(R.id.otherW);
        otherX = (CheckBox) view.findViewById(R.id.otherX);

        body = (TextView) view.findViewById(R.id.body);
        body.setText(getBody());

        return new MaterialDialog.Builder(getActivity())
                .positiveText(android.R.string.ok)
                .title(R.string.details)
                .customView(view, true)
                .autoDismiss(false)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        applyPermissionsIfNecessary();
                    }
                }).build();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        BackgroundThread.reset();
    }

    private void applyPermissionsIfNecessary() {
        if ((permissionsString == null || permissionsString.length() != 3) ||
                (initialPermission == null || initialPermission.equals(permissionsString))) {
            dismiss();
            return;
        }
        final MainActivity activity = (MainActivity) getActivity();
        dismiss();
        final MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .cancelable(false)
                .content(R.string.applying_permissions)
                .progress(true, 0)
                .show();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    file.chmod(Integer.parseInt(permissionsString));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            activity.reloadActiveFolder();
                        }
                    });
                } catch (RootDeniedException | TimeoutException | IOException | RemoteException | PluginFileImpl.ResultException | File.OperationNotSupported e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            Utils.showErrorDialog(activity, R.string.failed_chmod_file, e);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        invalidatePerms();
    }

    private void invalidatePerms() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean enabled = initialPermission != null;
                setPermissionCheckboxesEnabled(enabled);

                int owner = 0;
                if (ownerR.isChecked()) owner += Perm.READ;
                if (ownerW.isChecked()) owner += Perm.WRITE;
                if (ownerX.isChecked()) owner += Perm.EXECUTE;
                int group = 0;
                if (groupR.isChecked()) group += Perm.READ;
                if (groupW.isChecked()) group += Perm.WRITE;
                if (groupX.isChecked()) group += Perm.EXECUTE;
                int other = 0;
                if (otherR.isChecked()) other += Perm.READ;
                if (otherW.isChecked()) other += Perm.WRITE;
                if (otherX.isChecked()) other += Perm.EXECUTE;

                permissionsString = owner + "" + group + "" + other;
                invalidateBody();
            }
        });
    }

    private void setPermissionCheckboxesEnabled(boolean enabled) {
        ownerR.setEnabled(enabled);
        ownerW.setEnabled(enabled);
        ownerX.setEnabled(enabled);
        groupR.setEnabled(enabled);
        groupW.setEnabled(enabled);
        groupX.setEnabled(enabled);
        otherR.setEnabled(enabled);
        otherW.setEnabled(enabled);
        otherX.setEnabled(enabled);
    }

    private void invalidateBody() {
        final Spanned newBody = getBody();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                body.setText(newBody);
            }
        });
    }

    public void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }
}
