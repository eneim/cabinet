package com.afollestad.cabinet.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.fragments.base.LeakDetectDialogFragment;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ChangelogDialog extends LeakDetectDialogFragment {

    private final MaterialDialog.SingleButtonCallback mButtonCallback = new MaterialDialog.SingleButtonCallback() {
        @Override
        public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
            final int currentVersion;
            try {
                PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                currentVersion = pInfo.versionCode;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .putInt("changelog_version", currentVersion).commit();
        }
    };

    public static ChangelogDialog create(ThemableActivity context) {
        ChangelogDialog dialog = new ChangelogDialog();
        Bundle args = new Bundle();
        args.putBoolean("dark_theme", ThemeUtils.isDarkMode(context) || ThemeUtils.isTrueBlack(context));
        args.putInt("accent_color", context.getThemeUtils().accentColor());
        args.putInt("accent_color_lighter", context.getThemeUtils().accentColorLight());
        dialog.setArguments(args);
        return dialog;
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View customView;
        try {
            customView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_webview, null);
        } catch (InflateException e) {
            e.printStackTrace();
            return new MaterialDialog.Builder(getActivity())
                    .title(R.string.error)
                    .content("This device doesn't support web view. It is missing a system component.")
                    .positiveText(android.R.string.ok)
                    .build();
        }
        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .theme(getArguments().getBoolean("dark_theme") ? Theme.DARK : Theme.LIGHT)
                .title(R.string.changelog)
                .customView(customView, false)
                .positiveText(android.R.string.ok)
                .onPositive(mButtonCallback)
                .build();

        final WebView webView = (WebView) customView.findViewById(R.id.webview);
        try {
            // Load from cabinetchangelog.html in the assets folder
            StringBuilder buf = new StringBuilder();
            InputStream json = getActivity().getAssets().open("cabinetchangelog.html");
            BufferedReader in = new BufferedReader(new InputStreamReader(json, "UTF-8"));
            String str;
            while ((str = in.readLine()) != null)
                buf.append(str);
            in.close();

            // Inject color values for WebView body background and links
            webView.loadData(buf.toString()
                    .replace("{style-placeholder}", getArguments().getBoolean("dark_theme") ?
                            "body { background-color: #444444; color: #fff; }" :
                            "body { background-color: #fff; color: #000; }")
                    .replace("{link-color}", colorToHex(getArguments().getInt("accent_color")))
                    .replace("{link-color-active}", colorToHex(getArguments().getInt("accent_color_lighter")))
                    , "text/html", "UTF-8");
        } catch (Throwable e) {
            webView.loadData("<h1>Unable to load</h1><p>" + e.getLocalizedMessage() + "</p>", "text/html", "UTF-8");
        }
        return dialog;
    }

    private String colorToHex(int color) {
        return Integer.toHexString(color).substring(2);
    }
}