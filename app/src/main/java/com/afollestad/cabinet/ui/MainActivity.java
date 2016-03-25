package com.afollestad.cabinet.ui;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.view.menu.BaseMenuPresenter;
import android.support.v7.view.menu.ListMenuItemView;
import android.support.v7.view.menu.MenuItemImpl;
import android.support.v7.view.menu.MenuPopupHelper;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.BuildConfig;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.bookmarks.BookmarkProvider;
import com.afollestad.cabinet.cab.MainCab;
import com.afollestad.cabinet.cab.PickerCab;
import com.afollestad.cabinet.cab.base.BaseCab;
import com.afollestad.cabinet.cab.base.BaseFileCab;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.ChangelogDialog;
import com.afollestad.cabinet.fragments.NavigationDrawerFragment;
import com.afollestad.cabinet.fragments.content.ArchiveFragment;
import com.afollestad.cabinet.fragments.content.ContentFragment;
import com.afollestad.cabinet.fragments.content.DirectoryFragment;
import com.afollestad.cabinet.plugins.PluginDataProvider;
import com.afollestad.cabinet.plugins.base.PluginConstants;
import com.afollestad.cabinet.ui.base.CrumbsActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.StorageUtils;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.utils.ViewUtils;
import com.afollestad.cabinet.views.BreadCrumbLayout;
import com.afollestad.cabinet.views.ScrimInsetsFrameLayout;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDTintHelper;
import com.afollestad.materialdialogs.internal.ThemeSingleton;
import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.crashlytics.android.Crashlytics;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;

import io.fabric.sdk.android.Fabric;

public class MainActivity extends CrumbsActivity implements BillingProcessor.IBillingHandler {

    public final static int UNINSTALL_PLUGIN_REQUEST = 6969;
    public BaseFileCab.PasteMode fabPasteMode = BaseFileCab.PasteMode.DISABLED;
    public View appBar;
    public boolean shouldAttachFab; // used during config change, tells fragment to reattach to cab
    public boolean pickMode; // flag indicating whether user is picking a file/ringtone for another app
    public boolean collapsingAppBar;
    private BillingProcessor mBP; // used for donations
    private BaseCab mCab; // the current contextual action bar, saves state throughout fragments
    private FloatingActionMenu mFab; // the floating blue add/paste button
    private FabListener mFabListener; // a callback used to notify DirectoryFragment of fab press
    private boolean mFabDisabled; // flag indicating whether fab should stay hidden while scrolling
    private DrawerLayout mDrawerLayout;
    private View mAppBarShadow;
    private View mOuterFrame;
    private Toolbar mToolbar;
    private boolean mFabFrameVisible;
    private boolean mAnimationDirectionDown;
    private boolean mAnimationDirectionUp;
    private AnimatorSet mAppBarAnimator;
    private Runnable mAppBarAnimationRunnable;

    private ValueAnimator mTabletExpander;

    public void notifyScroll(int dy, boolean reset, View... paddedViews) {
        if (mCrumbs.getVisibility() == View.GONE || reset || !collapsingAppBar) {
            postAppBarAnimate(true);
            mAnimationDirectionUp = false;
            mAnimationDirectionDown = false;
        } else if (appBar != null && mToolbar != null) {
            float newTranslationY = appBar.getTranslationY() - (dy);
            if (dy > 0) {
                final float minY = -mToolbar.getHeight();
                if (newTranslationY < minY)
                    newTranslationY = minY;
                mAnimationDirectionUp = true;
                mAnimationDirectionDown = false;
            } else if (dy < 0) {
                if (newTranslationY > 0)
                    newTranslationY = 0;
                mAnimationDirectionUp = false;
                mAnimationDirectionDown = true;
            }
            appBar.setTranslationY(newTranslationY);
            mAppBarShadow.setTranslationY(newTranslationY);
            final int padding = appBar.getMeasuredHeight() - (int) Math.abs(newTranslationY);
            ViewUtils.applyTopPadding(padding, paddedViews);
        }
    }

    public void finishWithAnimation(View... paddedViews) {
        if (mAnimationDirectionUp) {
            postAppBarAnimate(false, paddedViews);
        } else if (mAnimationDirectionDown) {
            postAppBarAnimate(true, paddedViews);
        }
        mAnimationDirectionDown = false;
        mAnimationDirectionUp = false;
    }

