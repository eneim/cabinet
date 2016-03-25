package com.afollestad.cabinet.ui.base;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.materialdialogs.internal.ThemeSingleton;
import com.squareup.leakcanary.RefWatcher;

/**
 * @author Aidan Follestad (afollestad)
 */
@SuppressLint("MissingSuperCall")
public abstract class ThemableActivity extends AppCompatActivity {

    private ThemeUtils mThemeUtils;

    protected boolean hasNavDrawer() {
        return false;
    }

    public ThemeUtils getThemeUtils() {
        return mThemeUtils;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mThemeUtils = new ThemeUtils(this);
        setTheme(mThemeUtils.getCurrent(hasNavDrawer()));
        super.onCreate(savedInstanceState);

        final ColorStateList accent = ColorStateList.valueOf(mThemeUtils.accentColor());
        ThemeSingleton.get().positiveColor = accent;
        ThemeSingleton.get().neutralColor = accent;
        ThemeSingleton.get().negativeColor = accent;
        ThemeSingleton.get().widgetColor = mThemeUtils.accentColor();
        ThemeSingleton.get().darkTheme = ThemeUtils.isDarkMode(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Sets color of entry in the system recents page
            ActivityManager.TaskDescription td = new ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher),
                    mThemeUtils.primaryColor());
            setTaskDescription(td);

            final int dark = getThemeUtils().primaryColorDark();
            if (hasNavDrawer()) {
                getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.transparent));
            } else {
                getWindow().setStatusBarColor(dark);
            }
            if (getThemeUtils().isColoredNavBar())
                getWindow().setNavigationBarColor(dark);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mThemeUtils.isChanged(true)) {
            setTheme(mThemeUtils.getCurrent(hasNavDrawer()));
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefWatcher refWatcher = App.getRefWatcher(this);
        refWatcher.watch(this);
    }
}
