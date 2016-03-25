package com.afollestad.cabinet.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.views.CircleView;

public class ThemeUtils {

    public ThemeUtils(Activity context) {
        mContext = context;
        isChanged(false); // invalidate stored booleans
    }

    private final Context mContext;
    private boolean mDarkMode;
    private boolean mTrueBlack;
    @ColorInt
    private int mLastPrimaryColor;
    @ColorInt
    private int mLastAccentColor;
    private boolean mLastColoredNav;
    @ColorInt
    private int mThumbnailColor;
    private boolean mDirectoryCount;
    private boolean mLoadThumbnails;

    @StyleRes
    public int getPopupTheme() {
        if (mDarkMode || mTrueBlack) {
            return R.style.ThemeOverlay_AppCompat_Dark;
        } else {
            return R.style.ThemeOverlay_AppCompat_Light;
        }
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("dark_mode", false);
    }

    public static boolean isTrueBlack(Context context) {
        if (!isDarkMode(context)) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("true_black", false);
    }

    public static boolean collasingAppBar(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("collapsing_appbar", true);
    }

    public static boolean isDirectoryCount(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("directory_count", false);
    }

    public static boolean isLoadThumbnails(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("load_thumbnails", true);
    }

    @ColorInt
    public int primaryColor() {
        return primaryColor(mContext);
    }

    @ColorInt
    public static int primaryColor(Context context) {
        final int defaultColor = ContextCompat.getColor(context, R.color.cabinet_color);
        return PreferenceManager.getDefaultSharedPreferences(context).getInt("primary_color", defaultColor);
    }

    @ColorInt
    public void primaryColor(int newColor) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("primary_color", newColor).commit();
    }

    @ColorInt
    public int primaryColorDark() {
        return CircleView.shiftColorDown(primaryColor());
    }

    @ColorInt
    public int accentColor() {
        final int defaultColor = ContextCompat.getColor(mContext, R.color.cabinet_accent_color);
        return PreferenceManager.getDefaultSharedPreferences(mContext).getInt("accent_color", defaultColor);
    }

    @ColorInt
    public int accentColorLight() {
        return CircleView.shiftColorUp(accentColor());
    }

    @ColorInt
    public int accentColorDark() {
        return CircleView.shiftColorDown(accentColor());
    }

    public void accentColor(@ColorInt int newColor) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("accent_color", newColor).commit();
    }

    @ColorInt
    public int thumbnailColor() {
        final int defaultColor = ContextCompat.getColor(mContext, R.color.non_colored_folder);
        return PreferenceManager.getDefaultSharedPreferences(mContext).getInt("thumbnail_color", defaultColor);
    }

    public void thumbnailColor(@ColorInt int newColor) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("thumbnail_color", newColor).commit();
    }

    public boolean isColoredNavBar() {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("colored_navbar", false);
    }

    public boolean isChanged(boolean checkForChanged) {
        final boolean darkTheme = isDarkMode(mContext);
        final boolean blackTheme = isTrueBlack(mContext);
        final int primaryColor = primaryColor();
        final int accentColor = accentColor();
        final int thumbnailColor = thumbnailColor();
        final boolean coloredNav = isColoredNavBar();
        final boolean directoryCount = isDirectoryCount(mContext);
        final boolean loadThumbnails = isLoadThumbnails(mContext);

        boolean changed = false;
        if (checkForChanged) {
            changed = mDarkMode != darkTheme || mTrueBlack != blackTheme ||
                    mLastPrimaryColor != primaryColor || mLastAccentColor != accentColor ||
                    coloredNav != mLastColoredNav || thumbnailColor != mThumbnailColor ||
                    directoryCount != mDirectoryCount || loadThumbnails != mLoadThumbnails;
        }

        mDarkMode = darkTheme;
        mTrueBlack = blackTheme;
        mLastPrimaryColor = primaryColor;
        mLastAccentColor = accentColor;
        mLastColoredNav = coloredNav;
        mThumbnailColor = thumbnailColor;
        mDirectoryCount = directoryCount;
        mLoadThumbnails = loadThumbnails;

        return changed;
    }

    @StyleRes
    public int getCurrent(boolean hasNavDrawer) {
        if (hasNavDrawer) {
            if (mTrueBlack) {
                return R.style.Theme_CabinetTrueBlack_WithNavDrawer;
            } else if (mDarkMode) {
                return R.style.Theme_CabinetDark_WithNavDrawer;
            } else {
                return R.style.Theme_Cabinet_WithNavDrawer;
            }
        } else {
            if (mTrueBlack) {
                return R.style.Theme_CabinetTrueBlack;
            } else if (mDarkMode) {
                return R.style.Theme_CabinetDark;
            } else {
                return R.style.Theme_Cabinet;
            }
        }
    }
}
