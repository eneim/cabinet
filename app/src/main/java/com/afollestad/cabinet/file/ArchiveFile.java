package com.afollestad.cabinet.file;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.afollestad.cabinet.file.base.File;

import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Top level zip file
 */
public class ArchiveFile extends LocalFile {

    private static final long serialVersionUID = 5646455972037332537L;

    public final List<ArchiveSubFile> subFiles;
    private boolean mInitialized;
    private File mVirtual;

    public void putFiles(ArchiveEntry[] entries) {
        synchronized (subFiles) {
            for (ArchiveEntry f : entries) {
                ArchiveSubFile newFile = new ArchiveSubFile(this, f);
                putFile(newFile);
            }
            mInitialized = true;
        }
    }

    void putFile(ArchiveSubFile newFile) {
        if (newFile.getParentPath().equals(getPath())) {
            subFiles.add(newFile);
            newFile.setParent(this);
        } else {
            for (ArchiveSubFile subFile : subFiles) {
                if (subFile.isDirectory() && newFile.getParentPath().startsWith(subFile.getPath())) {
                    subFile.putFile(newFile);
                    break;
                }
            }
        }
    }

    public ArchiveFile(File from, File virtual) {
        this(from.getUri());
        mVirtual = virtual;
    }

    ArchiveFile(Uri uri) {
        super(uri);
        this.subFiles = new ArrayList<>();
    }

    @Override
    public File getParent() {
        if (mVirtual != null)
            return mVirtual.getParent();
        return super.getParent();
    }

    @Override
    public boolean isArchiveOrInArchive(Context context) {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @NonNull
    @Override
    public List<File> listFiles() {
        //noinspection unchecked
        return (List<File>) (List<?>) subFiles;
    }

    @Override
    public String toString() {
        if (mVirtual != null)
            return "(VIRTUAL ARCHIVE) " + mVirtual.getPath();
        return "(ARCHIVE) " + getPath();
    }

    public boolean isInitialized() {
        return mInitialized;
    }
}
