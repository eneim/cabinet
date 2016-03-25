package com.afollestad.cabinet.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.adapters.NavigationDrawerAdapter;
import com.afollestad.cabinet.bookmarks.BookmarkProvider;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.base.LeakDetectFragment;
import com.afollestad.cabinet.plugins.PluginDataProvider;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.ui.SettingsActivity;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;

public class NavigationDrawerFragment extends LeakDetectFragment {

    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
    private static final int ADD_DOCUMENT_TREE_REQUEST = 4202;

    private DrawerLayout mDrawerLayout;
    private RecyclerView mRecyclerView;
    private NavigationDrawerAdapter mAdapter;
    private ActionBarDrawerToggle mDrawerToggle;

    private int mCurrentSelectedPosition = 1;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload(false);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.onResume();
        reload(false);
        try {
            IntentFilter filter = new IntentFilter();
            filter.addDataScheme("package");
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            getActivity().registerReceiver(mReceiver, filter);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            getActivity().unregisterReceiver(mReceiver);
        } catch (Throwable ignored) {
        }
        mAdapter.onPause();
    }

    public void notifyDeleted(File file) {
        if (file.isDirectory())
            mAdapter.remove(file);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    private void showEditDialog(final BookmarkProvider.Item item, final int index) {
        final MainActivity act = (MainActivity) getActivity();
        if (act == null) return;
        new MaterialDialog.Builder(act)
                .title(item.getDisplay(act, false))
                .items(R.array.bookmark_edit_options)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                        if (getActivity() == null) return;
                        switch (i) {
                            default:
                                Utils.showInputDialog(act, R.string.rename, R.string.nickname, item.nickname, new Utils.InputCallback() {
                                    @Override
                                    public void onInput(String input) {
                                        if (getActivity() == null) return;
                                        item.nickname = input;
                                        BookmarkProvider.update(getActivity(), item);
                                        mAdapter.reload();
                                    }
                                });
                                break;
                            case 1:
                                Utils.showConfirmDialog(act, R.string.remove_bookmark,
                                        R.string.remove_bookmark_desc,
                                        item.getDisplay(act, false),
                                        new Utils.ClickListener() {
                                            @TargetApi(Build.VERSION_CODES.KITKAT)
                                            @Override
                                            public void onPositive(int which, View view) {
                                                if (getActivity() == null) return;
                                                if (item.isRootDocumentTree(getActivity())) {
                                                    // Revoke permission to access
                                                    try {
                                                        final Uri uri = Uri.parse(item.uri);
                                                        getActivity().getContentResolver()
                                                                .releasePersistableUriPermission(uri,
                                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                                    } catch (SecurityException ignored) {
                                                    }
                                                }
                                                if (BookmarkProvider.removeItem(getActivity(), item)) {
                                                    mAdapter.reload();
                                                    if (mAdapter.getCheckedPos() > index) {
                                                        // Items get shifted up, shift checked position
                                                        mAdapter.setCheckedPos(mAdapter.getCheckedPos() - 1);
                                                    }
                                                }
                                            }
                                        }
                                );
                                break;
                        }
                    }
                }).show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_drawer, container, false);
        mRecyclerView = (RecyclerView) v.findViewById(android.R.id.list);
        mRecyclerView.setClipToPadding(false);
        mAdapter = new NavigationDrawerAdapter((MainActivity) getActivity(), new NavigationDrawerAdapter.ClickListener() {
            @Override
            public void onClick(int index) {
                selectItem(index);
            }

            @Override
            public boolean onLongClick(final int index) {
                BookmarkProvider.Item item = mAdapter.getItem(index);
                showEditDialog(item, index);
                return false;
            }

            @Override
            public void onClickSettings() {
                final MainActivity act = (MainActivity) getActivity();
                if (act != null) {
                    if (act.getDrawerLayout() != null) {
                        act.getDrawerLayout().closeDrawers();
                        act.getDrawerLayout().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && !isDetached())
                                    startActivity(new Intent(act, SettingsActivity.class));
                            }
                        }, 200);
                    } else {
                        startActivity(new Intent(act, SettingsActivity.class));
                    }
                }
            }

            @Override
            public void onClickHelpAndFeedback() {
                final MainActivity act = (MainActivity) getActivity();
                if (act != null) {
                    if (act.getDrawerLayout() != null) {
                        act.getDrawerLayout().closeDrawers();
                        act.getDrawerLayout().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    new HelpAndFeedbackDialog().show(act.getFragmentManager(), "helpandfeedback");
                                } catch (Throwable ignored) {
                                }
                            }
                        }, 200);
                    } else {
                        new HelpAndFeedbackDialog().show(act.getFragmentManager(), "helpandfeedback");
                    }
                }
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClickAddExternal() {
                final MainActivity act = (MainActivity) getActivity();
                if (act != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        startActivityForResult(intent, ADD_DOCUMENT_TREE_REQUEST);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(act);
                        if (!prefs.getBoolean("shown_external_storage_note", false)) {
                            Toast.makeText(act, R.string.external_storage_hint, Toast.LENGTH_LONG).show();
                            prefs.edit().putBoolean("shown_external_storage_note", true).apply();
                        }
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getActivity(), "Your device does not support Lollipop's external storage APIs. Tap and hold the shortcut to hide it.", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onClickDonate() {
                donate();
            }
        });
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setCheckedPos(mCurrentSelectedPosition);
        return v;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        final MainActivity act = (MainActivity) getActivity();
        if (requestCode == ADD_DOCUMENT_TREE_REQUEST && resultCode == Activity.RESULT_OK && act != null) {
            final ContentResolver cr = act.getContentResolver();
            final Uri treeUri = resultData.getData();
            final DocumentFile pickedDir = DocumentFile.fromTreeUri(act, treeUri);
            cr.takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            BookmarkProvider.addItem(act, new BookmarkProvider.Item(pickedDir, getString(R.string.external_storage)));
            reload(false);
        }
    }

    private void donate() {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.support_dev)
                .items(R.array.donation_options)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                        ((MainActivity) getActivity()).donate(i);
                    }
                }).show();
    }

    public void setUp(DrawerLayout drawerLayout, Toolbar actionBarToolbar) {
        mDrawerLayout = drawerLayout;
        if (mDrawerLayout != null) {
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);

            //Call super.onDrawerOpened() and super.onDrawerClosed() to update content descriptions.
            //Call super.onDrawerSlide(..,0f) to trick it into thinking the drawer is closed.
            mDrawerToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout, actionBarToolbar,
                    R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    super.onDrawerSlide(drawerView, 0f);
                }

                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    super.onDrawerSlide(drawerView, 0f);
                }
            };
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerLayout.post(new Runnable() {
                @Override
                public void run() {
                    mDrawerToggle.syncState();
                    mDrawerToggle.onDrawerSlide(mDrawerLayout, 0f);
                }
            });
            mDrawerLayout.setDrawerListener(mDrawerToggle);
        }
    }

    private void selectItem(int position) {
        if (position < 0) position = 1;
        mCurrentSelectedPosition = position;
        if (mRecyclerView != null) {
            mAdapter.setCheckedPos(position);
        }
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            BookmarkProvider.Item item = mAdapter.getItem(position);
            if (!act.switchDirectory(item) && mDrawerLayout != null)
                mDrawerLayout.closeDrawers();
        }
    }

    public void selectFile(File file) {
        mCurrentSelectedPosition = mAdapter.setCheckedFile(file);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
    }

    private ActionBar getActionBar() {
        return ((AppCompatActivity) getActivity()).getSupportActionBar();
    }

    public void reload(boolean open) {
        Activity act = getActivity();
        if (act != null && mAdapter != null) {
            mAdapter.reload();
            if (open && mDrawerLayout != null)
                mDrawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void invalidatePadding(int insetsHeight) {
        if (getView() == null || getActivity() == null) return;
        final View v = getView();
        int eightDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        v.setPadding(v.getPaddingLeft(), eightDp + insetsHeight, v.getPaddingRight(), v.getPaddingBottom());
    }

    public void pluginAuthenticated(@NonNull String pkg) {
        final PluginActivity context = (PluginActivity) getActivity();
        int index = mAdapter.findPluginIndex(pkg);
        if (index == -1)
            throw new IllegalStateException("Plugin " + pkg + " not found in nav drawer.");

        BookmarkProvider.Item i = mAdapter.getItem(index);
        Uri currentUri = PluginDataProvider.getUri(context, pkg, true);
        if (currentUri == null)
            throw new IllegalStateException("No current URI found for plugin " + pkg);

        i.uri = currentUri.toString();
        i.nickname = PluginDataProvider.getAccountDisplay(context, pkg,
                PluginFileImpl.getPluginAccount(currentUri, i.getPlugin(context)));
        mAdapter.set(index, i);
        mAdapter.notifyItemChanged(index);
        selectItem(index);
    }

    public void pluginUninstalled() {
        mAdapter.pluginUninstalled();
    }
}