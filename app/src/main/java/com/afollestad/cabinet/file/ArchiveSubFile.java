package com.afollestad.cabinet.file;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.afollestad.cabinet.file.base.File;

import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * A sub file in an archive. The top level archive file is never an ArchiveSubFile.
 *
 * @author Aidan Follestad (afollestad)
 */
public class ArchiveSubFile extends ArchiveFile {

    private final ArchiveFile topFile; // File on disk path
    private ArchiveFile parent;

    @Override
    public boolean readUnavailableForOther() {
        return false;
    }

    @NonNull
    @Override
    public String getPermissions(Context context) {
        return "";
    }

    @Override
    public void chmod(int permissions) throws OperationNotSupported {
        throw new OperationNotSupported("chmod not supported.");
    }

    @Override
    public void chown(int uid) {
        throw new IllegalStateException("chown not supported.");
    }

    public void setParent(ArchiveFile file) {
        parent = file;
    }

    public ArchiveSubFile(ArchiveFile topFile, ArchiveEntry ze) {
        super(Uri.parse(topFile.getUri().toString() +
                (topFile.getUri().toString().endsWith("/") || ze.getName().startsWith("/")
                        ? "" : File.separator)
                + (ze.getName() != null && ze.getName().endsWith(File.separator) ?
                ze.getName().substring(0, ze.getName().length() - 1) : ze.getName())));
        this.topFile = topFile;
        getFileInfo().readAvailable = true;
        getFileInfo().size = ze.getSize();
        if (ze.isDirectory()) {
            forceIsDir();
        }
    }

    @Override
    public boolean isDirectory() {
        return getFileInfo().isDirectory();
    }

    @Override
    public long length() {
        if (isDirectory()) {
            if (subFiles == null) return 0;
            return subFiles.size();
        }
        return super.length();
    }

    @Override
    protected long recursiveSize(File dir, boolean dirs, boolean bytes) {
        return length();
    }

    @Override
    public File copyFile(Activity context, File dest) {
        throw new IllegalStateException("copyFile not supported for ArchiveSubFile.");
    }

    @Override
    public File getParent() {
        return parent;
    }

    public String getParentPath() {
        return getPath().substring(0, getPath().lastIndexOf(File.separator));
    }

    public ArchiveFile getTopFile() {
        return topFile;
    }

    @Override
    public boolean isInitialized() {
        return topFile.isInitialized();
    }
}
