package com.afollestad.cabinet.file;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.PluginFileResult;
import com.afollestad.cabinet.utils.Perm;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class LocalFile extends File {

    private static final long serialVersionUID = -1600351726599164257L;

    /**
     * isDirectory is cached since it will return false if the file no longer exists
     * And this value is needed after a folder is deleted in some cases.
     */
    private FileInfo mFileInfo;

    public LocalFile() {
        this(Uri.parse("file:///"));
    }

    public LocalFile(Uri uri) {
        super(uri);
        mFileInfo = new FileInfo(false, getName());
    }

    public LocalFile(java.io.File local) {
        this(Uri.fromFile(local));
    }

    public LocalFile(LocalFile parent, String name) {
        this(Uri.parse("file://" + parent.getPath() + (parent.getPath().equals("/") ? "" : "/") + name));
    }

    public FileInfo getFileInfo() {
        return mFileInfo;
    }

    public void forceIsDir() {
        mFileInfo.type = 'd';
    }

    public void attachFileInfo(FileInfo info) {
        mFileInfo = info;
    }

    public boolean dirReadUnavailableForOther() {
        File parentFile = getParent();
        return parentFile != null && !parentFile.toJavaFile().canRead();
    }

    @Override
    public boolean dirWriteUnavailableForOther() {
        File parentFile = getParent();
        return parentFile != null && !parentFile.toJavaFile().canWrite();
    }

    @Override
    public boolean writeUnavailableForOther() {
        java.io.File file = toJavaFile();
        return file != null && !file.canWrite();
    }

    @Override
    public boolean readUnavailableForOther() {
        java.io.File file = toJavaFile();
        return file != null && !file.canRead();
    }

    @Override
    public boolean readAvailable() {
        return mFileInfo != null && mFileInfo.readAvailable;
    }

    @Override
    public boolean isViewableArchive(Context context) {
        final String ext = getExtension();
        final List<String> zipExtensions = Arrays.asList(
                context.getResources().getStringArray(R.array.viewable_archive_extensions));
        return zipExtensions.contains(ext);
    }

    @Override
    public boolean isHidden() {
        return toJavaFile().isHidden();
    }

    @Override
    public void createFile(Context context) throws FileCreationException, TimeoutException, RootDeniedException, IOException {
        try {
            LocalFileUtils.remount(this, true, context);
        } catch (TimeoutException | RootDeniedException | IOException ignored) {
        }

        App.runCommand(dirWriteUnavailableForOther(), "touch \"" + getPath() + "\"");

        if (!exists())
            throw new FileCreationException("Failed to make file " + this);
    }

    @Override
    public void mkdir(Context context) throws FileCreationException, IOException, RootDeniedException, TimeoutException, RemoteException {
        try {
            LocalFileUtils.remount(this, true, context);
        } catch (TimeoutException | RootDeniedException | IOException ignored) {
        }

        App.runCommand(dirWriteUnavailableForOther(), "mkdir \"" + getPath() + "\"");

        if (!exists())
            throw new FileCreationException("Failed to make directory " + this);
    }

    @Override
    public File copyFile(Activity context, File dest) throws PluginFileImpl.ResultException,
            IOException, RootDeniedException, TimeoutException, RemoteException {
        if (dest.getPluginPackage() != null) {
            // File is uploaded directly from the local file to the plugin
            PluginFileImpl pfi = (PluginFileImpl) dest;
            PluginFileResult result = pfi.getPlugin().getService().upload(getUri(), pfi.getWrapper());
            if (result.getError() != null)
                throw new PluginFileImpl.ResultException(result.getError());
            return new PluginFileImpl(result.getFile(), pfi.getPluginAccount(), pfi.getPlugin());
        } else {
            String copyCommand = "cp -fp \"" + getPath() + "\"" + " \"" + dest.getPath() + "\"";
            boolean rootNeeded = readUnavailableForOther() || dest.dirWriteUnavailableForOther();

            if (rootNeeded) {
                try {
                    LocalFileUtils.remount(this, true, context);
                } catch (TimeoutException | RootDeniedException | IOException ignored) {
                }
            }
            App.runCommand(rootNeeded, copyCommand);


            // Update the media database if necessary
            LocalFileUtils.updateMediaDatabase(context, (LocalFile) dest, LocalFileUtils.MediaUpdateType.ADD);
            initFileInfo();
            return dest;
        }
    }

    @Override
    public File moveFile(Activity context, File dest) throws PluginFileImpl.ResultException, IOException, RootDeniedException, TimeoutException, RemoteException {
        if (dest.getPluginPackage() != null) {
            // File is uploaded directly from the local file to the plugin
            PluginFileImpl pfi = (PluginFileImpl) dest;
            PluginFileResult result = pfi.getPlugin().getService().upload(getUri(), pfi.getWrapper());
            if (result.getError() != null)
                throw new PluginFileImpl.ResultException(result.getError());
            delete(context);
            return new PluginFileImpl(result.getFile(), pfi.getPluginAccount(), pfi.getPlugin());
        } else {
            String moveCommand = "mv -f \"" + getPath() + "\"" + " \"" + dest.getPath() + "\"";
            boolean rootNeeded = readUnavailableForOther() || dest.dirWriteUnavailableForOther();

            if (rootNeeded) {
                try {
                    LocalFileUtils.remount(this, true, context);
                } catch (TimeoutException | RootDeniedException | IOException ignored) {
                }
            }
            App.runCommand(rootNeeded, moveCommand);

            // Update the media database if necessary
            LocalFileUtils.updateMediaDatabase(context, (LocalFile) dest, LocalFileUtils.MediaUpdateType.ADD);
            setUri(dest.getUri());
            initFileInfo();

            return this;
        }
    }

    @Override
    public boolean delete(Context context) {
        try {
            LocalFileUtils.remount(this, true, context);
        } catch (TimeoutException | RootDeniedException | IOException ignored) {
        }
        try {
            App.runCommand(dirWriteUnavailableForOther(), "rm -r \"" + getPath() + "\"");
        } catch (RootDeniedException | IOException | TimeoutException e) {
            return false;
        }
        LocalFileUtils.updateMediaDatabase(context, this, LocalFileUtils.MediaUpdateType.REMOVE);
        return true;
    }

    @NonNull
    @Override
    public String getDirectoryFileCount() {
        return mFileInfo.directoryFileCount;
    }

    @Override
    public void setDirectoryFileCount(String fileCount) {
        mFileInfo.directoryFileCount = fileCount;
    }

    @Override
    public boolean isDirectory() {
        if (isSymlink()) {
            return getRealFile().isDirectory();
        }
        return mFileInfo.isDirectory();
    }

    @Override
    public boolean exists() {
        return dirReadUnavailableForOther() ? RootTools.exists(getPath(), true) : toJavaFile().exists();
    }

    @Override
    public long length() {
        if (isSymlink()) {
            return getRealFile().length();
        }
        return getFileInfo().size;
    }

    @WorkerThread
    @NonNull
    @Override
    public List<File> listFiles() throws RootDeniedException, TimeoutException, IOException {
        List<File> results = new ArrayList<>();

        String path = getPath();
        if (!path.endsWith("/"))
            path += "/";

        List<FileInfo> infos = listFileInfos();
        for (FileInfo info : infos) {
            java.io.File temp = new java.io.File(path + info.name);
            LocalFile fi = new LocalFile(temp);
            fi.attachFileInfo(info);
            fi.cacheRealFileIfLink();
            results.add(fi);
        }

        return results;
    }

    @WorkerThread
    @NonNull
    public List<FileInfo> listFileInfos() throws RootDeniedException, TimeoutException, IOException {
        String path = getPath();
        if (!path.endsWith("/"))
            path += "/";

        List<String> response = App.runCommand(readUnavailableForOther(), "ls -la \"" + path + "\"");
        return LsParser.parse(path, response).getFileInfos();
    }

    /**
     * When finishList in DirectoryFragment is not run for a LocalFile
     */
    public void initFileInfo() throws TimeoutException, RootDeniedException, IOException {
        initFileInfoNoLink();
        cacheRealFileIfLink();
    }

    private void initFileInfoNoLink() throws TimeoutException, RootDeniedException, IOException {
        final List<String> response = App.runCommand(readUnavailableForOther(), "ls -lad \"" + getPath() + "\"");
        if (getParent() != null) {
            List<FileInfo> results = LsParser.parse(getParent().getPath(), response).getFileInfos();
            if (results.isEmpty()) return;
            attachFileInfo(results.get(0));
        }
    }

    @Override
    public boolean isSymlink() {
        return mFileInfo.isSymlink();
    }

    @Nullable
    @Override
    public File getRealFile() {
        return mFileInfo.realFile;
    }

    @WorkerThread
    @Override
    public void cacheRealFileIfLink() {
        LocalFile symlinked = this;
        while (symlinked.isSymlink()) {
            symlinked = new LocalFile(Uri.fromFile(new java.io.File(symlinked.getFileInfo().linkedPath)));
            try {
                symlinked.initFileInfoNoLink();
            } catch (TimeoutException | RootDeniedException | IOException e) {
                e.printStackTrace();
            }
        }
        mFileInfo.realFile = symlinked;
    }

    @Override
    public long lastModified() {
        if (isSymlink()) {
            return getRealFile().lastModified();
        }
        return mFileInfo.lastModified;
    }

    @Override
    public String getPluginPackage() {
        return null;
    }

    @Override
    public void chmod(int permissions) throws RootDeniedException, TimeoutException, IOException, OperationNotSupported {
        App.runCommand(writeUnavailableForOther(), "chmod " + permissions + " \"" + getPath() + "\"");
    }

    @Override
    public void chown(int uid) throws Exception {
        // TODO
        throw new IllegalStateException("chown not implemented yet.");
    }

    @NonNull
    @Override
    public String getPermissions(Context context) {
        return mFileInfo.permissions;
    }

    /**
     * Careful here. The new LocalFile doesn't have FileInfo initialized.
     */
    @Override
    public File getParent() {
        if (getPath().contains("/")) {
            if (getPath().equals("/")) return null;
            String str = getPath().substring(0, getPath().lastIndexOf('/'));
            if (str.trim().isEmpty()) str = "/";
            return new LocalFile(Uri.parse("file://" + str));
        } else return null;
    }

    /**
     * Preps file for writing
     *
     * @return initial permissions of file and file parent directory
     * @throws IOException
     * @throws RootDeniedException
     * @throws TimeoutException
     */
    public int prepRootWrite(Context context) throws IOException, RootDeniedException, TimeoutException {
        LocalFileUtils.remount((LocalFile) getParent(), true, context);
        // CHMOD gives temporary permission to write
        List<String> results = App.runCommand(readUnavailableForOther(),
                "ls -l \"" + getPath() + "\"",
                "ls -ld \"" + getParent().getPath() + "\"");
        int initialPerms = -1;
        if (results.size() > 0) {
            initialPerms = Integer.parseInt(Perm.parse(results.get(0), null));
        }
        try {
            chmod(777);
        } catch (OperationNotSupported operationNotSupported) {
            operationNotSupported.printStackTrace();
        }
        return initialPerms;
    }

    /**
     * Finishes file writing
     */
    public void finishRootWrite(int initialPerms, Context context) throws IOException, RootDeniedException, TimeoutException {
        LocalFileUtils.remount((LocalFile) getParent(), false, context);
        try {
            chmod(initialPerms);
        } catch (OperationNotSupported operationNotSupported) {
            operationNotSupported.printStackTrace();
        }
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof LocalFile) &&
                (((((LocalFile) o).getPath() == null) == (getPath() == null)) &&
                        ((LocalFile) o).getPath() != null &&
                        ((LocalFile) o).getPath().equals(getPath()));
    }

}