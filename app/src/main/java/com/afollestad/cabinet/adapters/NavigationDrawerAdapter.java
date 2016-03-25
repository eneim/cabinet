package com.afollestad.cabinet.adapters;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.RemoteException;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.bookmarks.BookmarkProvider;
import com.afollestad.cabinet.bookmarks.PinsLegacy;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.IPluginService;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginDataProvider;
import com.afollestad.cabinet.plugins.PluginErrorResult;
import com.afollestad.cabinet.plugins.PluginFile;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.StorageUtils;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.views.CircleView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.ThemeSingleton;
import com.afollestad.materialdialogs.util.DialogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class NavigationDrawerAdapter extends RecyclerView.Adapter<NavigationDrawerAdapter.ShortcutViewHolder>
        implements View.OnClickListener, View.OnLongClickListener {

    private final static int ALPHA_ACTIVATED = 255;
    private final static int ALPHA_ICON = 138;
    private final static int ALPHA_TEXT = 222;

    private final static int ACTION_ADD_EXTERNAL_STORAGE = 1;
    private final static int ACTION_DONATE = 2;
    private final static int ACTION_SETTINGS = 3;
    private final static int ACTION_HELPFEEDBACK = 4;

    private int mPluginWaitIndex = -1;
    private String uninstalledPkg;
    private MaterialDialog mDialog;
    private Plugin.Callback mConnectionCallback;
    private Handler mHandler;

    private final MainActivity mContext;
    private List<BookmarkProvider.Item> mItems;
    private int mCheckedPos = -1;
    private final ClickListener mListener;
    private int navIconColor;
    private HashMap<String, StatFs> mStatFs;
    private boolean mShowAddExternal;

    public void onResume() {
        if (mPluginWaitIndex > -1) {
            BookmarkProvider.Item item = mItems.get(mPluginWaitIndex);
            if (item.isPlugin()) {
                final Uri uri = PluginDataProvider.getUri(mContext, item.getPluginPackage(), true);
                item.uri = uri.toString();
                final String accountId = PluginFileImpl.getPluginAccount(uri, item.getPlugin(mContext));
                if (accountId == null) {
                    item.nickname = item.getPlugin(mContext).getName().toString();
                } else {
                    item.nickname = PluginDataProvider.getAccountDisplay(mContext, item.getPluginPackage(), accountId);
                    if (item.nickname == null)
                        item.nickname = accountId;
                }
                if (mCheckedPos == mPluginWaitIndex)
                    mContext.switchDirectory(item);
            }
            mPluginWaitIndex = -1;
        }
    }

    public void onPause() {
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();
        if (mConnectionCallback != null)
            mConnectionCallback.cancelled = true;
        for (BookmarkProvider.Item i : mItems) {
            Plugin p = i.getPlugin(mContext, true);
            if (p != null)
                p.release();
        }
        mHandler = null;
    }

    @Override
    public void onClick(View view) {
        if (view.getTag() instanceof String) {
            String[] splitTag = ((String) view.getTag()).split(":");
            if (splitTag[0].equalsIgnoreCase("accountswitch")) {
                int index = Integer.parseInt(splitTag[1]);
                showAccountSwitcher(mItems.get(index));
            }
        } else {
            int index = (Integer) view.getTag();
            if (index > mItems.size() - 1) return;
            BookmarkProvider.Item item = mItems.get(index);
            switch (item.actionId) {
                case ACTION_ADD_EXTERNAL_STORAGE:
                    mListener.onClickAddExternal();
                    break;
                case ACTION_DONATE:
                    mListener.onClickDonate();
                    break;
                case ACTION_SETTINGS:
                    mListener.onClickSettings();
                    break;
                case ACTION_HELPFEEDBACK:
                    mListener.onClickHelpAndFeedback();
                    break;
                default:
                    mListener.onClick(index);
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        final int index = (Integer) view.getTag();
        if (index < mItems.size()) {
            final BookmarkProvider.Item item = mItems.get(index);
            if (item.actionId == ACTION_ADD_EXTERNAL_STORAGE) {
                new MaterialDialog.Builder(mContext)
                        .content(Html.fromHtml(mContext.getString(R.string.hide_add_external_confirm)))
                        .positiveText(R.string.yes)
                        .negativeText(R.string.no)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);
                                PreferenceManager.getDefaultSharedPreferences(mContext)
                                        .edit().putBoolean("show_add_external", false).commit();
                                reload();
                                if (mCheckedPos > index) {
                                    // Items get shifted up, so shift checked position
                                    setCheckedPos(mCheckedPos - 1);
                                }
                            }
                        }).show();
            } else if (item.isMain && item.isLocalRoot()) {
                new MaterialDialog.Builder(mContext)
                        .content(Html.fromHtml(mContext.getString(R.string.hide_root_confirm)))
                        .positiveText(R.string.yes)
                        .negativeText(R.string.no)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);
                                PreferenceManager.getDefaultSharedPreferences(mContext)
                                        .edit().putBoolean("show_root_bookmark", false).commit();
                                reload();
                                if (mCheckedPos == index)
                                    setCheckedPos(-1);
                            }
                        }).show();
            } else if (item.isPlugin()) {
                showPluginOptions(item);
            } else if (item.isDocumentTree() || !item.isMain) {
                return mListener.onLongClick((Integer) view.getTag());
            }
        }
        return true;
    }

    private void showContactingPluginDialog() {
        mHandler = new Handler();
        mDialog = new MaterialDialog.Builder(mContext)
                .content(R.string.contacting_plugin)
                .progress(true, -1)
                .cancelable(true)
                .cancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (mConnectionCallback != null)
                            mConnectionCallback.cancelled = true;
                    }
                }).show();
    }

    /**
     * Plugin long-press action
     */
    private void showPluginOptions(final BookmarkProvider.Item item) {
        final List<CharSequence> options = new ArrayList<>();
        final String[] current = PluginDataProvider.getCurrentAccount(mContext, item.getPluginPackage());
        if (current != null && item.pluginHasSettings(mContext, true))
            options.add(mContext.getString(R.string.account_settings));
        if (current != null && item.pluginHasAccounts(mContext))
            options.add(mContext.getString(R.string.remove_account));
        if (item.pluginHasSettings(mContext, false))
            options.add(mContext.getString(R.string.plugin_settings));
        options.add(mContext.getString(R.string.uninstall_plugin));

        mDialog = new MaterialDialog.Builder(mContext)
                .title(item.nickname)
                .items(options.toArray(new CharSequence[options.size()]))
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                        final String option = charSequence.toString();
                        if (option.equals(mContext.getString(R.string.account_settings))) {
                            assert current != null;
                            showPluginSettings(item, current[0]);
                        } else if (option.equals(mContext.getString(R.string.remove_account))) {
                            assert current != null;
                            showRemoveAccountPrompt(item, current[0]);
                        } else if (option.equals(mContext.getString(R.string.plugin_settings))) {
                            showPluginSettings(item, null);
                        } else if (option.equals(mContext.getString(R.string.uninstall_plugin))) {
                            uninstallPlugin(item.getPluginPackage());
                        }
                    }
                }).show();
    }

    private void showPluginSettings(final BookmarkProvider.Item item, final String accountId) {
        showContactingPluginDialog();
        mConnectionCallback = new Plugin.Callback() {
            @Override
            public void onResult(Plugin plugin, Exception e) {
                if (mDialog != null)
                    mDialog.dismiss();
                if (cancelled) return;
                else if (e != null) {
                    Utils.showErrorDialog(mContext, e.getLocalizedMessage());
                    return;
                }
                try {
                    mPluginWaitIndex = item.index;
                    final String[] accountData = PluginDataProvider.getAccount(mContext, item.getPluginPackage(), accountId);
                    plugin.getService().openSettings(accountId, accountData[0], accountData[1]);
                    plugin.getService().disconnect();
                } catch (Exception e2) {
                    mPluginWaitIndex = -1;
                    Utils.showErrorDialog(mContext, e2.getLocalizedMessage());
                }
            }
        };
        item.verifyPluginConnection(mContext, mHandler, true, mConnectionCallback);
    }

    /**
     * Plugin menu action
     */
    private void showAccountSwitcher(final BookmarkProvider.Item item) {
        showContactingPluginDialog();
        mConnectionCallback = new Plugin.Callback() {
            @Override
            public void onResult(final Plugin plugin, Exception e) {
                if (mDialog != null)
                    mDialog.dismiss();
                if (cancelled) return;
                else if (e != null) {
                    Utils.showErrorDialog(mContext, e.getLocalizedMessage());
                    return;
                }

                final String[][] accounts = PluginDataProvider.getAccounts(mContext, plugin.getPackage());
                if (accounts.length == 0) {
                    mDialog = new MaterialDialog.Builder(mContext)
                            .title(R.string.no_accounts)
                            .content(Html.fromHtml(mContext.getString(R.string.no_accounts_prompt, plugin.getName())))
                            .positiveText(R.string.yes)
                            .negativeText(R.string.no)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    try {
                                        plugin.getService().addAccount(true);
                                    } catch (RemoteException e) {
                                        Utils.showErrorDialog(mContext, e.getLocalizedMessage());
                                    }
                                }
                            }).show();
                    return;
                }

                final String[] currentAccount = PluginDataProvider.getCurrentAccount(mContext, plugin.getPackage());
                List<String> names = new ArrayList<>();
                int preselect = -1;
                for (int i = 0; i < accounts.length; i++) {
                    String[] acc = accounts[i];
                    if (currentAccount != null && acc[0].equals(currentAccount[0]))
                        preselect = i;
                    names.add(acc[1]);
                }
                if (cancelled) return;

                mDialog = new MaterialDialog.Builder(mContext)
                        .title(R.string.switch_accounts)
                        .items(names.toArray(new String[names.size()]))
                        .positiveText(android.R.string.ok)
                        .neutralText(R.string.switcher_add_account)
                        .itemsCallbackSingleChoice(preselect, new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                                if (cancelled) return true;
                                else if (currentAccount != null && accounts[i][0].equals(currentAccount[0]))
                                    return true; // account didn't change
                                switchAccount(item, plugin, accounts[i]);
                                return true;
                            }
                        })
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onNeutral(MaterialDialog dialog) {
                                try {
                                    plugin.getService().addAccount(false);
                                } catch (RemoteException e) {
                                    Utils.showErrorDialog(mContext, e.getLocalizedMessage());
                                }
                            }
                        })
                        .show();
            }
        };
        item.verifyPluginConnection(mContext, mHandler, true, mConnectionCallback);
    }

    private void switchAccount(final BookmarkProvider.Item item, final Plugin plugin, final String[] account) {
        mDialog = new MaterialDialog.Builder(mContext)
                .content(Html.fromHtml(mContext.getString(R.string.switching_to_x, account[1])))
                .progress(true, -1)
                .show();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                IPluginService service = plugin.getService();
                try {
                    service.disconnect();
                    service.setCurrentAccount(account[0]);
                } catch (final RemoteException e) {
                    e.printStackTrace();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mDialog != null)
                                mDialog.dismiss();
                            Utils.showErrorDialog(mContext, e.getLocalizedMessage());
                        }
                    });
                    return;
                }

                PluginDataProvider.setCurrent(mContext, plugin.getPackage(), account[0]);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Uri uri = PluginDataProvider.getUri(mContext, plugin.getPackage(), true);
                        PluginFileImpl pf = new PluginFileImpl(uri, plugin);
                        item.uri = uri.toString();
                        if (account[1] != null && !account[1].trim().isEmpty())
                            item.nickname = account[1];
                        else item.nickname = account[0];
                        notifyItemChanged(item.index);
                        mContext.switchDirectory(pf);
                        if (mDialog != null)
                            mDialog.dismiss();
                    }
                });
            }
        });
    }

    private void showRemoveAccountPrompt(final BookmarkProvider.Item item, final String accountId) {
        mDialog = new MaterialDialog.Builder(mContext)
                .title(R.string.remove_account)
                .content(Html.fromHtml(mContext.getString(R.string.remove_plugin_account_prompt,
                        item.nickname, item.getPluginName(mContext))))
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        showContactingPluginDialog();
                        item.verifyPluginConnection(mContext, mHandler, true, new Plugin.Callback() {
                            @Override
                            public void onResult(Plugin plugin, Exception e) {
                                if (mDialog != null)
                                    mDialog.dismiss();
                                if (cancelled) return;
                                else if (e != null) {
                                    Utils.showErrorDialog(mContext, e.getLocalizedMessage());
                                    return;
                                }
                                try {
                                    IPluginService service = plugin.getService();
                                    if (accountId.equals(service.getCurrentAccount()))
                                        service.disconnect();
                                    PluginErrorResult result = service.removeAccount(accountId);
                                    if (result != null && result.getError() != null)
                                        throw new Exception(result.getError());
                                    if (cancelled) return;

                                    // Get initial URI for newly selected account if possible
                                    final Uri newUri = Uri.parse(PluginDataProvider.removeAccount(
                                            mContext, plugin.getPackage(), accountId));
                                    item.uri = newUri.toString();
                                    final String accountId = PluginFileImpl.getPluginAccount(newUri, plugin);
                                    if (accountId != null) {
                                        // A new account is selected
                                        item.nickname = PluginDataProvider.getAccountDisplay(mContext, plugin.getPackage(), accountId);
                                        if (item.nickname == null)
                                            item.nickname = accountId;
                                    } else {
                                        // Plugin is out of accounts
                                        item.nickname = plugin.getName().toString();
                                    }
                                    notifyItemChanged(item.index);

                                    // Navigate away from plugin folder if visible
                                    if (accountId == null) {
                                        // Plugin is out of accounts, kill the service and hide the plugin folder
                                        mContext.hidePluginFolder(plugin.getPackage(), null, false);
                                        service.exit();
                                    } else {
                                        // Set the current account and navigate to its folder
                                        service.setCurrentAccount(accountId);
                                        if (cancelled) return;
                                        mContext.switchDirectory(item);
                                    }
                                } catch (Exception e2) {
                                    e2.printStackTrace();
                                    Utils.showErrorDialog(mContext, e2.getLocalizedMessage());
                                }
                            }
                        });
                    }
                }).show();
    }

    private void uninstallPlugin(final String pkg) {
        mHandler = new Handler();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                List<Plugin> plugins = mContext.getPlugins();
                for (Plugin p : plugins) {
                    if (p.getPackage().equals(pkg)) {
                        if (p.isBinded()) {
                            try {
                                if (p.getService().isConnected())
                                    p.getService().disconnect();
                                p.getService().exit();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        p.release();
                    }
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            uninstalledPkg = pkg;
                            Uri packageURI = Uri.parse("package:" + pkg);
                            Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI)
                                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
                            mContext.startActivityForResult(uninstallIntent, MainActivity.UNINSTALL_PLUGIN_REQUEST);
                        } catch (ActivityNotFoundException ignored) {
                            uninstalledPkg = null;
                        }
                    }
                });
            }
        });
    }

    public void pluginUninstalled() {
        if (uninstalledPkg != null) {
            for (Iterator<BookmarkProvider.Item> iterator = mItems.iterator(); iterator.hasNext(); ) {
                BookmarkProvider.Item i = iterator.next();
                if (i.getPluginPackage() != null && i.getPluginPackage().equals(uninstalledPkg)) {
                    iterator.remove();
                    mContext.notifyDeleted(i.uri, true, false);
                }
            }
            notifyDataSetChanged();
            mContext.removePlugin(uninstalledPkg);
            mContext.hidePluginFolder(uninstalledPkg, null, false);
            uninstalledPkg = null;
            BookmarkProvider.clean(mContext, mContext.getPlugins());
        }
    }

    public interface ClickListener {

        void onClick(int index);

        boolean onLongClick(int index);

        void onClickDonate();

        void onClickSettings();

        void onClickHelpAndFeedback();

        void onClickAddExternal();
    }

    @SuppressWarnings("deprecation")
    private boolean runLegacyMigration() {
        List<PinsLegacy.Item> old = PinsLegacy.getAll(mContext);
        if (old.size() > 0) {
            // Migrate old legacy items to the new provider
            int migratedCount = 0;
            for (PinsLegacy.Item item : old) {
                File fi = item.toFile();
                if (fi == null || fi.isRoot() || fi.isStorageDirectory() || fi.isSDCardDirectory() || fi.isDocumentTreeFile()) {
                    Log.v("NavLegacyMigrate", "Skipped: " + (fi != null ? fi.getUri().toString() : "null"));
                    continue;
                }
                try {
                    if (fi.exists()) {
                        BookmarkProvider.addItem(mContext, new BookmarkProvider.Item(fi));
                        Log.v("NavLegacyMigrate", "Migrated: " + fi.getUri());
                        migratedCount++;
                    } else {
                        Log.v("NavLegacyMigrate", fi.getUri() + " no longer exists. Not migrated.");
                    }
                } catch (Exception e) {
                    Log.v("NavLegacyMigrate", "Error migrating " + fi.getUri() + ": " + e.getLocalizedMessage());
                }
            }
            Log.v("NavLegacyMigrate", "Clearing legacy items...");
            PinsLegacy.clear(mContext);
            Log.v("NavLegacyMigrate", "Migration complete.");
            return migratedCount > 0;
        }
        Log.v("NavLegacyMigrate", "No legacy items exist. Migration skipped.");
        return false;
    }

    public NavigationDrawerAdapter(MainActivity context, ClickListener listener) {
        mContext = context;
        mListener = listener;
        navIconColor = Utils.resolveColor(context, R.attr.nav_drawer_icontext);
        if (DialogUtils.isColorDark(navIconColor))
            navIconColor = CircleView.shiftColorUp(navIconColor);

        if (BookmarkProvider.isEmpty(context)) {
            if (!runLegacyMigration()) {
                // First start, fill the pins with the defaults
                java.io.File item = new java.io.File(Environment.getExternalStorageDirectory(), "DCIM");
                if (item.exists())
                    BookmarkProvider.addItem(context, new BookmarkProvider.Item(item));
                item = new java.io.File(Environment.getExternalStorageDirectory(), "Download");
                if (item.exists())
                    BookmarkProvider.addItem(context, new BookmarkProvider.Item(item));
                item = new java.io.File(Environment.getExternalStorageDirectory(), "Music");
                if (item.exists())
                    BookmarkProvider.addItem(context, new BookmarkProvider.Item(item));
                item = new java.io.File(Environment.getExternalStorageDirectory(), "Pictures");
                if (item.exists())
                    BookmarkProvider.addItem(context, new BookmarkProvider.Item(item));
            }
        }
    }

    public void reload() {
        if (uninstalledPkg != null) {
            // Skip reloading this time
            uninstalledPkg = null;
            return;
        } else if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("show_free_space", true))
            mStatFs = new HashMap<>();
        else mStatFs = null;

        final boolean showRoot = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("show_root_bookmark", true);
        mShowAddExternal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("show_add_external", true);
        final List<Plugin> plugins = mContext.getPlugins(true);

        if (plugins != null) {
            BookmarkProvider.clean(mContext, plugins);
            PluginDataProvider.clean(mContext, plugins);
        }
        mItems = BookmarkProvider.getItems(mContext);
        int addIndex = 0;

        // Add 1 for 'Root' shortcut
        if (showRoot) {
            mItems.add(addIndex, new BookmarkProvider.Item(mContext.getString(R.string.root), Uri.parse("file:///")).asMain());
            addIndex++;
        }

        // Add 1 for internal storage
        mItems.add(addIndex, new BookmarkProvider.Item(mContext.getString(R.string.internal_storage),
                Uri.fromFile(Environment.getExternalStorageDirectory())).asMain());
        addIndex++;

        // Add 1 for pre-Lollipop SD card
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (App.getSDCard() != null) {
                mItems.add(addIndex, new BookmarkProvider.Item(App.getSDCard().asFile()).asMain());
            }
        } else {
            // Add 1 for 'Add external storage…' shortcut
            if (mShowAddExternal) {
                mItems.add(addIndex, new BookmarkProvider.Item(
                        mContext.getString(R.string.add_document_tree_path), ACTION_ADD_EXTERNAL_STORAGE
                ).asMain());
            }
            // Move document tree paths up under internal storage, and mark them as main items
            for (int i = 0; i < mItems.size(); i++) {
                BookmarkProvider.Item item = mItems.get(i);
                if (item.isRootDocumentTree(mContext)) {
                    mItems.remove(i);
                    mItems.add(addIndex, item.asMain());
                    addIndex++;
                    break;
                }
            }
        }

        if (plugins != null) {
            for (Plugin p : plugins) {
                Uri uri = PluginDataProvider.getUri(mContext, p.getPackage(), true);
                String path;
                if (uri != null)
                    path = PluginFileImpl.getPath(uri, p);
                else path = File.separator;
                PluginFile pf = new PluginFile.Builder(null, p.getPackage())
                        .path(path)
                        .build();
                final String acc = PluginFileImpl.getPluginAccount(uri, p);
                PluginFileImpl pi = new PluginFileImpl(pf, acc, p);
                BookmarkProvider.Item item = new BookmarkProvider.Item(pi).asMain();
                if (acc != null) {
                    item.nickname = PluginDataProvider.getAccountDisplay(mContext,
                            pi.getPluginPackage(), acc);
                }
                mItems.add(addIndex, item);
            }
        }

        // Add 3 for donate, settings, and help & feedback
        mItems.add(new BookmarkProvider.Item(
                mContext.getString(R.string.support_dev), ACTION_DONATE
        ).asMain());
        mItems.add(new BookmarkProvider.Item(
                mContext.getString(R.string.settings), ACTION_SETTINGS
        ).asMain());
        mItems.add(new BookmarkProvider.Item(
                mContext.getString(R.string.helpandfeedback_title), ACTION_HELPFEEDBACK
        ).asMain());

        notifyDataSetChanged();
    }

    public int findPluginIndex(String pkg) {
        for (int i = 0; i < mItems.size(); i++) {
            BookmarkProvider.Item item = mItems.get(i);
            if (item.isPlugin() && item.getPluginPackage().equals(pkg))
                return i;
        }
        return -1;
    }


    public void set(int index, BookmarkProvider.Item item) {
        mItems.set(index, item);
        notifyItemChanged(index);
    }

    public int setCheckedFile(File file) {
        if (mItems == null || mItems.size() == 0)
            return -1;
        int index = -1;
        for (int i = 0; i < mItems.size(); i++) {
            BookmarkProvider.Item item = mItems.get(i);
            File fi = item.asFile(mContext);
            if (fi != null && fi.equals(file)) {
                index = i;
                break;
            }
        }
        setCheckedPos(index);
        return index;
    }

    public void remove(File file) {
        synchronized (mContext) {
            for (int i = 0; i < mItems.size(); i++) {
                if (mItems.get(i).uri != null && mItems.get(i).uri.equals(file.getUri().toString())) {
                    mItems.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
        }
    }

    public void setCheckedPos(int index) {
        int beforeChecked = mCheckedPos;
        mCheckedPos = index;
        if (beforeChecked > -1)
            notifyItemChanged(beforeChecked);
        notifyItemChanged(mCheckedPos);
    }

    public int getCheckedPos() {
        return mCheckedPos;
    }

    public BookmarkProvider.Item getItem(int index) {
        if (mItems.size() == 0) return null;
        return mItems.get(index);
    }

    public static class ShortcutViewHolder extends RecyclerView.ViewHolder {

        public ShortcutViewHolder(View itemView) {
            super(itemView);
            divider = itemView.findViewById(R.id.divider);
            item = itemView.findViewById(R.id.item);
            title = (TextView) itemView.findViewById(R.id.title);
            subtitle = (TextView) itemView.findViewById(R.id.subtitle);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            expand = (ImageView) itemView.findViewById(R.id.expand);
        }

        final TextView title;
        final TextView subtitle;
        final ImageView icon;
        final View divider;
        final View item;
        final ImageView expand;
    }

    @Override
    public ShortcutViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_drawer,
                parent, false);
        return new ShortcutViewHolder(v);
    }

    private String getFreeSpace(final File forFile) {
        String path = forFile.getPath();
        if (path.equals(File.separator))
            path = Environment.getRootDirectory().getAbsolutePath();
        StatFs stats;
        if (mStatFs.get(path) != null) {
            stats = mStatFs.get(path);
        } else {
            try {
                stats = new StatFs(path);
                mStatFs.put(path, stats);
            } catch (Throwable t) {
                t.printStackTrace();
                return null;
            }
        }
        final long total = StorageUtils.getTotalSpace(stats);
        final long free = StorageUtils.getAvailableSpace(stats);
        return mContext.getString(R.string.x_free_of_x,
                File.readableFileSize(free), File.readableFileSize(total));
    }

    @SuppressWarnings("ResourceType")
    @Override
    public void onBindViewHolder(ShortcutViewHolder holder, int index) {
        holder.item.setTag(index);
        holder.item.setOnClickListener(this);

        final ColorStateList navIconSl = ColorStateList.valueOf(navIconColor);
        final ColorStateList iconColorSl = mCheckedPos == index ? ThemeSingleton.get().positiveColor : navIconSl;
        final ColorStateList textColorSl = mCheckedPos == index ? ThemeSingleton.get().positiveColor : navIconSl;
        final ColorStateList textColorSubSl = mCheckedPos == index ? ThemeSingleton.get().positiveColor : navIconSl;

        BookmarkProvider.Item item = mItems.get(index);
        item.index = index;
        holder.item.setActivated(mCheckedPos == index);

        holder.divider.setVisibility(View.GONE);
        holder.subtitle.setVisibility(View.GONE);
        holder.item.setOnLongClickListener(this);

        if (index > 0) {
            BookmarkProvider.Item li = mItems.get(index - 1);
            if (mShowAddExternal && li.actionId == ACTION_ADD_EXTERNAL_STORAGE) {
                // Show divider underneath add external storage option
                holder.divider.setVisibility(View.VISIBLE);
            } else if (!mShowAddExternal && li.isMain && !item.isMain) {
                // If the add external storage option is hidden
                // Display a divider under the last main item (e.g. internal storage)
                holder.divider.setVisibility(View.VISIBLE);
            }
        }

        // Setup plugin account expand button
        if (item.isPlugin()) {
            boolean hasAccounts = item.pluginHasAccounts(mContext);
            if (hasAccounts) {
                holder.expand.setOnClickListener(this);
                holder.expand.setVisibility(View.VISIBLE);
                holder.expand.setTag("accountswitch:" + index);
                holder.expand.setColorFilter(iconColorSl.getDefaultColor(), PorterDuff.Mode.SRC_ATOP);
            } else {
                holder.expand.setOnClickListener(null);
                holder.expand.setVisibility(View.GONE);
            }
        } else {
            holder.expand.setOnClickListener(null);
            holder.expand.setVisibility(View.GONE);
        }

        File file = null;
        switch (item.actionId) {
            case ACTION_ADD_EXTERNAL_STORAGE:
                holder.title.setText(R.string.add_document_tree_path);
                holder.icon.setImageResource(R.drawable.ic_drawer_add);
                break;
            case ACTION_DONATE:
                holder.title.setText(R.string.support_dev);
                holder.icon.setImageResource(R.drawable.ic_drawer_donate);
                holder.divider.setVisibility(View.VISIBLE);
                holder.item.setOnLongClickListener(null);
                break;
            case ACTION_SETTINGS:
                holder.title.setText(R.string.settings);
                holder.icon.setImageResource(R.drawable.ic_drawer_settings);
                holder.item.setOnLongClickListener(null);
                break;
            case ACTION_HELPFEEDBACK:
                holder.title.setText(R.string.helpandfeedback_title);
                holder.icon.setImageResource(R.drawable.ic_drawer_help);
                holder.item.setOnLongClickListener(null);
                break;
            default:
                file = item.asFile(mContext);
                break;
        }

        if (file != null) {
            if (file instanceof PluginFileImpl) {
                holder.title.setText(item.getDisplay(mContext, true));
            } else if (file.isRoot()) {
                if (mStatFs != null) {
                    String freespace = getFreeSpace(file);
                    if (freespace != null) {
                        holder.subtitle.setVisibility(View.VISIBLE);
                        holder.subtitle.setText(freespace);
                    }
                }
                holder.title.setText(R.string.root);
            } else if (file.isStorageDirectory()) {
                if (mStatFs != null) {
                    String freespace = getFreeSpace(file);
                    if (freespace != null) {
                        holder.subtitle.setVisibility(View.VISIBLE);
                        holder.subtitle.setText(freespace);
                    }
                }
                holder.title.setText(R.string.internal_storage);
            } else if (file.isSDCardDirectory()) {
                if (mStatFs != null) {
                    String freespace = getFreeSpace(file);
                    if (freespace != null) {
                        holder.subtitle.setVisibility(View.VISIBLE);
                        holder.subtitle.setText(freespace);
                    }
                }
                holder.title.setText(R.string.external_storage);
            } else {
                holder.title.setText(item.getDisplay(mContext, true));
            }
            loadThumbnail(item, holder.icon);
        }

        holder.icon.setColorFilter(iconColorSl.getDefaultColor(), PorterDuff.Mode.SRC_ATOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            holder.icon.setImageAlpha(mCheckedPos == index ? ALPHA_ACTIVATED : ALPHA_ICON);
        } else {
            // noinspection deprecation
            holder.icon.setAlpha(mCheckedPos == index ? ALPHA_ACTIVATED : ALPHA_ICON);
        }

        holder.title.setTextColor(textColorSl);
        holder.title.setAlpha(mCheckedPos == index ? ALPHA_ACTIVATED : ALPHA_TEXT);
        holder.subtitle.setTextColor(textColorSubSl);
        holder.subtitle.setAlpha(mCheckedPos == index ? ALPHA_ACTIVATED : ALPHA_ICON);
    }

    private void loadThumbnail(BookmarkProvider.Item item, ImageView icon) {
        Drawable pluginIcon = item.getPluginIcon(mContext);
        if (pluginIcon != null) {
            icon.setImageDrawable(pluginIcon);
            return;
        }
        final String p = Uri.parse(item.uri).getPath().toLowerCase(Locale.getDefault());
        if (p.equals(File.separator)) {
            icon.setImageResource(R.drawable.ic_drawer_root);
        } else if (p.equals(Environment.getExternalStorageDirectory().getAbsolutePath()) ||
                item.isDocumentTree()) {
            icon.setImageResource(R.drawable.ic_drawer_storage);
        } else if (App.getSDCard() != null && p.equals(App.getSDCard().path)) {
            icon.setImageResource(R.drawable.ic_drawer_storage);
        } else if (p.contains("dcim") || p.contains("camera") ||
                p.contains("video") || p.contains("movie")) {
            icon.setImageResource(R.drawable.ic_drawer_camera);
        } else if (p.contains("download")) {
            icon.setImageResource(R.drawable.ic_drawer_download);
        } else if (p.contains("music") || p.contains("audio") ||
                p.contains("ringtone") || p.contains("notification") ||
                p.contains("podcast") || p.contains("alarm")) {
            icon.setImageResource(R.drawable.ic_drawer_audio);
        } else if (p.contains("picture") || p.contains("instagram")) {
            icon.setImageResource(R.drawable.ic_drawer_photo);
        } else {
            icon.setImageResource(R.drawable.ic_drawer_folder);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }
}