package com.afollestad.cabinet.fragments;

import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.method.LinkMovementMethod;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.fragments.base.LeakDetectDialogFragment;
import com.afollestad.cabinet.views.CircleView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.ThemeSingleton;

/**
 * @author Aidan Follestad (afollestad)
 */
public class HelpAndFeedbackDialog extends LeakDetectDialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        PackageManager pm = getActivity().getPackageManager();
        String packageName = getActivity().getPackageName();
        String versionName;
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "Unknown";
        }

        final CharSequence content = Html.fromHtml(getString(R.string.help_and_feedback_body, versionName));
        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.helpandfeedback_title)
                .content(content)
                .positiveText(android.R.string.ok)
                .build();
        if (dialog.getContentView() != null) {
            dialog.getContentView().setMovementMethod(LinkMovementMethod.getInstance());
            dialog.getContentView().setLinksClickable(true);
            final ColorStateList linkSl = new ColorStateList(new int[][]{
                    new int[]{-android.R.attr.state_pressed},
                    new int[]{android.R.attr.state_pressed}
            }, new int[]{
                    ThemeSingleton.get().positiveColor.getDefaultColor(),
                    CircleView.shiftColorUp(ThemeSingleton.get().positiveColor.getDefaultColor())
            });
            dialog.getContentView().setLinkTextColor(linkSl);
        }
        return dialog;
    }
}