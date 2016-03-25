package com.afollestad.cabinet.cram;

import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.Date;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class CramEntry implements ArchiveEntry {

    private final String mName;
    private final long mSize;
    private final Date mModified;

    public CramEntry(ArchiveEntry entry) {
        mName = entry.getName();
        mSize = entry.getSize();
        mModified = entry.getLastModifiedDate();
    }

    public CramEntry(String name, long size) {
        mName = name;
        mSize = size;
        mModified = new Date();
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public long getSize() {
        return mSize;
    }

    @Override
    public boolean isDirectory() {
        return getName() != null && !getName().trim().isEmpty() &&
                getName().charAt(getName().length() - 1) == '/';
    }

    @Override
    public Date getLastModifiedDate() {
        return mModified;
    }

    @Override
    public String toString() {
        return "(ARCHIVE STUB) " + getName();
    }

}
