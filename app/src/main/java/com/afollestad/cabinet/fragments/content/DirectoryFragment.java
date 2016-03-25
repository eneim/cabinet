package com.afollestad.cabinet.fragments.content;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.adapters.FileAdapter;
import com.afollestad.cabinet.bookmarks.BookmarkProvider;
import com.afollestad.cabinet.cab.CopyCab;
import com.afollestad.cabinet.cab.CutCab;
import com.afollestad.cabinet.cab.MainCab;
import com.afollestad.cabinet.cab.PickerCab;
import com.afollestad.cabinet.cab.base.BaseCab;
import com.afollestad.cabinet.cab.base.BaseFileCab;
import com.afollestad.cabinet.comparators.AlphabeticalComparator;
import com.afollestad.cabinet.comparators.ExtensionComparator;
import com.afollestad.cabinet.comparators.HighLowSizeComparator;
import com.afollestad.cabinet.comparators.LastModifiedComparator;
import com.afollestad.cabinet.comparators.LowHighSizeComparator;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.base.FileFilter;
import com.afollestad.cabinet.fragments.DetailsDialog;
import com.afollestad.cabinet.fragments.NavigationDrawerFragment;
import com.afollestad.cabinet.plugins.IPluginService;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginDataProvider;
import com.afollestad.cabinet.plugins.PluginErrorResult;
import com.afollestad.cabinet.plugins.base.PluginConstants;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.views.BreadCrumbLayout;
import com.afollestad.cabinet.zip.Unzipper;
import com.afollestad.cabinet.zip.Zipper;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stericson.RootShell.exceptions.RootDeniedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class DirectoryFragment extends ContentFragment
        implements FileAdapter.ItemClickListener, FileAdapter.MenuClickListener, MainActivity.FabListener {

    public static final String STATE_QUERY = "query";
    private final static int CHOOSE_DESTINATION_REQUEST = 7001;
    private final static int NEW_CONNECTION_REQUEST = 5001;
    public String mQuery;
    private final transient BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null &&
                    intent.getAction().equals(PluginConstants.EXIT_ACTION)) {
                final String pkg = intent.getStringExtra(PluginConstants.EXTRA_PACKAGE);
                MainActivity act = (MainActivity) getActivity();
                if (act == null) return;
                act.hidePluginFolder(pkg, null, false);
            }
        }
    };
    public boolean showHidden;
    public int sorter;
    public String filter;
    private Thread mSearchThread;
    private Plugin.Callback mConnectCallback;

    public static DirectoryFragment newInstance(File directory) {
        DirectoryFragment frag = new DirectoryFragment();
        frag.mDirectory = directory;
        Bundle b = new Bundle();
        b.putSerializable(STATE_PATH, directory);
        frag.setArguments(b);
        return frag;
    }

    public static Comparator<File> getComparator(Context context, int sorter) {
        Comparator<File> comparator;
        switch (sorter) {
            default:
                comparator = new AlphabeticalComparator(context);
                break;
            case 2:
                comparator = new ExtensionComparator(context);
                break;
            case 3:
                comparator = new LowHighSizeComparator(context);
                break;
            case 4:
                comparator = new HighLowSizeComparator(context);
                break;
            case 5:
                comparator = new LastModifiedComparator(context);
                break;
        }
        return comparator;
    }

    public final boolean setQuery(String query, boolean initLoad) {
        final boolean clearedQuery = mQuery != null && query == null;
        mQuery = query;

        final MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            if (!initLoad) {
                // Persist the query in the associated bread crumb
                BreadCrumbLayout crumbs = act.getCrumbs();
                BreadCrumbLayout.Crumb crumb = crumbs.findCrumb(mDirectory);
                if (crumb != null) crumb.setQuery(query);
            }
            invalidateTitle(); // update activity title based on query
            act.invalidateOptionsMenu(); // update search icon in menu
        }

        if (query != null) {
            getArguments().putString(STATE_QUERY, query);
        } else if (getArguments().containsKey(STATE_QUERY)) {
            getArguments().remove(STATE_QUERY);
        }

        return clearedQuery;
    }

    @Override
    public void setDirectory(File directory) {
        super.setDirectory(directory);
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            Utils.setLastOpenFolder(act, directory);
            Fragment frag = act.getFragmentManager().findFragmentByTag("NAV_DRAWER");
            if (frag != null)
                ((NavigationDrawerFragment) frag).selectFile(directory);
        }
    }

    protected void invalidateCabAndFab() {
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            act.toggleFab(false);
            BaseCab cab = act.getCab();
            if (cab != null) {
                if (cab instanceof BaseFileCab) {
                    mAdapter.restoreCheckedPaths(((BaseFileCab) cab).getFiles());
                    if (act.shouldAttachFab) {
                        BaseFileCab fCab = (BaseFileCab) act.getCab()
                                .setFragment(DirectoryFragment.this);
                        fCab.start();
                        act.shouldAttachFab = false;
                    }
                }
                cab.setFragment(this);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mQuery = getArguments().getString(STATE_QUERY);

        if (mQuery != null) mQuery = mQuery.trim();
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            showHidden = Utils.getShowHidden(act);
            sorter = Utils.getSorter(act);
            filter = Utils.getFilter(act);
        }
    }

    @Override
    protected void invalidateTitle() {
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            if (mQuery != null) {
                act.setTitle(Html.fromHtml(getString(R.string.search_x, mQuery)));
            } else {
                super.invalidateTitle();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        invalidateCabAndFab();
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            act.registerReceiver(mReceiver, new IntentFilter(PluginConstants.EXIT_ACTION));

            NavigationDrawerFragment navDrawer = (NavigationDrawerFragment) act.getFragmentManager().findFragmentByTag("NAV_DRAWER");
            if (navDrawer != null)
                navDrawer.selectFile(mDirectory);
            showHidden = Utils.getShowHidden(act);
            sorter = Utils.getSorter(act);
            filter = Utils.getFilter(act);

            act.setFabListener(this);
            Utils.setLastOpenFolder(act, getDirectory());

            if (mQuery == null) {
                // Load query if it wasn't already set by the Activity initially
                BreadCrumbLayout crumbs = act.getCrumbs();
                BreadCrumbLayout.Crumb crumb = crumbs.findCrumb(mDirectory);
                if (crumb != null)
                    setQuery(crumb.getQuery(), true);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSearchThread != null) mSearchThread.interrupt();
        if (mDirectory instanceof PluginFileImpl) {
            PluginFileImpl pf = (PluginFileImpl) mDirectory;
            if (pf.getPlugin() != null)
                pf.getPlugin().release();
        }
        mConnectCallback = null;

        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            act.setFabListener(null);
            try {
                getActivity().unregisterReceiver(mReceiver);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_menu, menu);
        switch (sorter) {
            default:
                menu.findItem(R.id.sortName).setChecked(true);
                break;
            case 2:
                menu.findItem(R.id.sortExtension).setChecked(true);
                break;
            case 3:
                menu.findItem(R.id.sortSizeLowHigh).setChecked(true);
                break;
            case 4:
                menu.findItem(R.id.sortSizeHighLow).setChecked(true);
                break;
            case 5:
                menu.findItem(R.id.sortLastModified).setChecked(true);
                break;
        }
        if (getActivity() != null) {
            boolean foldersFirst = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getBoolean("folders_first", true);
            menu.findItem(R.id.foldersFirstCheck).setChecked(foldersFirst);
        }

        if (filter != null) {
            if (filter.equals("archives")) {
                menu.findItem(R.id.filterArchives).setChecked(true);
            } else if (filter.contains(":")) {
                String[] splitFilter = filter.split(":");
                if (splitFilter.length > 1 && splitFilter[0].equals("mime")) {
                    switch (splitFilter[1]) {
                        case "text":
                            menu.findItem(R.id.filterText).setChecked(true);
                            break;
                        case "image":
                            menu.findItem(R.id.filterImage).setChecked(true);
                            break;
                        case "audio":
                            menu.findItem(R.id.filterAudio).setChecked(true);
                            break;
                        case "video":
                            menu.findItem(R.id.filterVideo).setChecked(true);
                            break;
                    }
                } else if (splitFilter[0].equals("ext")) {
                    menu.findItem(R.id.filterOther).setChecked(true);
                }
            }
        } else menu.findItem(R.id.filterNone).setChecked(true);

        if (getActivity() != null) {
            final int maxWidth = getResources().getInteger(R.integer.max_grid_width);
            if (maxWidth > -1) {
                if (maxWidth < 6)
                    menu.findItem(R.id.gridSizeSix).setVisible(false);
                if (maxWidth < 5)
                    menu.findItem(R.id.gridSizeFive).setVisible(false);
                if (maxWidth < 4)
                    menu.findItem(R.id.gridSizeFour).setVisible(false);
                if (maxWidth < 3)
                    menu.findItem(R.id.gridSizeThree).setVisible(false);
                if (maxWidth < 2)
                    menu.findItem(R.id.gridSizeTwo).setVisible(false);
            }
            switch (Utils.getGridSize(getActivity())) {
                default:
                    menu.findItem(R.id.gridSizeOne).setChecked(true);
                    break;
                case 2:
                    menu.findItem(R.id.gridSizeTwo).setChecked(true);
                    break;
                case 3:
                    menu.findItem(R.id.gridSizeThree).setChecked(true);
                    break;
                case 4:
                    menu.findItem(R.id.gridSizeFour).setChecked(true);
                    break;
                case 5:
                    menu.findItem(R.id.gridSizeFive).setChecked(true);
                    break;
                case 6:
                    menu.findItem(R.id.gridSizeSix).setChecked(true);
                    break;
            }
            menu.findItem(R.id.compactMode).setChecked(Utils.isCompactMode(getActivity()));
            menu.findItem(R.id.showHidden).setChecked(Utils.getShowHidden(getActivity()));
            menu.findItem(R.id.showCurrentPath).setChecked(PreferenceManager
                    .getDefaultSharedPreferences(getActivity()).getBoolean("show_breadcrumbs", true));
        }

        final boolean searchMode = mQuery != null;
        menu.findItem(R.id.goUp).setVisible(!searchMode && mDirectory.getParent() != null);

        final MenuItem search = menu.findItem(R.id.search);
        if (!searchMode) {
            SearchView searchView = (SearchView) MenuItemCompat.getActionView(search);
            try {
                searchView.setQueryHint(getString(R.string.search_files));
            } catch (IllegalStateException e) {
                searchView.setQueryHint("Search files…");
            }
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    search.collapseActionView();
                    if (getActivity() != null) {
                        // Persists query and performs search
                        setQuery(query, false);
                        mAdapter.clear();
                        reload(true);
                    }
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });
            MenuItemCompat.setOnActionExpandListener(search, new MenuItemCompat.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (getActivity() != null)
                        ((AppCompatActivity) getActivity()).supportInvalidateOptionsMenu();
                    return true;
                }
            });

        } else search.setVisible(false);
    }

    private void showNewFolderDialog(final Activity context) {
        Utils.showInputDialog(context, R.string.new_folder, R.string.untitled, null,
                new Utils.InputCallback() {
                    @Override
                    public void onInput(String newName) {
                        if (newName.isEmpty())
                            newName = getString(R.string.untitled);
                        final String fNewName = newName;
                        BackgroundThread.getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final File dir = File.getNewFile(getActivity(), mDirectory, fNewName, true, true);
                                    dir.mkdir(context);
                                    if (dir instanceof LocalFile) {
                                        ((LocalFile) dir).initFileInfo();
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            getAdapter().add(dir);
                                        }
                                    });
                                } catch (RemoteException | LocalFile.FileCreationException | TimeoutException | RootDeniedException | IOException e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Utils.showErrorDialog(getActivity(), R.string.failed_make_directory, e);
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
        );
    }

    private void showNewFileDialog(final Activity context) {
        Utils.showInputDialog(context, R.string.new_file, R.string.untitled, null,
                new Utils.InputCallback() {
                    @Override
                    public void onInput(String newName) {
                        if (newName.isEmpty())
                            newName = getString(R.string.untitled);
                        final String fNewName = newName;
                        BackgroundThread.getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final File newFile = File.getNewFile(getActivity(), mDirectory, fNewName, false, true);
                                    newFile.createFile(context);
                                    if (newFile instanceof LocalFile) {
                                        ((LocalFile) newFile).initFileInfo();
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            getAdapter().add(newFile);

                                        }
                                    });
                                } catch (final Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Utils.showErrorDialog(context, e.getMessage());
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
        );
    }

    @Override
    public void onFabPressed(int action) {
        if (getActivity() != null) {
            switch (action) {
                case 0: // File
                    showNewFileDialog(getActivity());
                    break;
                case 1: // Folder
                    showNewFolderDialog(getActivity());
                    break;
            }
        }
    }

    private Comparator<File> getComparator() {
        return getComparator(getActivity(), sorter);
    }

    void search() {
        setListShown(false);
        mSearchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<File> results = searchDir(showHidden, mDirectory);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mSearchThread.isInterrupted()) return;
                            mAdapter.set(results, true);
                            invalidateSubtitle(results);
                            setListShown(true);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    errorListing(e.getLocalizedMessage());
                }
            }
        });
        mSearchThread.start();
    }

    private List<File> searchDir(boolean includeHidden, File dir) throws Exception {
        return File.searchRecursive(dir, includeHidden, new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (mQuery.startsWith("type:")) {
                    String target = mQuery.substring(mQuery.indexOf(':') + 1);
                    setEmptyText(Html.fromHtml(getString(R.string.no_files_of_type, target)));
                    return file.getExtension().equalsIgnoreCase(target);
                }
                return file.getName().toLowerCase().contains(mQuery.toLowerCase());
            }
        });
    }

    public FileAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    protected FileAdapter newAdapter() {
        return new FileAdapter(getActivity(), this, this, mQuery != null, getComparator());
    }

    public void changeLayout() {
        View v = getView();
        if (v == null) return;
        RecyclerView recyclerView = (RecyclerView) v.findViewById(android.R.id.list);
        mLayoutManager = getNewGridLayoutManager((MainActivity) getActivity());
        recyclerView.setLayoutManager(mLayoutManager);
        mAdapter.changeLayout();
        getActivity().invalidateOptionsMenu(); // update checkbox
    }

    @Override
    public void reload(final boolean showAnimation) {
        final View v = getView();
        final MainActivity act = (MainActivity) getActivity();
        if (act == null || v == null) {
            Log.v("DirectoryFragment", "Reload cancelled. Activity null? " + (act == null) + ", view null? " + (v == null));
            return;
        }
        if (mQuery != null) {
            //noinspection ConstantConditions
            act.getSupportActionBar().setSubtitle(getString(R.string.loading));
            if (mAdapter.getItemCount() == 0)
                search();
            return;
        }

        setListShown(false);
        mAdapter.showLastModified = (sorter == 5);

        final FileFilter lsFilter;
        if (filter != null) {
            String display = getFilterDisplay();
            act.setStatus(R.string.filter_active, display);
            lsFilter = new FileFilter() {
                @Override
                public boolean accept(File file) {
                    if (file.isDirectory()) return true;
                    if (filter.equals("archives")) {
                        String ext = file.getExtension();
                        return ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("rar") || ext.equalsIgnoreCase("tar") ||
                                ext.equalsIgnoreCase("tar.gz") || ext.equalsIgnoreCase(".7z");
                    } else {
                        String[] splitFilter = filter.split(":");
                        if (splitFilter[0].equals("mime")) {
                            return file.getMimeType().startsWith(splitFilter[1]);
                        } else {
                            return file.getExtension().equals(splitFilter[1]);
                        }
                    }
                }
            };
        } else {
            lsFilter = null;
            act.setStatus(0, null);
        }

        act.closeDrawers();
        if (mDirectory.getPluginPackage() != null) {
            PluginFileImpl pf = (PluginFileImpl) mDirectory;
            boolean needsConnection = false;
            if (!pf.getPlugin().isBinded()) {
                needsConnection = true;
            } else {
                try {
                    // Verify the service is still binded by executing a remote method
                    pf.getPlugin().getService().isConnected();
                } catch (RemoteException e) {
                    needsConnection = true;
                }
            }
            if (needsConnection) {
                mConnectCallback = new Plugin.Callback() {
                    @Override
                    public void onResult(Plugin plugin, Exception e) {
                        if (e != null) {
                            e.printStackTrace();
                            errorListing(e.getMessage());
                            return;
                        }
                        finishList(lsFilter, act, showAnimation);
                    }
                };
                pf.getPlugin().startService(mConnectCallback);
            } else {
                finishList(lsFilter, act, showAnimation);
            }
        } else {
            finishList(lsFilter, act, showAnimation);
        }
    }

    private void finishList(final FileFilter lsFilter, final MainActivity act, final boolean showAnimation) {
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                //Plugin
                if (mDirectory.getPluginPackage() != null) {
                    PluginFileImpl pf = (PluginFileImpl) mDirectory;
                    try {
                        final IPluginService service = pf.getPlugin().getService();
                        if (service.authenticationNeeded()) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    act.hidePluginFolder(null, null, true);
                                }
                            });
                            service.addAccount(true);
                            return;
                        }

                        if (pf.getPlugin().hasAccounts()) {
                            final Uri uri = PluginDataProvider.getUri(getActivity(), pf.getPluginPackage(), true);
                            final String uriPkg = uri.getAuthority();
                            final String uriAccount = PluginFileImpl.getPluginAccount(uri, pf.getPlugin());
                            if (mDirectory.getPluginAccount() == null)
                                throw new Exception("URI didn't specify an account, but the plugin expects one.");
                            else if (uriAccount == null)
                                throw new Exception("Plugin expects account in URI, but didn't get one.");

                            if (service.getCurrentAccount() == null ||
                                    !service.getCurrentAccount().equals(uriAccount)) {
                                // User is opening a plugin account that isn't currently connected to
                                service.disconnect();
                                service.setCurrentAccount(uriAccount);
                                PluginDataProvider.setCurrent(getActivity(), uriPkg, uriAccount);
                            }
                        }
                        if (!service.isConnected()) {
                            PluginErrorResult result = service.connect();
                            if (result != null)
                                throw new Exception(result.getError());
                        }
                    } catch (Exception e1) {
                        e1.printStackTrace();
                        errorListing(e1.getMessage());
                        return;
                    }
                }

                //List files
                List<File> listed;
                try {
                    listed = mDirectory.listFiles();
                    listed = File.filter(listed, showHidden, lsFilter);
                } catch (RootDeniedException e) {
                    errorListing(getString(R.string.superuser_not_available));
                    return;
                } catch (Exception e2) {
                    errorListing(e2.getMessage());
                    return;
                }

                final List<File> finalListed = listed;
                successListing();
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalListed != null)
                            mAdapter.set(finalListed, showAnimation);
                        restoreScrollPosition();
                    }
                });
            }
        });
    }

    public void updateComparator() {
        mAdapter.setComparator(getComparator());
        jumpToTop(false);
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.goUp:
                if (getActivity() != null)
                    ((MainActivity) getActivity()).switchDirectory(mDirectory.getParent());
                break;
            case R.id.showHidden:
                Utils.setShowHidden(this, !showHidden);
                break;
            case R.id.sortName:
                item.setChecked(true);
                Utils.setSorter(this, 1);
                break;
            case R.id.sortExtension:
                item.setChecked(true);
                Utils.setSorter(this, 2);
                break;
            case R.id.sortSizeLowHigh:
                item.setChecked(true);
                Utils.setSorter(this, 3);
                break;
            case R.id.sortSizeHighLow:
                item.setChecked(true);
                Utils.setSorter(this, 4);
                break;
            case R.id.sortLastModified:
                item.setChecked(true);
                Utils.setSorter(this, 5);
                break;
            case R.id.foldersFirstCheck:
                if (getActivity() == null) return false;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                boolean foldersFirst = prefs.getBoolean("folders_first", true);
                prefs.edit().putBoolean("folders_first", !foldersFirst).commit();
                item.setChecked(!foldersFirst);
                updateComparator();
                break;
            case R.id.filterNone:
                item.setChecked(true);
                Utils.setFilter(this, null);
                break;
            case R.id.filterText:
                item.setChecked(true);
                Utils.setFilter(this, "mime:text");
                break;
            case R.id.filterImage:
                item.setChecked(true);
                Utils.setFilter(this, "mime:image");
                break;
            case R.id.filterAudio:
                item.setChecked(true);
                Utils.setFilter(this, "mime:audio");
                break;
            case R.id.filterVideo:
                item.setChecked(true);
                Utils.setFilter(this, "mime:video");
                break;
            case R.id.filterArchives:
                item.setChecked(true);
                Utils.setFilter(this, "archives");
                break;
            case R.id.filterOther: {
                String prefill = null;
                if (filter != null && filter.startsWith("ext")) {
                    prefill = filter.split(":")[1];
                }
                Utils.showInputDialog(getActivity(), R.string.extension, R.string.extension_hint, prefill, new Utils.InputCancelCallback() {
                    @Override
                    public void onInput(String input) {
                        if (getActivity() != null)
                            getActivity().invalidateOptionsMenu();
                        if (input.startsWith(".") && !input.equals("."))
                            input = input.substring(1);
                        Utils.setFilter(DirectoryFragment.this, "ext:" + input);
                    }

                    @Override
                    public void onCancel() {
                        //setChecked(false) on the menu item somehow makes it checked.
                        if (getActivity() != null)
                            getActivity().invalidateOptionsMenu();
                    }

                    @Override
                    public void onDismiss() {
                        if (getActivity() != null)
                            getActivity().invalidateOptionsMenu();
                    }
                });
                break;
            }
            case R.id.gridSizeOne:
                item.setChecked(true);
                Utils.setGridSize(this, 1);
                break;
            case R.id.gridSizeTwo:
                item.setChecked(true);
                Utils.setGridSize(this, 2);
                break;
            case R.id.gridSizeThree:
                item.setChecked(true);
                Utils.setGridSize(this, 3);
                break;
            case R.id.gridSizeFour:
                item.setChecked(true);
                Utils.setGridSize(this, 4);
                break;
            case R.id.gridSizeFive:
                item.setChecked(true);
                Utils.setGridSize(this, 5);
                break;
            case R.id.gridSizeSix:
                item.setChecked(true);
                Utils.setGridSize(this, 6);
                break;
            case R.id.compactMode:
                item.setChecked(!item.isChecked());
                Utils.setCompactMode(this, item.isChecked());
                break;
            case R.id.showCurrentPath:
                item.setChecked(!item.isChecked());
                if (getActivity() != null) {
                    PreferenceManager.getDefaultSharedPreferences(getActivity())
                            .edit().putBoolean("show_breadcrumbs", item.isChecked()).commit();
                    invalidateCrumbs(true);
                    invalidateTitle();
                }
                break;
            case R.id.addHomescreenShortcut:
                Utils.addHomescreenShortcut(getActivity(), getDirectory());
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClicked(int index, File file) {
        final MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            if (!act.pickMode && act.getCab() != null && act.getCab() instanceof MainCab && act.getCab().isActive()) {
                mAdapter.performSelect(index);
            } else if (file.isDirectory() || file.isViewableArchive(act)) {
                saveScrollPosition();
                act.switchDirectory(file);
            } else if (act.pickMode) {
                final PickerCab cab = (PickerCab) act.getCab();
                if (cab.isExtractMode()) {
                    // Ignore file taps when choosing a folder
                    return;
                }
                pickFile(file, act, cab);
            } else {
                Utils.openFile(this, file, false);
            }
        }
    }

    private void pickFile(File file, final Activity act, PickerCab cab) {
        if (act == null) return;
        final Uri uri = file.getUri();
        final Intent intent = act.getIntent().setData(uri);
        if (file.isDocumentTreeFile())
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (cab.isRingtoneMode()) {
            String mime = file.getMimeType();
            if (mime == null || !mime.startsWith("audio/")) {
                Toast.makeText(act, R.string.not_audio_file, Toast.LENGTH_SHORT).show();
                return;
            }
            MediaScannerConnection.scanFile(act, new String[]{file.getPath()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.v("CabinetRingtone", uri.toString());
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
                            act.setResult(Activity.RESULT_OK, intent);
                            act.finish();
                        }
                    });
        } else {
            act.setResult(Activity.RESULT_OK, intent);
            act.finish();
        }
    }

    @Override
    public void onItemLongClick(int index, File file, boolean added) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        BaseCab cab = activity.getCab();
        if (cab != null && (cab instanceof CopyCab || cab instanceof CutCab) && cab.isActive()) {
            if (added) ((BaseFileCab) cab).addFile(file, false);
            else ((BaseFileCab) cab).removeFile(file, false);
        } else {
            boolean shouldCreateCab = cab == null || !cab.isActive() || !(cab instanceof MainCab) && added;
            if (shouldCreateCab)
                activity.setCab(new MainCab()
                        .setFragment(this).setFile(file, false).start());
            else {
                if (added) ((BaseFileCab) cab).addFile(file, false);
                else ((BaseFileCab) cab).removeFile(file, false);
            }
        }
    }

    @Override
    public void onMenuItemClick(final File file, MenuItem item) {
        final MainActivity act = (MainActivity) getActivity();
        if (act == null) return;
        switch (item.getItemId()) {
            case R.id.bookmark:
                BookmarkProvider.addItem(act, new BookmarkProvider.Item(file));
                act.reloadNavDrawer(true);
                break;
            case R.id.addHomescreenShortcut:
                Utils.addHomescreenShortcut(act, file);
                break;
            case R.id.openAs:
                Utils.openFile(this, file, true);
                break;
            case R.id.copy: {
                onFileMenuCopy(file, act);
                break;
            }
            case R.id.move: {
                onFileMenuMove(file, act);
                break;
            }
            case R.id.rename:
                onFileMenuRename(file, act);
                break;
            case R.id.archive:
                onFileMenuArchive(file, act);
                break;
            case R.id.share:
                MainCab.shareFile(getActivity(), file);
                break;
            case R.id.delete:
                onFileMenuDelete(file, act);
                break;
            case R.id.details:
                onFileMenuDetails(file, act);
                break;
        }

    }

    private void onFileMenuCopy(File file, MainActivity act) {
        BaseCab cab = act.getCab();
        boolean shouldCreateCopy = cab == null || !cab.isActive() || !(cab instanceof CopyCab);
        if (shouldCreateCopy) {
            if (cab != null && cab instanceof BaseFileCab) {
                ((BaseFileCab) cab).overrideDestroy = true;
            }
            act.setCab(new CopyCab()
                    .setFragment(this).setFile(file, true).start());
        } else ((BaseFileCab) cab).setFragment(this).addFile(file, true);
    }

    private void onFileMenuMove(File file, MainActivity act) {
        BaseCab cab = act.getCab();
        boolean shouldCreateCut = cab == null || !cab.isActive() || !(cab instanceof CutCab);
        if (shouldCreateCut) {
            if (cab != null && cab instanceof BaseFileCab) {
                ((BaseFileCab) cab).overrideDestroy = true;
            }
            ((MainActivity) getActivity()).setCab(new CutCab()
                    .setFragment(this).setFile(file, true).start());
        } else ((BaseFileCab) cab).setFragment(this).addFile(file, true);
    }

    private void onFileMenuRename(final File file, final MainActivity act) {
        Utils.showInputDialog(getActivity(), R.string.rename, 0, file.getName(), new Utils.InputCallback() {
            @Override
            public void onInput(String text) {
                if (getActivity() == null || text.equals(file.getName()))
                    return;
                else if (!text.contains("."))
                    text += file.getExtension();
                final File newFile = File.getNewFile(getActivity(), file.getParent(), text, file.isDirectory(), true);
                BackgroundThread.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final File result = file.move(getActivity(), newFile, null, false);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    getAdapter().update(file, result);
                                    updateComparator();
                                    if (act.getCab() != null && act.getCab() instanceof BaseFileCab) {
                                        int cabIndex = ((BaseFileCab) act.getCab()).findFile(file);
                                        if (cabIndex > -1)
                                            ((BaseFileCab) act.getCab()).setFile(cabIndex, newFile, true);
                                        Toast.makeText(getActivity(), getString(R.string.renamed_to,
                                                newFile.getPath()), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        } catch (final Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utils.showErrorDialog(getActivity(), R.string.failed_rename_file, e);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void onFileMenuDetails(File file, MainActivity act) {
        try {
            DetailsDialog.create(file, false).show(act.getFragmentManager(), "DETAILS_DIALOG");
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void onFileMenuArchive(File file, MainActivity act) {
        if (file.isArchiveOrInArchive(getActivity())) {
            startActivityForResult(new Intent(act, MainActivity.class)
                    .setAction(Intent.ACTION_GET_CONTENT)
                    .putExtra("extract_mode", true)
                    .putExtra("file", file), CHOOSE_DESTINATION_REQUEST);
        } else {
            final List<File> files = new ArrayList<>();
            files.add(file);
            Zipper.zip(this, files, null);
        }
    }

    private void onFileMenuDelete(final File file, final MainActivity act) {
        Utils.showConfirmDialog(act, R.string.delete, R.string.confirm_delete_prompt, file.getName(), new Utils.ClickListener() {
            @Override
            public void onPositive(int which, View view) {
                final MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                        .content(R.string.deleting)
                        .progress(true, 0)
                        .show();
                BackgroundThread.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            file.delete(act);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    act.notifyDeleted(file);
                                    mAdapter.remove(file);
                                    if (act.getCab() != null && act.getCab() instanceof BaseFileCab) {
                                        BaseFileCab cab = (BaseFileCab) act.getCab();
                                        if (cab.getFiles() != null && cab.getFiles().size() > 0) {
                                            List<File> files = new ArrayList<>();
                                            files.addAll(cab.getFiles()); // copy so it doesn't get modified by CAB functions
                                            cab.removeFile(file, true);
                                            for (File fi : files) {
                                                if (fi.getUri().toString().startsWith(file.getUri().toString()))
                                                    cab.removeFile(fi, true);
                                            }
                                        }
                                    }
                                    try {
                                        dialog.dismiss();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (final Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utils.showErrorDialog(getActivity(), R.string.failed_delete_file, e);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == CHOOSE_DESTINATION_REQUEST) {
                final File dest = new LocalFile(new java.io.File(data.getData().getPath()));
                final List<File> target = new ArrayList<>();
                target.add((File) data.getSerializableExtra("file"));
                Unzipper.unzip((PluginActivity) getActivity(), dest, target, new Zipper.ZipCallback() {
                    @Override
                    public void onComplete() {
                        if (getActivity() != null)
                            ((MainActivity) getActivity()).switchDirectory(dest);
                    }
                });
            } else if (requestCode == NEW_CONNECTION_REQUEST) {
                ((MainActivity) getActivity()).reloadNavDrawer(true);
            }
        }
    }

    @Override
    public String toString() {
        if (mDirectory != null)
            return mDirectory.toString();
        return null;
    }
}