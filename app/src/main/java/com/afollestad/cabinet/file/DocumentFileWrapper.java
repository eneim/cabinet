package com.afollestad.cabinet.file;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.PluginFileResult;
import com.stericson.RootShell.exceptions.RootDeniedException;

import org.apache.commons.compress.utils.IOUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Aidan Follestad (afollestad)
 */
public class DocumentFileWrapper extends File {

    private static final long serialVersionUID = -4299220724947581929L;
    private final String mMime;
    private transient DocumentFile mWrap;
    private String mDirectoryFileCount;

    public DocumentFileWrapper(DocumentFile wrap) {
        super(wrap.getUri());
        mWrap = wrap;
        mMime = wrap.getType();
    }

    public DocumentFileWrapper(DocumentFile wrap, String mime) {
        super(wrap.getUri());
        mWrap = wrap;
        mMime = mime;
    }

    public DocumentFile getRawFile() {
        return mWrap;
    }

    public void restoreWrapped(Context context) {
        mWrap = DocumentFile.fromTreeUri(context, getUri());
    }

    @NonNull
    @Override
    public String getName() {
        return mWrap.getName() == null ? "" : mWrap.getName();
    }

    @Override
    public String getMimeType() {
        return mMime;
    }

    @Override
    public List<File> listFiles() {
        long start = System.nanoTime();
        DocumentFile[] results = mWrap.listFiles();
        List<File> returns = new ArrayList<>();
        for (DocumentFile fi : results) {
            DocumentFileWrapper newFi = new DocumentFileWrapper(fi);
            returns.add(newFi);
        }
        long end = System.nanoTime();
        long total = TimeUnit.NANOSECONDS.toMillis(end - start);
        Log.v("DocumentFile#listFiles", total + "ms");
        return returns;
    }

    @Override
    public boolean isHidden() {
        return mWrap.getName() != null && mWrap.getName().startsWith(".");
    }

    @Override
    public File getParent() {
        if (mWrap == null || mWrap.getParentFile() == null)
            return null;
        return new DocumentFileWrapper(mWrap.getParentFile());
    }

    @Override
    public void createFile(Context context) {
        mWrap.getParentFile().createFile(getMimeType(), getNameNoExtension());
    }

    @Override
    public void mkdir(Context context) throws FileCreationException, IOException, RootDeniedException, TimeoutException, RemoteException {
        if (getName().trim().isEmpty())
            throw new FileCreationException("File name is null.");
        mWrap.getParentFile().createDirectory(getName());
    }

    @Override
    public File copyFile(Activity context, File dest) throws IOException, RemoteException, PluginFileImpl.ResultException {
        java.io.File cacheFile = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = openInputStream(context);
            if (dest.getPluginPackage() != null) {
                /**
                 * Cabinet only has been granted SD card access, so the SD card file is copied
                 * to an internal storage cache file, so plugins can access it.
                 */
                cacheFile = new java.io.File(context.getExternalCacheDir(),
                        dest.getPath().replace(File.separator, "_"));
                out = new FileOutputStream(cacheFile);
            } else {
                // File is copied to a local location, e.g. internal storage or another SD card location
                out = dest.openOutputStream(context);
            }
            IOUtils.copy(in, out);
            if (cacheFile != null) {
                // Upload the cache file to the plugin
                PluginFileImpl pfi = (PluginFileImpl) dest;
                PluginFileResult result = pfi.getPlugin().getService().upload(Uri.fromFile(cacheFile), pfi.getWrapper());
                if (result.getError() != null)
                    throw new PluginFileImpl.ResultException(result.getError());
                //noinspection ResultOfMethodCallIgnored
                cacheFile.delete();
                return new PluginFileImpl(result.getFile(), pfi.getPluginAccount(), pfi.getPlugin());
            }
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
        return dest;
    }

    //TODO: Properly move file
    @Override
    public File moveFile(Activity context, File dest) throws RemoteException, PluginFileImpl.ResultException, IOException, RootDeniedException, TimeoutException, FileCreationException {
        File result = copyFile(context, dest);
        delete(context);
        setUri(dest.getUri());
        return result;
    }

    @Override
    public boolean delete(Context context) {
        /*if(!*/
        mWrap.delete();/*)
            throw new Exception("Failed to delete: " + this);*/
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
    public boolean isArchiveOrInArchive(Context context) {
        final String ext = getExtension();
        final List<String> zipExtensions = Arrays.asList(
                context.getResources().getStringArray(R.array.supported_archive_extensions_sd));
        return zipExtensions.contains(ext);
    }

    @Override
    public boolean isDirectory() {
        return mWrap.isDirectory();
    }

    @Override
    public boolean exists() {
        return mWrap.exists();
    }

    @Override
    public long length() {
        return mWrap.length();
    }

    @Override
    public long lastModified() {
        return mWrap.lastModified();
    }

    @Override
    public String getPluginPackage() {
        return null;
    }

    @Override
    public void chmod(int permissions) throws OperationNotSupported {
        throw new OperationNotSupported("chmod not supported.");
    }

    @Override
    public void chown(int uid) throws Exception {
        throw new Exception("chown not supported.");
    }

    @NonNull
    @Override
    public String getPermissions(Context context) {
        return "";
    }
}