    public void postAppBarAnimate(final boolean show, final View... paddedViews) {
        if (appBar == null || mToolbar == null)
            return;
        appBar.removeCallbacks(mAppBarAnimationRunnable);
        mAppBarAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                int minY = -mToolbar.getHeight();
                int destY = (show ? 0 : minY);

                if (mAppBarAnimator != null && mAppBarAnimator.isStarted()) {
                    mAppBarAnimator.cancel();
                }

                if (appBar.getY() != destY) {
                    mAppBarAnimator = new AnimatorSet();
                    ObjectAnimator animator = ObjectAnimator.ofFloat(appBar, View.Y, appBar.getY(), destY);
                    animator.setInterpolator(new AccelerateDecelerateInterpolator());
                    animator.setDuration(150);
                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            float animatedValue = (float) animation.getAnimatedValue();
                            ViewUtils.applyTopPadding(appBar.getMeasuredHeight() - (int) Math.abs(animatedValue), paddedViews);
                            mAppBarShadow.setTranslationY(animatedValue);
                        }
                    });
                    mAppBarAnimator.play(animator);
                    mAppBarAnimator.start();
                }
            }
        };
        appBar.postDelayed(mAppBarAnimationRunnable, 50);
    }

    public BaseCab getCab() {
        return mCab;
    }

    public void setCab(BaseCab cab) {
        mCab = cab;
    }

    public void toggleFab(boolean hide) {
        if (mFabDisabled) getFab().hideMenuButton(false);
        else if (hide) getFab().hideMenuButton(true);
        else getFab().showMenuButton(true);
    }

    public void disableFab(boolean disable, boolean force) {
        if (getFab() == null) return;
        if (!disable) {
            if (getFab().getVisibility() == View.INVISIBLE)
                getFab().setVisibility(View.VISIBLE);
            getFab().showMenuButton(true);
        } else {
            getFab().hideMenuButton(true);
            if (force) {
                getFab().setVisibility(View.INVISIBLE);
            } else {
                getFab().hideMenuButton(true);
            }
        }
        mFabDisabled = disable;
    }

    public void setFabListener(FabListener mFabListener) {
        this.mFabListener = mFabListener;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCab != null && mCab.isActive())
            outState.putSerializable("cab", mCab);
        outState.putSerializable("fab_pastemode", fabPasteMode);
        outState.putBoolean("fab_disabled", mFabDisabled);
        outState.putBoolean("fab_frame_visible", mFabFrameVisible);
    }

    public boolean tryClearQuery() {
        boolean queryCleared = false;
        Fragment current = getFragmentManager().findFragmentById(R.id.container);
        if (current != null && current instanceof DirectoryFragment) {
            // Causes search to be exited
            DirectoryFragment dirFrag = (DirectoryFragment) current;
            queryCleared = dirFrag.setQuery(null, false);
            if (queryCleared) dirFrag.reload(true);
        }
        return queryCleared;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else if (getFab().isOpened()) {
            getFab().close(true);
        } else {
            try {
                if (!tryClearQuery()) {
                    if (mCrumbs.popHistory()) {
                        // Go to previous crumb in history
                        final BreadCrumbLayout.Crumb crumb = mCrumbs.lastHistory();
                        switchDirectory(crumb, false, false);
                    } else {
                        // No history to go back to, close CAB or default action (will usually close the app)
                        if (mCab != null && mCab.isActive()) {
                            mCab.finish();
                        } else {
                            super.onBackPressed();
                        }
                    }
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected boolean hasNavDrawer() {
        return true;
    }

    public DrawerLayout getDrawerLayout() {
        if (mDrawerLayout == null)
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        return mDrawerLayout;
    }

    public void closeDrawers() {
        if (mDrawerLayout != null)
            mDrawerLayout.closeDrawers();
    }

    public FloatingActionMenu getFab() {
        if (mFab == null)
            mFab = (FloatingActionMenu) findViewById(R.id.fab_actions);
        return mFab;
    }

    public BreadCrumbLayout getCrumbs() {
        return mCrumbs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final StorageUtils.SDCard card = App.getSDCard(true);
        super.onCreate(savedInstanceState);
        if (card != null) {
            if (!card.canWrite &&
                    !PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean("shown_sd_warning", false)) {
                try {
                    new MaterialDialog.Builder(this)
                            .content(R.string.sdcard_write_warning)
                            .positiveText(android.R.string.ok)
                            .neutralText(R.string.dont_show_again)
                            .cancelable(false)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    super.onPositive(dialog);
                                }

                                @Override
                                public void onNeutral(MaterialDialog dialog) {
                                    super.onNeutral(dialog);
                                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
                                            .edit().putBoolean("shown_sd_warning", true).commit();
                                }
                            })
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!BuildConfig.DEBUG) {
            Fabric.with(this, new Crashlytics());
        }

        mToolbar = (Toolbar) findViewById(R.id.appbar_toolbar);
        mToolbar.setSubtitleTextAppearance(this, R.style.DirectoryToolbarTitle);
        mToolbar.setSubtitleTextAppearance(this, R.style.DirectoryToolbarSubtitle);
        mToolbar.setPopupTheme(getThemeUtils().getPopupTheme());
        setSupportActionBar(mToolbar);

        appBar = findViewById(R.id.toolbar_directory);
        appBar.setBackgroundColor(getThemeUtils().primaryColor());

        mAppBarShadow = findViewById(R.id.appbar_shadow);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("cab")) {
                mCab = (BaseCab) savedInstanceState.getSerializable("cab");
                if (mCab != null) {
                    mCab.setActivity(this);
                    if (mCab instanceof BaseFileCab) {
                        shouldAttachFab = true;
                    } else {
                        if (mCab instanceof PickerCab)
                            pickMode = true;
                        mCab.start();
                    }
                }
            }
            fabPasteMode = (BaseFileCab.PasteMode) savedInstanceState.getSerializable("fab_pastemode");
            mFabDisabled = savedInstanceState.getBoolean("fab_disabled");
            disableFab(mFabDisabled, true);
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null)
            mDrawerLayout.setStatusBarBackgroundColor(getThemeUtils().primaryColorDark());
        else {
            Drawable menu = ContextCompat.getDrawable(this, R.drawable.ic_menu);
            mToolbar.setNavigationIcon(menu);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                getWindow().setStatusBarColor(getThemeUtils().primaryColorDark());
        }
        NavigationDrawerFragment mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(mDrawerLayout, mToolbar);

        View drawerFrame = findViewById(R.id.nav_drawer_frame);
        if (drawerFrame != null) {
            int navDrawerMargin = getResources().getDimensionPixelSize(R.dimen.nav_drawer_margin);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int navDrawerWidthLimit = getResources().getDimensionPixelSize(R.dimen.nav_drawer_width_limit);
            int navDrawerWidth = displayMetrics.widthPixels - navDrawerMargin;
            if (navDrawerWidth > navDrawerWidthLimit)
                navDrawerWidth = navDrawerWidthLimit;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) drawerFrame.getLayoutParams();

            if (mDrawerLayout != null) {
                params.width = navDrawerWidth;
                if (drawerFrame instanceof ScrimInsetsFrameLayout) {
                    ScrimInsetsFrameLayout scrimFrame = (ScrimInsetsFrameLayout) drawerFrame;
                    scrimFrame.setOnInsetsCallback(new ScrimInsetsFrameLayout.OnInsetsCallback() {
                        @Override
                        public void onInsetsChanged(Rect insets) {
                            NavigationDrawerFragment frag = (NavigationDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
                            if (frag != null) frag.invalidatePadding(insets.top);
                        }
                    });
                }
            } else {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                final boolean expanded = prefs.getBoolean("static_drawer_expanded", true);
                params.width = expanded ? navDrawerWidth : 0;
            }
        }

        setupFab(findViewById(R.id.fab_actions), -1);
        setupFab(findViewById(R.id.actionNewFile), 0);
        setupFab(findViewById(R.id.actionNewFolder), 1);

        findViewById(R.id.outerFrame).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFab().close(true);
            }
        });

        if (savedInstanceState != null) {
            if (mFabFrameVisible = savedInstanceState.getBoolean("fab_frame_visible")) {
                toggleOuterFrame(true);
                if (!getFab().isShown())
                    getFab().toggle(false);
            }
        }

        mBP = new BillingProcessor(this, "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlPBB2hP/R0PrXtK8NPeDX7QV1fvk1hDxPVbIwRZLIgO5l/ZnAOAf8y9Bq57+eO5CD+ZVTgWcAVrS/QsiqDI/MwbfXcDydSkZLJoFofOFXRuSL7mX/jNwZBNtH0UrmcyFx1RqaHIe9KZFONBWLeLBmr47Hvs7dKshAto2Iy0v18kN48NqKxlWtj/PHwk8uIQ4YQeLYiXDCGhfBXYS861guEr3FFUnSLYtIpQ8CiGjwfU60+kjRMmXEGnmhle5lqzj6QeL6m2PNrkbJ0T9w2HM+bR7buHcD8e6tHl2Be6s/j7zn1Ypco/NCbqhtPgCnmLpeYm8EwwTnH4Yei7ACR7mXQIDAQAB", this);
        processIntent(getIntent(), savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        collapsingAppBar = ThemeUtils.collasingAppBar(this);
    }

    public boolean invalidateShowCrumbs() {
        boolean shouldBeVisible = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_breadcrumbs", true);
        if (shouldBeVisible && mCrumbs.getVisibility() != View.VISIBLE) {
            mCrumbs.setVisibility(View.VISIBLE);
            return true;
        } else if (!shouldBeVisible && mCrumbs.getVisibility() == View.VISIBLE) {
            mCrumbs.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    private void setupFab(View view, int action) {
        final ThemeUtils theme = getThemeUtils();
        if (view instanceof FloatingActionButton) {
            FloatingActionButton btn = (FloatingActionButton) view;
            if (theme.accentColor() == Color.WHITE) {
                btn.setColorNormal(theme.accentColor());
                btn.setColorPressed(theme.accentColorDark());
                btn.setColorFilter(ContextCompat.getColor(this, R.color.dark_theme_gray), PorterDuff.Mode.SRC_IN);
            } else {
                btn.setColorNormal(theme.accentColor());
                btn.setColorPressed(theme.accentColorLight());
                btn.clearColorFilter();
            }
            btn.setTag(action);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mFabListener != null)
                        mFabListener.onFabPressed((Integer) v.getTag());
                    ((FloatingActionMenu) v.getParent()).toggle(true);
                }
            });
        } else {
            getFab().setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
                @Override
                public void onMenuToggle(boolean expanded) {
                    toggleOuterFrame(expanded);
                }
            });
            setFabIcon(R.drawable.ic_fab_new);
        }
    }

    public void setFabIcon(@DrawableRes int resId) {
        if (getFab() == null)
            return;
        final ThemeUtils theme = getThemeUtils();
        getFab().getMenuIconView().setImageResource(resId);
        if (theme.accentColor() == Color.WHITE) {
            getFab().setMenuButtonColorNormal(theme.accentColor());
            getFab().setMenuButtonColorPressed(theme.accentColorDark());
            getFab().getMenuIconView().getDrawable().setColorFilter(ContextCompat.getColor(this,
                    R.color.dark_theme_gray), PorterDuff.Mode.SRC_IN);
        } else {
            getFab().setMenuButtonColorNormal(theme.accentColor());
            getFab().setMenuButtonColorPressed(theme.accentColorLight());
            getFab().getMenuIconView().getDrawable().clearColorFilter();
        }

        try {
            Class<?> cls = getFab().getClass();
            Field menuButtonFld = cls.getDeclaredField("mMenuButton");
            menuButtonFld.setAccessible(true);
            FloatingActionButton menuButton = (FloatingActionButton) menuButtonFld.get(getFab());
            menuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (fabPasteMode == BaseFileCab.PasteMode.ENABLED)
                        ((BaseFileCab) getCab()).paste();
                    else
                        ((FloatingActionMenu) v.getParent()).toggle(true);
                }
            });
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void toggleOuterFrame(boolean show) {
        mOuterFrame = findViewById(R.id.outerFrame);
        mOuterFrame.clearAnimation();
        mFabFrameVisible = show;
        Animation anim = AnimationUtils.loadAnimation(this,
                show ? R.anim.fade_in : R.anim.fade_out);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (mOuterFrame != null)
                    mOuterFrame.setVisibility(mFabFrameVisible ? View.VISIBLE : View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mOuterFrame.startAnimation(anim);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent(intent, null);
    }

    @SuppressLint("CommitPrefEdits")
    private void checkRating() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("shown_rating_dialog", false)) {
            String country = Locale.getDefault().getISO3Country();
            if (country != null && (country.equalsIgnoreCase("chn"))) {
                prefs.edit().putBoolean("shown_rating_dialog", true).commit();
                return;
            }
            try {
                new MaterialDialog.Builder(MainActivity.this)
                        .title(R.string.rate)
                        .content(R.string.rate_desc)
                        .positiveText(R.string.sure)
                        .neutralText(R.string.later)
                        .negativeText(R.string.no_thanks)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
                                        .edit().putBoolean("shown_rating_dialog", true).commit();
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW)
                                            .setData(Uri.parse("market://details?id=com.afollestad.cabinet")));
                                } catch (ActivityNotFoundException e) {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW)
                                                .setData(Uri.parse("https://play.google.com/store/apps/details?id=com.afollestad.cabinet")));
                                    } catch (ActivityNotFoundException e2) {
                                        Toast.makeText(MainActivity.this, e2.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
                                        .edit().putBoolean("shown_rating_dialog", true).commit();
                            }
                        }).show();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    protected void processIntent(Intent intent, Bundle savedInstanceState) {
        boolean actionView = false;
        if (intent.getAction() != null) {
            if (intent.getAction().equals(Intent.ACTION_GET_CONTENT) ||
                    intent.getAction().equals(Intent.ACTION_PICK) ||
                    intent.getAction().equals(RingtoneManager.ACTION_RINGTONE_PICKER)) {
                pickMode = true;
                setCab(new PickerCab(
                        intent.getBooleanExtra("extract_mode", false),
                        intent.getAction().equals(RingtoneManager.ACTION_RINGTONE_PICKER))
                        .setActivity(this).start());
                switchDirectory((File) null);
            } else if (intent.getAction().equals(Intent.ACTION_VIEW) &&
                    intent.getData() != null) {
                actionView = true;
                File fi = File.fromUri(this, intent.getData(), false);
                if (fi == null) {
                    Toast.makeText(this, "Unsupported URI: " + intent.getData(), Toast.LENGTH_SHORT).show();
                    switchDirectory((File) null);
                    return;
                }
                switchDirectory(fi);
            } else if (intent.getAction().equals(Intent.ACTION_SEND)) {
                Uri sendUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (sendUri == null) sendUri = intent.getData();
                if (sendUri == null) {
                    Toast.makeText(this, "No URIs to share", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                pickMode = true;
                setCab(new PickerCab(sendUri).setActivity(this).start());
                switchDirectory((File) null);
            } else if (intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
                ArrayList<Uri> sendUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (sendUris == null) {
                    Toast.makeText(this, "No URIs to share", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                pickMode = true;
                setCab(new PickerCab(sendUris).setActivity(this).start());
                switchDirectory((File) null);
            } else if (intent.getAction().equals(PluginConstants.AUTHENTICATED_ACTION) ||
                    intent.getAction().equals(PluginConstants.ACCOUNT_ADDED_ACTION) ||
                    intent.getAction().equals(PluginConstants.SETTINGS_ACTION)) {
                final String pkg = intent.getStringExtra(PluginConstants.EXTRA_PACKAGE);
                final String accountId = intent.getStringExtra(PluginConstants.ACCOUNT_ID_EXTRA);
                final String initialPath = intent.getStringExtra(PluginConstants.INITIAL_PATH_EXTRA);
                final String accountDisplay = intent.getStringExtra(PluginConstants.ACCOUNT_DISPLAY_EXTRA);
                if (intent.getAction().equals(PluginConstants.SETTINGS_ACTION)) {
                    PluginDataProvider.updateAccount(this, pkg, accountId, accountDisplay, initialPath);
                    // Navigation drawer adapter handles updating after this
                } else {
                    PluginDataProvider.addAccount(this, pkg, accountId, accountDisplay, initialPath, true);
                    viewPlugin(intent);
                }
            }
        }
        if (PluginConstants.VIEW_PLUGIN_ACTION.equals(intent.getAction())) {
            viewPlugin(intent);
        } else if (intent.hasExtra(PluginConstants.SHORTCUT_PATH) &&
                !intent.getStringExtra(PluginConstants.SHORTCUT_PATH).isEmpty()) {
            File dest = File.fromUri(this, Uri.parse(intent.getStringExtra(PluginConstants.SHORTCUT_PATH)), true);
            switchDirectory(dest);
        } else if (!actionView && !pickMode && savedInstanceState == null) {
            if (!checkChangelog())
                checkRating();
            switchDirectory((File) null); // show initial directory
        }
    }

    private void viewPlugin(Intent intent) {
        final String pkg = intent.getStringExtra(PluginConstants.EXTRA_PACKAGE);
        NavigationDrawerFragment frag = (NavigationDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
        if (frag != null) frag.pluginAuthenticated(pkg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Fragment changelog = getFragmentManager().findFragmentByTag("CHANGELOG_DIALOG");
        if (changelog != null)
            ((DialogFragment) changelog).dismiss();
        if (mOuterFrame != null)
            mOuterFrame.clearAnimation();
        if (mTabletExpander != null) {
            mTabletExpander.cancel();
            mTabletExpander = null;
        }
    }

    @SuppressLint("CommitPrefEdits")
    boolean checkChangelog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 69);
            return true;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final int currentVersion;
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersion = pInfo.versionCode;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (currentVersion != prefs.getInt("changelog_version", -1)) {
            ChangelogDialog.create(this).show(getFragmentManager(), "CHANGELOG_DIALOG");
            return true;
        }
        return false;
    }

    public void pluginUninstalled() {
        NavigationDrawerFragment frag = (NavigationDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
        if (frag != null) frag.pluginUninstalled();
    }

    public final void notifyDeleted(String uri, boolean dir, boolean archive) {
        if (mCrumbs.trim(uri, dir, archive)) {
            // returns true if active folder was inside trimmed folders
            switchDirectory((File) null);
        }
    }

    public final void notifyDeleted(File file) {
        notifyDeleted(file.getUri().toString(), file.isDirectory(), file.isViewableArchive(this));
        if (BookmarkProvider.removeItem(this, new BookmarkProvider.Item(file))) {
            NavigationDrawerFragment frag = (NavigationDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
            if (frag != null) frag.notifyDeleted(file);
        }
    }

    public void reloadNavDrawer(boolean open) {
        NavigationDrawerFragment frag = (NavigationDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
        if (frag != null) frag.reload(open);
    }

    public void reloadActiveFolder() {
        ContentFragment frag = (ContentFragment) getFragmentManager().findFragmentById(R.id.container);
        if (frag != null) frag.reload(false);
    }

    /**
     * Used by the directory fragment when a plugin displays its authenticator.
     * Prevents an infinite loop of going back to the authenticator if authentication is cancelled
     * (which causes the user to return to Cabinet, thus refreshing and causing it to open again).
     */
    public void hidePluginFolder(String pkg, String accountId, boolean force) {
        if (force) {
            Utils.setLastOpenFolder(this, null);
        } else {
            File lastOpen = Utils.getLastOpenFolder(this);
            if (lastOpen != null && lastOpen.getPluginPackage() != null) {
                // If the a folder from the plugin package is visible, navigate away from it
                PluginFileImpl pf = (PluginFileImpl) lastOpen;
                if (pf.getPluginPackage() != null && !pf.getPluginPackage().equals(pkg)) {
                    // Hiding not needed, last folder is for another plugin
                    return;
                } else if (accountId != null && !accountId.equals(pf.getPluginAccount())) {
                    // Hiding not needed, folder account ID doesn't match target account ID
                    return;
                }
                Utils.setLastOpenFolder(this, null);
            }
        }
        switchDirectory((File) null);
    }

    public boolean switchDirectory(BookmarkProvider.Item to) {
        ContentFragment frag = (ContentFragment) getFragmentManager().findFragmentById(R.id.container);
        File file = to.asFile(this);
        if (!file.equals(frag.getDirectory())) {
            mCrumbs.clearHistory();
            switchDirectory(file);
            return true;
        }
        return false;
    }

    /**
     * Switch to the directory. If null, switch to initial directory.
     */
    @Override
    public void switchDirectory(File to) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            return;
        }

        boolean wasNull = (to == null);
        if (wasNull) {
            // Initial directory
            to = Utils.getLastOpenFolder(this);
            if (to == null)
                to = new LocalFile(Uri.fromFile(Environment.getExternalStorageDirectory()));
        }

        BreadCrumbLayout.Crumb crumb = new BreadCrumbLayout.Crumb(to);
        switchDirectory(crumb, wasNull, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            new MaterialDialog.Builder(this)
                    .title(R.string.permission_needed)
                    .content(R.string.permission_needed_desc)
                    .cancelable(false)
                    .positiveText(android.R.string.ok)
                    .dismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();
                        }
                    }).show();
        } else {
            if(!checkChangelog())
                checkRating();
            switchDirectory((File) null);
        }
    }

    @Override
    public void switchDirectory(BreadCrumbLayout.Crumb crumb, boolean forceRecreate, boolean addToHistory) {
        tryClearQuery();
        BackgroundThread.reset();

        if (forceRecreate) {
            // Rebuild artificial history, most likely first time load
            mCrumbs.clearHistory();
            File fi = crumb.getFile();
            while (fi != null) {
                mCrumbs.addHistory(new BreadCrumbLayout.Crumb(fi));
                if (BreadCrumbLayout.isStorageOrSd(fi))
                    break;
                fi = fi.getParent();
            }
            mCrumbs.reverseHistory();
        } else if (addToHistory) {
            mCrumbs.addHistory(crumb);
        }
        mCrumbs.setActiveOrAdd(crumb, forceRecreate);

        final File to = crumb.getFile();
        Fragment frag = getFragmentManager().findFragmentById(R.id.container);

        if (frag == null ||
                frag instanceof ArchiveFragment && !to.isArchiveOrInArchive(this) ||
                frag instanceof DirectoryFragment && (to.isArchiveOrInArchive(this) || to.isAndroidPackage(this))) {
            if (to.isArchiveOrInArchive(this) || to.isAndroidPackage(this)) {
                frag = ArchiveFragment.newInstance(to);
            } else {
                frag = DirectoryFragment.newInstance(to);
                ((DirectoryFragment) frag).mQuery = crumb.getQuery();
            }
            try {
                getFragmentManager().beginTransaction().replace(R.id.container, frag).commit();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        } else if (!((ContentFragment) frag).getDirectory().equals(to)) {
            ((ContentFragment) frag).setDirectory(to);
        }
    }

    public final void setStatus(int message, String replacement) {
        TextView status = (TextView) findViewById(R.id.status);
        if (message == 0) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(getString(message, replacement));
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK &&
                (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == KeyEvent.FLAG_LONG_PRESS) {
            finish();
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (mCab != null && mCab.isActive()) {
                onBackPressed();
                return true;
            }
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            if (mToolbar != null) {
                mToolbar.showOverflowMenu();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBillingInitialized() {
    }

    /* Donation stuff via in app billing */

    @Override
    public void onProductPurchased(String productId, TransactionDetails transactionDetails) {
        mBP.consumePurchase(productId);
        Toast.makeText(this, R.string.thank_you, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        if (errorCode != 110) {
            Toast.makeText(this, "Billing error: code = " + errorCode + ", error: " +
                    (error != null ? error.getMessage() : "?"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPurchaseHistoryRestored() {
        /*
         * Called then purchase history was restored and the list of all owned PRODUCT ID's
         * was loaded from Google Play
         */
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UNINSTALL_PLUGIN_REQUEST) {
            if (data != null && data.getIntExtra("android.intent.extra.INSTALL_RESULT", 0) == 1)
                pluginUninstalled();
        } else if (requestCode == MainCab.CHOOSE_DESTINATION_REQUEST) {
            if (mCab != null && mCab instanceof MainCab) {
                MainCab mainCab = (MainCab) mCab;
                mainCab.setActivity(this);
                mainCab.onActivityResult(requestCode, resultCode, data);
            }
        } else {
            try {
                if (mBP == null || !mBP.handleActivityResult(requestCode, resultCode, data))
                    super.onActivityResult(requestCode, resultCode, data);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void donate(int index) {
        final String[] ids = getResources().getStringArray(R.array.donation_ids);
        mBP.purchase(this, ids[index]);
    }

    @Override
    public void onDestroy() {
        if (mBP != null) mBP.release();
        BackgroundThread.getHandler().removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        invalidateDarkOrLightMenuItems(this, menu);
        invalidateMenuTint();
        return super.onPrepareOptionsMenu(menu);
    }

    public void toggleStaticDrawer() {
        if (mTabletExpander != null)
            mTabletExpander.cancel();
        int navDrawerMargin = getResources().getDimensionPixelSize(R.dimen.nav_drawer_margin);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int navDrawerWidthLimit = getResources().getDimensionPixelSize(R.dimen.nav_drawer_width_limit);
        int navDrawerWidth = displayMetrics.widthPixels - navDrawerMargin;
        if (navDrawerWidth > navDrawerWidthLimit)
            navDrawerWidth = navDrawerWidthLimit;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean expanded = !prefs.getBoolean("static_drawer_expanded", true);
        prefs.edit().putBoolean("static_drawer_expanded", expanded).apply();

        final int startValue = expanded ? 0 : navDrawerWidth;
        final int endValue = expanded ? navDrawerWidth : 0;
        View drawerFrame = findViewById(R.id.nav_drawer_frame);
        mTabletExpander = ValueAnimator.ofObject(new WidthEvaluator(drawerFrame), startValue, endValue);
        mTabletExpander.setInterpolator(new DecelerateInterpolator());
        mTabletExpander.setDuration(150);
        mTabletExpander.start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        invalidateMenuTint();
        if (mDrawerLayout == null && item.getItemId() == android.R.id.home) {
            toggleStaticDrawer();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void invalidateMenuTint() {
        mToolbar.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Field f1 = Toolbar.class.getDeclaredField("mMenuView");
                    f1.setAccessible(true);
                    ActionMenuView actionMenuView = (ActionMenuView) f1.get(mToolbar);

                    Field f2 = ActionMenuView.class.getDeclaredField("mPresenter");
                    f2.setAccessible(true);

                    //Actually ActionMenuPresenter
                    BaseMenuPresenter presenter = (BaseMenuPresenter) f2.get(actionMenuView);

                    Field f3 = presenter.getClass().getDeclaredField("mOverflowPopup");
                    f3.setAccessible(true);
                    MenuPopupHelper overflowMenuPopupHelper = (MenuPopupHelper) f3.get(presenter);
                    setTintForMenuPopupHelper(overflowMenuPopupHelper);

                    Field f4 = presenter.getClass().getDeclaredField("mActionButtonPopup");
                    f4.setAccessible(true);
                    MenuPopupHelper subMenuPopupHelper = (MenuPopupHelper) f4.get(presenter);
                    setTintForMenuPopupHelper(subMenuPopupHelper);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void invalidateDarkOrLightMenuItems(Context context, final Menu menu) {
        if (menu != null && menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Field field = menu.getClass().getDeclaredField("mOptionalIconsVisible");
                field.setAccessible(true);
                field.setBoolean(menu, true);

                final boolean darkMode = ThemeUtils.isDarkMode(context);
                final int textColorPrimary = Utils.resolveColor(context, android.R.attr.textColorPrimary);

                mToolbar.post(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < menu.size(); i++) {
                            MenuItemImpl item = (MenuItemImpl) menu.getItem(i);
                            int color = darkMode || item.isActionButton() ? Color.WHITE : textColorPrimary;
                            if (item.getIcon() != null) {
                                item.getIcon().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                            }
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setTintForMenuPopupHelper(MenuPopupHelper menuPopupHelper) {
        if (menuPopupHelper != null) {
            final ListView listView = menuPopupHelper.getPopup().getListView();
            listView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    try {
                        Field checkboxField = ListMenuItemView.class.getDeclaredField("mCheckBox");
                        checkboxField.setAccessible(true);
                        Field radioButtonField = ListMenuItemView.class.getDeclaredField("mRadioButton");
                        radioButtonField.setAccessible(true);

                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View v = listView.getChildAt(i);
                            if (!(v instanceof ListMenuItemView)) continue;
                            ListMenuItemView iv = (ListMenuItemView) v;


                            CheckBox check = (CheckBox) checkboxField.get(iv);
                            if (check != null) {
                                MDTintHelper.setTint(check, ThemeSingleton.get().widgetColor);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    check.setBackground(null);
                                }
                            }


                            RadioButton radioButton = (RadioButton) radioButtonField.get(iv);
                            if (radioButton != null) {
                                MDTintHelper.setTint(radioButton, ThemeSingleton.get().widgetColor);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    radioButton.setBackground(null);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        listView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        //noinspection deprecation
                        listView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        }
    }

    public interface FabListener {
        void onFabPressed(int action);
    }

    private class WidthEvaluator extends IntEvaluator {

        private final View v;

        public WidthEvaluator(View v) {
            this.v = v;
        }

        @NonNull
        @Override
        public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
            int num = super.evaluate(fraction, startValue, endValue);
            ViewGroup.LayoutParams params = v.getLayoutParams();
            params.width = num;
            v.setLayoutParams(params);
            return num;
        }
    }
}