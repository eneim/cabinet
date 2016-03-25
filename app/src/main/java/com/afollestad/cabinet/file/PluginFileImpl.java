package com.afollestad.cabinet.file;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginErrorResult;
import com.afollestad.cabinet.plugins.PluginFile;
import com.afollestad.cabinet.plugins.PluginFileResult;
import com.afollestad.cabinet.plugins.PluginLsResult;
import com.afollestad.cabinet.plugins.PluginUriResult;
import com.stericson.RootShell.exceptions.RootDeniedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class PluginFileImpl extends File {

    private static final long serialVersionUID = -4299220724942581929L;

    private final transient Plugin mPlugin;
    private final PluginFile mWrap;
    private String mDirectoryFileCount;

    public PluginFileImpl(Uri uri, Plugin plugin) {
        super(uri);
        mPlugin = plugin;
        mWrap = new PluginFile.Builder(null, plugin.getPackage())
                .path(getPath())
                .build();
    }

    public PluginFileImpl(PluginFile wrap, String account, Plugin plugin) {
        super(Uri.parse("plugin://" + wrap.getPackage() +
                (plugin.hasAccounts() && account != null ? "/" + account : "") +
                (wrap.getPath().startsWith("/") ? "" : "/") + wrap.getPath()));
        mPlugin = plugin;
        mWrap = new PluginFile.Builder(null, plugin.getPackage())
                .path(getPath())
                .build();
    }

    public PluginFileImpl(PluginFileImpl parent, PluginFile wrap) {
        super(Uri.parse("plugin://" + parent.getPluginPackage() +
                (parent.getPlugin().hasAccounts() ? "/" + parent.getPluginAccount() : "") +
                (wrap.getPath().startsWith("/") ? "" : "/") + wrap.getPath()));
        mPlugin = parent.mPlugin;
        mWrap = wrap;
    }

    public static String getPluginAccount(Uri uri, Plugin plugin) {
        if (uri == null || plugin == null || !plugin.hasAccounts())
            return null;
        String path = uri.getPath();
        if (path == null || !path.contains(File.separator)) return null;
        else if (path.startsWith(File.separator)) path = path.substring(1);
        if (path.trim().isEmpty()) return null;
        return path.substring(0, path.indexOf(File.separatorChar));
    }

    public static String getPath(Uri uri, Plugin plugin) {
        if (uri == null) return null;
        String path = uri.getPath();
        if (plugin == null || !plugin.hasAccounts())
            return path;
        if (path == null || !path.contains(File.separator)) return path;
        else if (path.startsWith(File.separator)) path = path.substring(1);
        if (path.trim().isEmpty()) return null;
        return path.substring(path.indexOf(File.separatorChar));
    }

    @Override
    public boolean isHidden() {
        return mWrap.isHidden();
    }

    public PluginFile getWrapper() {
        return mWrap;
    }

    public Plugin getPlugin() {
        return mPlugin;
    }

    @Override
    public PluginFileImpl getParent() {
        if (mWrap == null)
            return null;
        PluginFile parent = mWrap.getParent();
        if (parent == null && !mWrap.getPath().equals(File.separator)) {
            parent = new PluginFile.Builder(null, mWrap.getPackage())
                    .isDir(true)
                    .path(File.separator)
                    .build();
        }
        if (parent == null)
            return null;
        return new PluginFileImpl(parent, getPluginAccount(), mPlugin);
    }

    @Override
    public void createFile(Context context) throws RemoteException, FileCreationException {
        PluginFileResult result = mPlugin.getService().makeFile(getName(), getParent().getWrapper());
        if (result != null && result.getError() != null)
            throw new FileCreationException(result.getError());
    }

    @Override
    public void mkdir(Context context) throws FileCreationException, IOException, RootDeniedException, TimeoutException, RemoteException {
        PluginFileResult result = mPlugin.getService().makeFolder(getName(), getParent().getWrapper());
        if (result != null && result.getError() != null)
            throw new FileCreationException(result.getError());
    }

    @Override
    public File copyFile(Activity context, File dest) throws RemoteException, ResultException, RootDeniedException, FileCreationException, TimeoutException, IOException {
        if (dest.getPluginPackage() != null) {
            // Remote to remote
            final PluginFileImpl dpf = (PluginFileImpl) dest;
            final PluginFileResult result = mPlugin.getService().copy(getWrapper(), dpf.getWrapper());
            if (result.getError() != null)
                throw new ResultException(result.getError());
            return new PluginFileImpl(result.getFile(), getPluginAccount(), getPlugin());
        } else {
            java.io.File cacheFile = null;
            try {
                // Remote to local
                Uri localUri = dest.getUri();
                if (dest.isDocumentTreeFile()) {
                    /**
                     * Plugins don't have permission to access SD card, so Cabinet will download
                     * the file to an internal cache file first, then copy it.
                     */
                    cacheFile = new java.io.File(context.getExternalCacheDir(),
                            dest.getPath().replace(File.separator, "_"));
                    localUri = Uri.fromFile(cacheFile);
                }
                final PluginUriResult result = mPlugin.getService().download(getWrapper(), localUri);
                if (result.getError() != null)
                    throw new ResultException(result.getError());
                if (cacheFile != null) {
                    // Now the cache file needs to be copied to the destination
                    return new LocalFile(cacheFile).copy(context, dest);
                } else {
                    // We're done
                    return fromUri(context, null, result.getUri(), true);
                }
            } finally {
                if (cacheFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheFile.delete();
                }
            }
        }
    }

    //TODO: Properly move file
    @Override
    public File moveFile(Activity context, File dest) throws RemoteException, ResultException, IOException, RootDeniedException, TimeoutException, FileCreationException {
        File result = copyFile(context, dest);
        delete(context);
        setUri(dest.getUri());
        return result;
    }

    @Override
    public boolean delete(Context context) throws RemoteException, ResultException {
        PluginErrorResult result = mPlugin.getService().remove(getWrapper());
        if (result != null && result.getError() != null)
            throw new ResultException(result.getError());
        return true;
    }

    @NonNull
    @Override
    public String getDirectoryFileCount() {
        return mDirectoryFileCount == null ? "" : mDirectoryFileCount;
    }

    @Override
    public void setDirectoryFileCount(String fileCount) {
        mDirectoryFileCount = fileCount;
    }

    @Override
    public boolean isViewableArchive(Context context) {
        final String ext = getExtension();
        final List<String> zipExtensions = Arrays.asList(
                context.getResources().getStringArray(R.array.viewable_archive_extensions));
        return zipExtensions.contains(ext);
    }

    @Override
    public boolean isDirectory() {
        return mWrap.isDir();
    }

    @Override
    public boolean exists() {
        try {
            return mPlugin.getService().exists(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public long length() {
        return mWrap.getLength();
    }

    @Override
    public List<File> listFiles() throws RemoteException, ResultException {
        PluginLsResult results = mPlugin.getService().listFiles(getWrapper());
        if (results.getError() != null)
            throw new ResultException(results.getError());
        List<File> returnVals = new ArrayList<>();
        for (PluginFile fi : results.getResults()) {
            PluginFileImpl add = new PluginFileImpl(this, fi);
            returnVals.add(add);
        }
        return returnVals;
    }

    @Override
    public long lastModified() {
        return mWrap.getModified();
    }

    @Override
    public String getPluginPackage() {
        Uri uri = getUri();
        if (uri == null) return null;
        return uri.getAuthority();
    }

    @Override
    public String getPluginAccount() {
        Uri uri = getUri();
        return getPluginAccount(uri, getPlugin());
    }

    @Override
    public String getPath() {
        Uri uri = getUri();
        return getPath(uri, getPlugin());
    }

    @Override
    public void chmod(int permissions) throws RemoteException, ResultException {
        PluginErrorResult result = mPlugin.getService().chmod(permissions, getWrapper());
        if (result != null && result.getError() != null)
            throw new ResultException(result.getError());
    }

    @Override
    public void chown(int uid) throws Exception {
        PluginErrorResult result = mPlugin.getService().chown(uid, getWrapper());
        if (result != null && result.getError() != null)
            throw new Exception(result.getError());
    }

    @NonNull
    @Override
    public String getPermissions(Context context) {
        return mWrap.getPermissions();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PluginFileImpl && ((PluginFileImpl) o).getUri().toString().equals(getUri().toString());
    }

    @Override
    public String getDisplay(Context context) {
        Uri uri = getUri();
        if (uri == null || File.isRootPluginFolder(uri, mPlugin)) {
            if (mPlugin == null)
                return "" + uri;
            String acc = getPluginAccount();
            if (acc != null) return acc;
            else return (String) mPlugin.getName();
        } else {
            return getName();
        }
    }

    public void verifyConnection(final Handler uiHandler, final boolean bindOnly, final Plugin.Callback callback) {
        getPlugin().verifyConnection(uiHandler, callback, bindOnly);
    }

    public static class ResultException extends Exception {
        public ResultException(String detailMessage) {
            super(detailMessage);
        }
    }
}