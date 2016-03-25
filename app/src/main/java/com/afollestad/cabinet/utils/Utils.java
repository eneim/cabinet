package com.afollestad.cabinet.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cram.ArchiveType;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.content.ContentFragment;
import com.afollestad.cabinet.fragments.content.DirectoryFragment;
import com.afollestad.cabinet.plugins.IPluginService;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginErrorResult;
import com.afollestad.cabinet.plugins.PluginUriResult;
import com.afollestad.cabinet.plugins.base.PluginConstants;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.zip.Unzipper;
import com.afollestad.cabinet.zip.Zipper;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class Utils {

    @DrawableRes
    public static int resolveDrawable(Context context, @AttrRes int drawable) {
        if (context == null) return 0;
        TypedArray a = context.obtainStyledAttributes(new int[]{drawable});
        int resId = a.getResourceId(0, 0);
        a.recycle();
        return resId;
    }

    @ColorInt
    public static int resolveColor(Context context, @AttrRes int color) {
        @ColorInt
        final int defaultColor = ContextCompat.getColor(context, R.color.cabinet_color);
        TypedArray a = context.obtainStyledAttributes(new int[]{color});
        int resId = a.getColor(0, defaultColor);
        a.recycle();
        return resId;
    }

    public static void lockOrientation(Activity context) {
        int currentOrientation = context.getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    public static void unlockOrientation(Activity context) {
        context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private static boolean existsInTree(ArrayList<DocumentFile> tree, String displayName, String mime, boolean dir) {
        if (tree != null) {
            for (Iterator<DocumentFile> iterator = tree.iterator(); iterator.hasNext(); ) {
                DocumentFile fi = iterator.next();
                if (fi.getName() == null) continue;
                if (dir) {
                    if (fi.isDirectory() && fi.getName().equals(displayName))
                        return true;
                } else if (mime != null && mime.equals("application/x-gzip") &&
                        fi.getName().equals(displayName + ".tar.gz")) {
                    return true;
                } else if (!fi.isDirectory() && fi.getName().equals(displayName) &&
                        ((mime == null && fi.getType() == null) ||
                                (mime != null && mime.equals(fi.getType())))) {
                    return true;
                }
                if (!fi.getName().startsWith(displayName))
                    iterator.remove();
            }
        }
        return false;
    }

    public static String checkDuplicatesTree(DocumentFile parent, String displayName, String mime, boolean dir) {
        ArrayList<DocumentFile> files = new ArrayList<>();
        Collections.addAll(files, parent.listFiles());

        if (existsInTree(files, displayName, mime, dir)) {
            int checks = 1;
            while (true) {
                String newName = displayName + " (" + checks + ")";
                if (mime != null && mime.equals(ArchiveType.CompressedTar.value()))
                    newName += ".tar.gz";
                if (!existsInTree(files, newName, mime, dir)) {
                    return newName;
                }
                checks++;
            }
        } else if (mime != null && mime.equals(ArchiveType.CompressedTar.value()))
            displayName += ".tar.gz";
        return displayName;
    }

    @WorkerThread
    public static File checkDuplicates(final Context context, final File file) {
        return checkDuplicates(context, file, file.getNameNoExtension());
    }

    @WorkerThread
    private static File checkDuplicates(final Context context, File file,
                                        final String originalNameNoExt) {
        if (context == null) return null;
        Log.v("checkDuplicatesSync", "Checking: " + file.getUri());
        if (file.exists()) {
            int checks = 1;
            while (true) {
                String newName = originalNameNoExt + " (" + checks + ")";
                String extension = file.getExtension();
                if (!extension.isEmpty())
                    newName += "." + extension;
                File newFile = File.getNewFile(context, file.getParent(), newName, file.isDirectory(), false);
                if (!newFile.exists()) {
                    file = newFile;
                    break;
                }
                checks++;
            }
        }
        return file;
    }

    @SuppressLint("CommitPrefEdits")
    public static void setLastOpenFolder(Activity context, File file) {
        if (context == null) return;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("remember_last_folder", true))
            file = null;
        if (file != null) {
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("last_folder_path", file.getUri().toString())
                    .remove("last_folder_plugin")
                    .remove("last_folder_remote")
                    .commit();
        } else {
            prefs.edit().remove("last_folder_path")
                    .remove("last_folder_plugin")
                    .remove("last_folder_remote")
                    .commit();
        }
    }

    public static File getLastOpenFolder(PluginActivity context) {
        if (context == null) return null;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String path = prefs.getString("last_folder_path", null);
        if (path == null) return null;
        return File.fromUri(context, context.getPlugins(), Uri.parse(path), false);
    }

    public static void setSorter(DirectoryFragment fragment, int sorter) {
        if (fragment == null || fragment.getActivity() == null) return;
        PreferenceManager.getDefaultSharedPreferences(fragment.getActivity()).edit().putInt("sorter", sorter).commit();
        fragment.mAdapter.showLastModified = (sorter == 5);
        fragment.sorter = sorter;
        fragment.updateComparator();
    }

    public static int getSorter(Context context) {
        if (context == null) return 0;
        return PreferenceManager.getDefaultSharedPreferences(context).getInt("sorter", 0);
    }

    public static void setGridSize(DirectoryFragment context, int size) {
        if (context == null || context.getActivity() == null) return;
        final boolean landscape = context.getResources()
                .getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        PreferenceManager.getDefaultSharedPreferences(context.getActivity()).edit()
                .putInt("grid_size_" + (landscape ? "land" : "port"), size).commit();
        context.changeLayout();
    }

    private static boolean isTablet(Context context) {
        return context != null && (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static int getGridSize(Context context) {
        if (context == null) return 1;
        final boolean landscape = context.getResources()
                .getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        final int defaultSize = isTablet(context) ? context.getResources().getInteger(R.integer.grid_columns) : 1;
        final int maxWidth = context.getResources().getInteger(R.integer.max_grid_width);
        int gridSize = -1;

        if (landscape && defaultSize == 1) {
            final int portraitValue = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt("grid_size_port", defaultSize);
            if (portraitValue > 1) {
                // This uses more than 1 grid by default in landscape even on phones if portrait is not set to 1 column
                gridSize = PreferenceManager.getDefaultSharedPreferences(context)
                        .getInt("grid_size_land", portraitValue + 1);
            }
        }
        if (gridSize == -1) {
            gridSize = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt("grid_size_" + (landscape ? "land" : "port"), defaultSize);
        }
        if (maxWidth > -1 && gridSize > maxWidth)
            gridSize = maxWidth;
        return gridSize;
    }

    public static void setCompactMode(DirectoryFragment context, boolean compact) {
        if (context == null || context.getActivity() == null) return;
        PreferenceManager.getDefaultSharedPreferences(context.getActivity()).edit()
                .putBoolean("compact_mode", compact).apply();
        context.changeLayout();
    }

    public static boolean isCompactMode(Context context) {
        return context != null &&
                PreferenceManager.getDefaultSharedPreferences(context).getBoolean("compact_mode", false);
    }

    public static void setFilter(DirectoryFragment fragment, String filter) {
        if (fragment == null || fragment.getActivity() == null) return;
        PreferenceManager.getDefaultSharedPreferences(fragment.getActivity()).edit().putString("filter", filter).commit();
        fragment.filter = filter;
        fragment.reload(true);
        fragment.jumpToTop(false);
        fragment.showAppBarAndInvalidateTopPadding();
    }

    public static String getFilter(Context context) {
        if (context == null) return null;
        return PreferenceManager.getDefaultSharedPreferences(context).getString("filter", null);
    }

    public static void setShowHidden(DirectoryFragment fragment, boolean show) {
        if (fragment == null || fragment.getActivity() == null) return;
        PreferenceManager.getDefaultSharedPreferences(fragment.getActivity()).edit().putBoolean("show_hidden", show).commit();
        fragment.showHidden = show;
        fragment.reload(false);
        fragment.getActivity().invalidateOptionsMenu();
    }

    public static boolean getShowHidden(Context context) {
        return context != null && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("show_hidden", false);
    }

    public static void showConfirmDialog(Activity context, int title, int message, Object replacement, final ClickListener callback) {
        try {
            new MaterialDialog.Builder(context)
                    .title(title)
                    .content(Html.fromHtml(context.getString(message, replacement)))
                    .positiveText(android.R.string.ok)
                    .negativeText(android.R.string.cancel)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            if (callback != null)
                                callback.onPositive(0, null);
                        }
                    }).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showErrorDialog(Context context, int message, Exception e) {
        if (context == null || e == null) return;
        showErrorDialog(context, context.getString(message, e.getMessage()));
    }

    public static void showErrorDialog(final Context context, final CharSequence message) {
        try {
            if (context == null) return;
            new MaterialDialog.Builder(context)
                    .title(R.string.error)
                    .content(message)
                    .positiveText(android.R.string.ok)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showInputDialog(Context context, @StringRes int title, @StringRes int hint, @Nullable CharSequence prefillInput, InputCallback callback) {
        showInputDialog(context, context.getString(title), hint, prefillInput, false, false, callback);
    }

    public static void showInputDialog(final Context context, String title, @StringRes int hint, @Nullable CharSequence prefillInput, boolean password, boolean lowerCase, final InputCallback callback) {
        try {
            final MaterialDialog dialog = new MaterialDialog.Builder(context)
                    .title(title)
                    .inputType(password ?
                                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD :
                                    lowerCase ? InputType.TYPE_CLASS_TEXT :
                                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    )
                    .cancelable(!password)
                    .input(hint == 0 ? "" : context.getResources().getString(hint), prefillInput, new MaterialDialog.InputCallback() {
                        @Override
                        public void onInput(MaterialDialog materialDialog, CharSequence charSequence) {
                            callback.onInput(charSequence.toString());
                        }
                    })
                    .cancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            if (callback instanceof InputCancelCallback) {
                                ((InputCancelCallback) callback).onCancel();
                            }
                        }
                    })
                    .dismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (callback instanceof InputCancelCallback) {
                                ((InputCancelCallback) callback).onDismiss();
                            }
                        }
                    })
                    .positiveText(android.R.string.ok)
                    .negativeText(android.R.string.cancel)
                    .build();

            final EditText input = dialog.getInputEditText();
            if (input != null) {
                if (!password) {
                    input.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            if (s.toString().contains("/")) {
                                input.setError(context.getString(R.string.slash_name_invalid));
                                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
                            } else {
                                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                        }
                    });
                }
            }
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void openFile(final ContentFragment context, final File item, final boolean openAs) {
        if (item.getPluginPackage() != null) {
            final Handler uiThread = new Handler();
            final MaterialDialog dialog = new MaterialDialog.Builder(context.getActivity())
                    .content(R.string.connecting)
                    .progress(true, -1)
                    .cancelable(true)
                    .show();
            PluginFileImpl pfi = (PluginFileImpl) item;
            pfi.verifyConnection(uiThread, true, new Plugin.Callback() {
                @Override
                public void onResult(Plugin plugin, Exception e) {
                    if (dialog.isCancelled() || !context.isAdded())
                        return;
                    else if (e != null) {
                        dialog.dismiss();
                        Utils.showErrorDialog(context.getActivity(), R.string.failed_open_plugin_file, e);
                        return;
                    }
                    dialog.setContent(Html.fromHtml(context.getString(R.string.downloading_file, item.getName())));
                    BackgroundThread.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                PluginFileImpl pfi = (PluginFileImpl) item;
                                IPluginService service = pfi.getPlugin().getService();
                                if (pfi.getPluginAccount() != null) {
                                    if (!pfi.getPluginAccount().equals(service.getCurrentAccount())) {
                                        if (service.isConnected())
                                            service.disconnect();
                                        PluginErrorResult result = service.setCurrentAccount(pfi.getPluginAccount());
                                        if (result != null && result.getError() != null)
                                            throw new Exception(result.getError());
                                        result = service.connect();
                                        if (result != null && result.getError() != null)
                                            throw new Exception(result.getError());
                                    }
                                }
                                PluginUriResult result = service.openFile(pfi.getWrapper(), true);
                                if (dialog.isCancelled())
                                    return;
                                if (result.getError() != null)
                                    throw new Exception(result.getError());
                                Uri uri = result.getUri();
                                try {
                                    String mime = null;
                                    if (uri.getScheme() == null || uri.getScheme().equals("file")) {
                                        mime = File.getMimeType(File.getExtension(File.getName(uri)));
                                    } else if (uri.getScheme().equals("content")) {
                                        mime = context.getActivity().getContentResolver().getType(uri);
                                    }
                                    if (mime == null) mime = "*/*";
                                    Intent intent = new Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(uri, mime);
                                    context.startActivity(intent);
                                } catch (ActivityNotFoundException e) {
                                    Toast.makeText(context.getActivity(), R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    Toast.makeText(context.getActivity(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                } finally {
                                    uiThread.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (dialog.isShowing())
                                                dialog.dismiss();
                                        }
                                    });
                                }
                            } catch (final Exception e) {
                                if (!context.isAdded() || context.getActivity() == null) return;
                                uiThread.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        dialog.dismiss();
                                        Utils.showErrorDialog(context.getActivity(), R.string.failed_open_plugin_file, e);
                                    }
                                });
                            }
                        }
                    });
                }
            });
        } else {
            openLocal(context, item, openAs ? null : item.getMimeType());
        }
    }

    private static void openLocal(final ContentFragment context, final File file, String mime) {
        if (context == null || file == null) return;
        final Activity act = context.getActivity();
        if (file.isViewableArchive(act)) {
            mime = "application/zip";
        } else if (file.isArchiveOrInArchive(act)) {
            new MaterialDialog.Builder(context.getActivity())
                    .title(R.string.auto_extract)
                    .content(R.string.auto_extract_prompt)
                    .positiveText(R.string.yes)
                    .negativeText(android.R.string.cancel)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            if (context.getActivity() == null)
                                return;
                            //noinspection unchecked
                            Unzipper.unzip((PluginActivity) context.getActivity(), file.getParent(), Collections.singletonList(file), new Zipper.ZipCallback() {
                                @Override
                                public void onComplete() {
                                    context.reload(true);
                                }
                            });
                        }
                    }).show();
            return;
        }
        if (mime == null) {
            try {
                new MaterialDialog.Builder(context.getActivity())
                        .title(R.string.open_as)
                        .items(R.array.open_as_array)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                String newMime;
                                switch (which) {
                                    default:
                                        newMime = "text/*";
                                        break;
                                    case 1:
                                        newMime = "image/*";
                                        break;
                                    case 2:
                                        newMime = "audio/*";
                                        break;
                                    case 3:
                                        newMime = "video/*";
                                        break;
                                    case 4:
                                        context.saveScrollPosition();
                                        ((MainActivity) act).switchDirectory(file);
                                        return;
                                    case 5:
                                        newMime = "*/*";
                                        break;
                                }
                                openLocal(context, file, newMime);
                            }
                        }).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(file.getUri(), mime);
            if (file.isDocumentTreeFile()) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context.getActivity(), R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(context.getActivity(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isRTL(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Configuration config = context.getResources().getConfiguration();
            return config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        } else return false;
    }

    public static void addHomescreenShortcut(final Activity context, final File to) {
        if (context == null) return;
        final String prefill =
                to.isStorageDirectory() ? context.getString(R.string.internal_storage) :
                        to.isSDCardDirectory() ? context.getString(R.string.external_storage) : to.getName();
        new MaterialDialog.Builder(context)
                .title(R.string.shortcut_nickname)
                .positiveText(R.string.add)
                .input(context.getString(R.string.nickname), prefill, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(MaterialDialog materialDialog, CharSequence charSequence) {
                        String name = prefill;
                        if (charSequence.toString().trim().length() > 0)
                            name = charSequence.toString().trim();
                        Intent shortcutIntent = new Intent(context, MainActivity.class)
                                .setAction(Intent.ACTION_MAIN)
                                .putExtra(PluginConstants.SHORTCUT_PATH, to.getUri().toString());
                        Intent addIntent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT")
                                .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                                .putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
                                .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                                        Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_launcher));
                        context.sendBroadcast(addIntent);
                        Toast.makeText(context, context.getString(R.string.shortcut_added, to.getDisplay(context)), Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    public interface InputCallback {
        void onInput(String input);
    }

    public interface InputCancelCallback extends InputCallback {
        void onCancel();

        void onDismiss();
    }

    public interface ClickListener {
        void onPositive(int which, View view);
    }
}