package com.afollestad.cabinet.file.base;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cram.ArchiveType;
import com.afollestad.cabinet.file.DocumentFileWrapper;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginFile;
import com.afollestad.cabinet.plugins.PluginFramework;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stericson.RootShell.exceptions.RootDeniedException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public abstract class File implements Serializable {

    public static final String separator = java.io.File.separator;
    public static final char separatorChar = java.io.File.separatorChar;
    private static final long serialVersionUID = -4536307733169074073L;
    private static final String DOCUMENT_TREE_AUTHORITY = "com.android.externalstorage.documents";
    private String mUri;

    protected File() {
    }

    protected File(Uri uri) {
        mUri = uri.toString();
    }

    public static String readableFileSize(long size) {
        if (size <= 0) return size + " B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @NonNull
    public static String getExtension(String name) {
        if (name == null || !name.contains(".")) return "";
        final String ext = name.substring(name.lastIndexOf('.') + 1);
        if (ext.equalsIgnoreCase("gz")) {
            final String withoutExt = name.substring(0, name.lastIndexOf('.'));
            final String subExt = withoutExt.substring(withoutExt.lastIndexOf('.') + 1);
            return (subExt + "." + ext).toLowerCase();
        }
        return ext.toLowerCase();
    }

    public static String getMimeType(String extension) {
        if (extension != null) {
            if (extension.equalsIgnoreCase("epub"))
                return "application/epub+zip";
            else if (extension.equalsIgnoreCase("tar.gz"))
                return "application/x-gzip";
        }
        String type = null;
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
            if (type == null)
                return "text/plain";
        }
        return type;
    }

    @NonNull
    public static String getName(Uri uri) {
        if (uri == null || uri.getPath() == null)
            return "(null)";
        String path = uri.getPath();
        if (path.endsWith(File.separator))
            path = path.substring(0, path.length() - 1);
        if (path.contains(File.separator)) {
            path = path.substring(path.lastIndexOf(File.separatorChar) + 1);
            if (path.trim().isEmpty())
                path = File.separator;
        }
        return path;
    }

    public static String getNameNoExtension(String name) {
        if (name == null) return null;
        else if (name.startsWith(".") || !name.substring(1).contains("."))
            return name;
        name = name.substring(0, name.lastIndexOf('.'));
        if (name.toLowerCase(Locale.getDefault()).endsWith(".tar"))
            name = name.substring(0, name.lastIndexOf('.'));
        return name;
    }

    public static File fromUri(PluginActivity context, Uri uri, boolean useSingleUri) {
        return fromUri(context, context.getPlugins(), uri, useSingleUri);
    }

    private static String getRealPath(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
            if (cursor != null && cursor.moveToFirst())
                return cursor.getString(0);
            return null;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public static File fromUri(Context context, List<Plugin> plugins, Uri uri, boolean useSingleUri) {
        if (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase("file")) {
            return new LocalFile(uri);
        } else if (uri.getScheme().equals("content")) {
            if (uri.getAuthority() == null || !uri.getAuthority().equals(File.DOCUMENT_TREE_AUTHORITY)) {
                LocalFile fi = new LocalFile(Uri.parse("file://" + getRealPath(context, uri)));
                if (!fi.exists()) return null;
                return fi;
            }
            DocumentFile fi = useSingleUri ?
                    DocumentFile.fromSingleUri(context, uri) :
                    DocumentFile.fromTreeUri(context, uri);
            return new DocumentFileWrapper(fi);
        } else if (uri.getScheme().equals("plugin")) {
            if (plugins == null)
                plugins = new PluginFramework(context).query();
            if (plugins == null || plugins.size() == 0)
                return null;
            final String pkg = uri.getAuthority();
            Plugin p = null;
            for (Plugin pl : plugins) {
                if (pl.getPackage().equals(pkg)) {
                    p = pl;
                    break;
                }
            }
            if (p == null)
                return null;
            final String account = PluginFileImpl.getPluginAccount(uri, p);
            final String path = PluginFileImpl.getPath(uri, p);
            PluginFile pf = new PluginFile.Builder(null, pkg)
                    .path(path)
                    .build();
            return new PluginFileImpl(pf, account, p);
        }
        return null;
    }

    public static boolean isRootPluginFolder(Uri uri, Plugin plugin) {
        if (plugin == null) return false;
        else if (uri != null && uri.getScheme() != null &&
                uri.getScheme().equalsIgnoreCase("plugin") &&
                uri.getAuthority() != null && !uri.getAuthority().isEmpty()) {
            final String path = PluginFileImpl.getPath(uri, plugin);
            return path == null || path.isEmpty() || path.equalsIgnoreCase(File.separator);
        }
        return false;
    }

    public static File getNewFile(Context context, @NonNull File parent, String newName,
                                  boolean isDir, boolean checkDuplicates) {
        if (newName.startsWith(File.separator))
            newName = newName.substring(1);

        final File newFile;

        if (parent.isDocumentTreeFile()) {
            DocumentFile raw = ((DocumentFileWrapper) parent).getRawFile();
            String nameNoExtension = getNameNoExtension(newName);
            String mime = getMimeType(getExtension(newName));
            if (checkDuplicates) {
                newName = Utils.checkDuplicatesTree(raw, nameNoExtension, isDir ? null : mime, isDir);
                checkDuplicates = false;
            } else if (mime != null && mime.equals(ArchiveType.CompressedTar.value()))
                newName += ".tar.gz";
            if (isDir) {
                raw = raw.createDirectory(newName);
            } else {
                raw = raw.createFile(mime, newName);
            }
            newFile = new DocumentFileWrapper(raw, mime);


        } else if (parent.getPluginPackage() != null) {
            PluginFileImpl pfi = (PluginFileImpl) parent;
            PluginFile newFi = new PluginFile.Builder(pfi.getWrapper(), pfi.getPluginPackage())
                    .path(pfi.getPath() + "/" + newName)
                    .isDir(isDir)
                    .build();
            newFile = new PluginFileImpl(pfi, newFi);


        } else {
            newFile = new LocalFile((LocalFile) parent, newName);
            if (isDir)
                ((LocalFile) newFile).forceIsDir();
        }


        if (checkDuplicates) {
            try {
                return Utils.checkDuplicates(context, newFile);
            } catch (Exception e) {
                return newFile;
            }
        } else {
            return newFile;
        }
    }

    public static List<File> filter(List<File> files, boolean includeHidden, FileFilter filter) {
        for (Iterator<File> iterator = files.iterator(); iterator.hasNext(); ) {
            File file = iterator.next();
            if (!includeHidden && file.isHidden()) {
                iterator.remove();
            }
            if (filter != null && !filter.accept(file)) {
                iterator.remove();
            }
        }
        return files;
    }

    public static List<File> searchRecursive(File file, boolean includeHidden, FileFilter filter) throws Exception {
        Log.v("SearchRecursive", "Searching: " + file.getUri().toString());
        List<File> all = filter(file.listFiles(), includeHidden, filter);
        if (all == null || all.size() == 0) {
            Log.v("SearchRecursive", "No files in " + file.getUri().toString());
            return null;
        }
        List<File> matches = new ArrayList<>();
        matches.addAll(all);
        for (File fi : all) {
            try {
                if (fi.isDirectory()) {
                    List<File> subResults = searchRecursive(fi, includeHidden, filter);
                    if (subResults != null && subResults.size() > 0)
                        matches.addAll(subResults);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return matches;
    }

    public boolean dirWriteUnavailableForOther() {
        return false;
    }

    public boolean writeUnavailableForOther() {
        return false;
    }

    public boolean readUnavailableForOther() {
        return false;
    }

    public boolean readAvailable() {
        return true;
    }

    public String getDisplay(Context context) {
        String name = getName();
        if (context == null)
            return name;
        else if (isRoot()) {
            return context.getString(R.string.root);
        } else if (isStorageDirectory())
            return context.getString(R.string.storage);
        else if (isSDCardDirectory())
            return context.getString(R.string.external_storage);
        return name;
    }

    @WorkerThread
    protected long recursiveSize(File dir, boolean dirs, boolean bytes) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException {
        List<File> contents = dir.listFiles();
        long count = 0;
        for (File f : contents) {
            if (f.isDirectory()) {
                if (dirs) count++;
                count += recursiveSize(f, dirs, bytes);
            } else if (!dirs) {
                if (bytes) count += f.length();
                else count++; // One for regular file
            }
        }
        return count;
    }

    @WorkerThread
    public String getSizeString(Context context) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException {
        if (isDirectory()) {
            if (context == null) return "(no context)";
            return context.getString(R.string.x_files, recursiveSize(this, false, false)) + ", " +
                    context.getString(R.string.x_dirs, recursiveSize(this, true, false)) + ", " +
                    readableFileSize(recursiveSize(this, false, true));
        } else {
            return readableFileSize(length());
        }
    }

    @NonNull
    public final String getExtension() {
        if (isDirectory()) return "";
        return getExtension(getName());
    }

    public String getMimeType() {
        return getMimeType(getExtension());
    }

    public final boolean isRoot() {
        Uri uri = getUri();
        return uri != null && uri.getPath() != null &&
                uri.getPath().equals("/");
    }

    public abstract boolean isHidden();

    public final boolean isStorageDirectory() {
        Uri uri = getUri();
        return uri != null && uri.getPath().equals(Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    public final boolean isSDCardDirectory() {
        Uri uri = getUri();
        return uri != null && uri.getPath() != null &&
                App.getSDCard() != null && uri.getPath().equals(App.getSDCard().path);
    }

    public final boolean isDocumentTreeFile() {
        Uri uri = getUri();
        return uri != null && uri.getScheme() != null && uri.getScheme().equals("content") &&
                uri.getAuthority() != null && uri.getAuthority().equals(DOCUMENT_TREE_AUTHORITY);
    }

    @NonNull
    public String getName() {
        return getName(getUri());
    }

    public abstract File getParent();

    public final String getNameNoExtension() {
        if (isDirectory())
            return getName();
        return getNameNoExtension(getName());
    }

    public String getPath() {
        Uri uri = getUri();
        if (uri == null) return null;
        return uri.getPath();
    }

    public Uri getUri() {
        if (mUri == null)
            return null;
        return Uri.parse(mUri);
    }

    public void setUri(Uri uri) {
        if (uri == null)
            this.mUri = null;
        else
            this.mUri = uri.toString();
    }

    public abstract void createFile(Context context) throws LocalFile.FileCreationException, TimeoutException, RootDeniedException, IOException, RemoteException;

    public abstract void mkdir(Context context) throws FileCreationException, IOException, RootDeniedException, TimeoutException, RemoteException;

    private void updateDialogContent(Activity context, final MaterialDialog dialog, final String content) {
        if (context == null || dialog == null) return;
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog.setContent(content);
            }
        });
    }

    @WorkerThread
    public final File copy(Activity context, File dest) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException, FileCreationException {
        return copy(context, dest, null);
    }

    @WorkerThread
    public final File copy(Activity context, File dest, final MaterialDialog updateDialog) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException, FileCreationException {
        updateDialogContent(context, updateDialog, getName());
        if (isDirectory()) {
            // Make destination folder if necessary
            if (!dest.exists())
                dest.mkdir(context);
            // List contents of current folder
            final List<File> contents = listFiles();
            for (File fi : contents) {
                // Copy each file inside the current folder into the new folder
                final File fiDest = File.getNewFile(context, dest, fi.getName(), fi.isDirectory(), !fi.isDirectory());
                fi.copy(context, fiDest, updateDialog);
            }
        } else {
            // Copy individual file
            return copyFile(context, dest);
        }
        return dest;
    }


    @WorkerThread
    public final File move(Activity context, File newFile) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException, FileCreationException {
        return move(context, newFile, null);
    }

    @WorkerThread
    public final File move(Activity context, File newFile, final MaterialDialog updateDialog) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException, FileCreationException {
        return move(context, newFile, updateDialog, true);
    }

    @WorkerThread
    public final File move(Activity context, File dest, final MaterialDialog updateDialog, boolean checkDuplicates) throws RootDeniedException, TimeoutException, RemoteException, PluginFileImpl.ResultException, IOException, FileCreationException {
        updateDialogContent(context, updateDialog, getName());
        if (isDirectory()) {
            // Make destination folder if necessary
            if (!dest.exists())
                dest.mkdir(context);
            // List contents of current folder
            final List<File> contents = listFiles();
            for (File fi : contents) {
                // Copy each file inside the current folder into the new folder
                final File fiDest = File.getNewFile(context, dest, fi.getName(), fi.isDirectory(), checkDuplicates && !fi.isDirectory());
                fi.move(context, fiDest, updateDialog, checkDuplicates);
            }
        } else {
            // Copy individual file
            return moveFile(context, dest);
        }
        return dest;
    }

    public abstract File copyFile(Activity context, File dest) throws RemoteException, PluginFileImpl.ResultException, IOException, RootDeniedException, TimeoutException, FileCreationException;

    public abstract File moveFile(Activity context, File dest) throws RemoteException, PluginFileImpl.ResultException, IOException, RootDeniedException, TimeoutException, FileCreationException;

    public abstract boolean delete(Context context) throws TimeoutException, RootDeniedException, IOException, RemoteException, PluginFileImpl.ResultException;

    @NonNull
    public abstract String getDirectoryFileCount();

    public abstract void setDirectoryFileCount(String fileCount);

    public abstract boolean isViewableArchive(Context context);

    public boolean isArchiveOrInArchive(Context context) {
        final String ext = getExtension();
        final List<String> zipExtensions = Arrays.asList(
                context.getResources().getStringArray(R.array.supported_archive_extensions));
        return zipExtensions.contains(ext);
    }

    public boolean isAndroidPackage(Context context) {
        final String ext = getExtension();
        final List<String> zipExtensions = Arrays.asList(
                context.getResources().getStringArray(R.array.viewable_application_extensions));
        return zipExtensions.contains(ext);
    }

    public abstract boolean isDirectory();

    public abstract boolean exists();

    public abstract long length();

    @WorkerThread
    public abstract List<File> listFiles() throws RootDeniedException, TimeoutException, IOException, RemoteException, PluginFileImpl.ResultException;

    public abstract long lastModified();

    public String getPluginPackage() {
        return null;
    }

    public String getPluginAccount() {
        return null;
    }

    public boolean isSymlink() {
        return false;
    }

    @Nullable
    public File getRealFile() {
        return null;
    }

    @WorkerThread
    public void cacheRealFileIfLink() {
    }

    @WorkerThread
    public abstract void chmod(int permissions) throws RootDeniedException, TimeoutException, IOException, RemoteException, PluginFileImpl.ResultException, OperationNotSupported;

    public abstract void chown(int uid) throws Exception;

    @NonNull
    public abstract String getPermissions(Context context);

    public java.io.File toJavaFile() {
        try {
            String path = getPath();
            if (path == null || path.trim().isEmpty()) path = "/";
            return new java.io.File(path);
        } catch (Throwable t) {
            return new java.io.File("/");
        }
    }

    public InputStream openInputStream(@NonNull Context context) throws FileNotFoundException {
        if (mUri == null)
            throw new IllegalStateException("File has null URI.");
        else {
            Uri uri = getUri();
            if (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase("file"))
                return new FileInputStream(uri.getPath());
            else if (uri.getScheme().equalsIgnoreCase("content"))
                return context.getContentResolver().openInputStream(uri);
            else throw new IllegalStateException("Unsupported URI scheme: " + uri);
        }
    }

    public OutputStream openOutputStream(@NonNull Context context) throws FileNotFoundException {
        if (mUri == null)
            throw new IllegalStateException("File has null URI.");
        else {
            Uri uri = getUri();
            if (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase("file"))
                return new FileOutputStream(uri.getPath());
            else if (uri.getScheme().equalsIgnoreCase("content"))
                return context.getContentResolver().openOutputStream(uri);
            else throw new IllegalStateException("Unsupported URI scheme: " + uri);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof File && ((File) o).mUri.equals(mUri);
    }

    @Override
    public String toString() {
        Uri uri = getUri();
        if (uri == null)
            return "(null)";
        return uri.toString();
    }

    public static class FileCreationException extends Exception {
        public FileCreationException(String detailMessage) {
            super(detailMessage);
        }
    }

    public static class OperationNotSupported extends Exception {
        public OperationNotSupported(String detailMessage) {
            super(detailMessage);
        }
    }
}