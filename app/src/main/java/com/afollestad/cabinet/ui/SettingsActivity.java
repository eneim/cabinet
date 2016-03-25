package com.afollestad.cabinet.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.adapters.SettingsCategoryAdapter;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.views.ColorChooserPreference;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.afollestad.materialdialogs.util.DialogUtils;

/**
 * @author Aidan Follestad (afollestad)
 */
@SuppressWarnings("ConstantConditions")
@SuppressLint("MissingSuperCall")
public class SettingsActivity extends ThemableActivity implements ColorChooserDialog.ColorCallback {

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog dialog, @ColorInt int selectedColor) {
        if (dialog.getTitle() == R.string.thumbnail_color) {
            getThemeUtils().thumbnailColor(selectedColor);
        } else if (dialog.isAccentMode()) {
            getThemeUtils().accentColor(selectedColor);
        } else {
            getThemeUtils().primaryColor(selectedColor);
        }
        recreate();
    }

    public abstract static class BasePreferenceFragment extends PreferenceFragment {

        @StringRes
        public abstract int getTitle();

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            view.findViewById(android.R.id.list).setPadding(0, 0, 0, 0);
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() != null && getActivity().findViewById(R.id.categories_content) == null)
                getActivity().setTitle(getTitle());
        }
    }

    public static class BehaviorFragment extends BasePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_behavior);
        }

        @Override
        public int getTitle() {
            return R.string.behavior;
        }
    }

    public static class NavDrawerFragment extends BasePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_navdrawer);
        }

        @Override
        public int getTitle() {
            return R.string.nav_drawer;
        }
    }

    public static class ThemingFragment extends BasePreferenceFragment {

        private Preference invalidateBaseTheme() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            Preference baseTheme = findPreference("base_theme");
            String[] themes = getResources().getStringArray(R.array.base_themes);
            if (prefs.getBoolean("true_black", false))
                baseTheme.setSummary(themes[2]);
            else if (prefs.getBoolean("dark_mode", false))
                baseTheme.setSummary(themes[1]);
            else
                baseTheme.setSummary(themes[0]);
            return baseTheme;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_theming);

            Preference baseTheme = invalidateBaseTheme();
            baseTheme.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    int preselect = 0;
                    if (prefs.getBoolean("true_black", false))
                        preselect = 2;
                    else if (prefs.getBoolean("dark_mode", false))
                        preselect = 1;
                    new MaterialDialog.Builder(getActivity())
                            .title(R.string.base_theme)
                            .items(R.array.base_themes)
                            .itemsCallbackSingleChoice(preselect, new MaterialDialog.ListCallbackSingleChoice() {
                                @SuppressLint("CommitPrefEdits")
                                @Override
                                public boolean onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                                    if (getActivity() == null) return false;
                                    SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
                                    switch (i) {
                                        default:
                                            prefs.remove("dark_mode").remove("true_black");
                                            break;
                                        case 1:
                                            prefs.remove("true_black").putBoolean("dark_mode", true);
                                            break;
                                        case 2:
                                            prefs.putBoolean("dark_mode", true).putBoolean("true_black", true);
                                            break;
                                    }
                                    prefs.commit();
                                    getActivity().recreate();
                                    return true;
                                }
                            }).show();
                    return false;
                }
            });

            Preference coloredNav = findPreference("colored_navbar");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                coloredNav.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (getActivity() != null) {
                            if ((Boolean) newValue) {
                                ThemeUtils themeUtils = ((ThemableActivity) getActivity()).getThemeUtils();
                                if (getActivity().getWindow() != null)
                                    getActivity().getWindow().setNavigationBarColor(themeUtils.primaryColorDark());
                            } else if (getActivity().getWindow() != null)
                                getActivity().getWindow().setNavigationBarColor(Color.BLACK);
                        }
                        return true;
                    }
                });
            } else {
                coloredNav.setEnabled(false);
                coloredNav.setSummary(R.string.only_available_api21);
            }


            ThemeUtils themeUtils = ((ThemableActivity) getActivity()).getThemeUtils();
            ColorChooserPreference primaryColor = (ColorChooserPreference) findPreference("primary_color");
            primaryColor.setColor(themeUtils.primaryColor(), Utils.resolveColor(getActivity(), R.attr.colorAccent));
            primaryColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ThemeUtils themeUtils = ((ThemableActivity) getActivity()).getThemeUtils();
                    new ColorChooserDialog.Builder((SettingsActivity) getActivity(), preference.getTitleRes())
                            .preselect(themeUtils.primaryColor())
                            .doneButton(R.string.done)
                            .backButton(R.string.back)
                            .cancelButton(android.R.string.cancel)
                            .show();
                    return true;
                }
            });


            ColorChooserPreference accentColor = (ColorChooserPreference) findPreference("accent_color");
            accentColor.setColor(themeUtils.accentColor(), Utils.resolveColor(getActivity(), R.attr.colorAccent));
            accentColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ThemeUtils themeUtils = ((ThemableActivity) getActivity()).getThemeUtils();
                    new ColorChooserDialog.Builder((SettingsActivity) getActivity(), preference.getTitleRes())
                            .preselect(themeUtils.accentColor())
                            .accentMode(true)
                            .doneButton(R.string.done)
                            .backButton(R.string.back)
                            .cancelButton(android.R.string.cancel)
                            .show();
                    return true;
                }
            });

            ColorChooserPreference thumbnailColor = (ColorChooserPreference) findPreference("thumbnail_color");
            thumbnailColor.setColor(themeUtils.thumbnailColor(), Utils.resolveColor(getActivity(), R.attr.colorAccent));
            thumbnailColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ThemeUtils themeUtils = ((ThemableActivity) getActivity()).getThemeUtils();
                    new ColorChooserDialog.Builder((SettingsActivity) getActivity(), preference.getTitleRes())
                            .preselect(themeUtils.thumbnailColor())
                            .show();
                    return true;
                }
            });
        }

        @Override
        public int getTitle() {
            return R.string.theming;
        }
    }

    public static class CategoriesFragment extends Fragment implements SettingsCategoryAdapter.ClickListener {

        private SettingsCategoryAdapter mAdapter;

        public static CategoriesFragment create(boolean tablet, int activated) {
            CategoriesFragment frag = new CategoriesFragment();
            Bundle args = new Bundle();
            args.putBoolean("tablet", tablet);
            args.putInt("activated", activated);
            frag.setArguments(args);
            return frag;
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_recyclerview_plain, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            final boolean tablet = getArguments().getBoolean("tablet");
            if (tablet)
                view.setBackgroundColor(DialogUtils.resolveColor(getActivity(), R.attr.drawer_background));
            mAdapter = new SettingsCategoryAdapter((ThemableActivity) getActivity(),
                    tablet, getArguments().getInt("activated"), this);
            RecyclerView list = (RecyclerView) view.findViewById(android.R.id.list);
            list.setLayoutManager(new LinearLayoutManager(getActivity()));
            list.setAdapter(mAdapter);
        }

        @Override
        public void onClick(int index) {
            getArguments().putInt("activated", index);
            SettingsActivity act = (SettingsActivity) getActivity();
            if (act != null)
                act.switchPage(index);
        }
    }

    private int mLastPage;

    private void switchPage(int index) {
        mLastPage = index;
        Fragment frag;
        switch (index) {
            default:
                frag = new BehaviorFragment();
                break;
            case 1:
                frag = new NavDrawerFragment();
                break;
            case 2:
                frag = new ThemingFragment();
                break;
        }

        View categoriesContent = findViewById(R.id.categories_content);
        FragmentTransaction trans = getFragmentManager().beginTransaction();
        if (categoriesContent == null) {
            // only use animation on phones
            trans.setCustomAnimations(R.animator.settings_frag_enter, 0, R.animator.settings_frag_exit, 0);
        }
        trans.replace(R.id.settings_content, frag);
        if (categoriesContent == null)
            trans.addToBackStack(null);
        trans.commit();
    }

    @Override
    public void setTitle(@StringRes int titleId) {
        getSupportActionBar().setTitle(titleId);
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
            if (findViewById(R.id.categories_content) == null)
                setTitle(R.string.settings);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("last_page", mLastPage);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preference_activity_custom);

        Toolbar mToolbar = (Toolbar) findViewById(R.id.appbar_toolbar);
        mToolbar.setBackgroundColor(getThemeUtils().primaryColor());

        setSupportActionBar(mToolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        boolean isTablet = false;
        View categoriesFrame = findViewById(R.id.categories_content);
        if (categoriesFrame != null) {
            isTablet = true;
            int navDrawerMargin = getResources().getDimensionPixelSize(R.dimen.nav_drawer_margin);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int navDrawerWidthLimit = getResources().getDimensionPixelSize(R.dimen.nav_drawer_width_limit);
            int navDrawerWidth = displayMetrics.widthPixels - navDrawerMargin;
            if (navDrawerWidth > navDrawerWidthLimit)
                navDrawerWidth = navDrawerWidthLimit;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) categoriesFrame.getLayoutParams();
            params.width = navDrawerWidth;
        }

        FragmentManager fm = getFragmentManager();
        if (savedInstanceState == null) {
            FragmentTransaction trans = fm.beginTransaction();
            if (isTablet) {
                // Tablet
                trans.replace(R.id.categories_content, CategoriesFragment.create(true, 0));
                switchPage(0);
            } else {
                // Phone
                trans.replace(R.id.settings_content, CategoriesFragment.create(false, -1));
            }
            trans.commit();
        } else {
            mLastPage = savedInstanceState.getInt("last_page", 0);
            if (isTablet) {
                Fragment content = fm.findFragmentById(R.id.settings_content);
                if (content instanceof CategoriesFragment) {
                    getFragmentManager().beginTransaction()
                            .remove(content)
                            .replace(R.id.categories_content, CategoriesFragment.create(true, mLastPage))
                            .commit();
                    switchPage(mLastPage);
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}