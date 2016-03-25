package com.afollestad.cabinet.cram;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.afollestad.cabinet.file.base.File;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class Writer {

    private final Cram mCram;
    private ArchiveOutputStream mStream;
    private File mFileTarget;
    private Handler mPostBack;
    private ProgressFuture<Writer, File> mProgress;
    private Future<Writer, File[]> mComplete;
    private final ArrayList<File> mFiles;
    private ArchiveType mMime;

    public Writer(@NonNull Cram cram, @NonNull ArchiveOutputStream stream, @NonNull ArchiveType mime) {
        mCram = cram;
        mFiles = new ArrayList<>();
        mPostBack = new Handler();

        mStream = stream;
        mMime = mime;
    }

    /**
     * Used for 7z, since it only supports local files
     */
    public Writer(@NonNull Cram cram, @NonNull File target) {
        mCram = cram;
        mFiles = new ArrayList<>();
        mPostBack = new Handler();

        mFileTarget = target;
    }

    public Writer progress(@NonNull ProgressFuture<Writer, File> future) {
        mProgress = future;
        return this;
    }

    public Writer handler(Handler handler) {
        mPostBack = handler;
        return this;
    }

    public Writer put(@NonNull File... files) throws Exception {
        for (File fi : files) {
            mFiles.add(fi);
            if (fi.isDirectory()) {
                List<File> contents = fi.listFiles();
                put(contents);
            }
        }
        return this;
    }

    public Writer put(@NonNull List<File> files) throws Exception {
        return put(files.toArray(new File[files.size()]));
    }

    private void completeSevenZip() throws Exception {
        synchronized (mFiles) {
            SevenZOutputFile sevenZOutput = null;
            try {
                sevenZOutput = new SevenZOutputFile(mFileTarget.toJavaFile());
                final String[] names = generateNames();
                for (int i = 0; i < mFiles.size(); i++) {
                    final File fi = mFiles.get(i);
                    final SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(
                            fi.toJavaFile(), names[i]);
                    if (fi.isDirectory()) {
                        sevenZOutput.putArchiveEntry(entry);
                        sevenZOutput.closeArchiveEntry();
                        continue;
                    }
                    InputStream is = null;
                    try {
                        is = mCram.getInputStream(fi);
                        byte[] contents = IOUtils.toByteArray(is);
                        entry.setSize(contents.length);
                        sevenZOutput.putArchiveEntry(entry);
                        sevenZOutput.write(contents);
                        sevenZOutput.closeArchiveEntry();
                    } finally {
                        IOUtils.closeQuietly(is);
                    }
                    if (mProgress != null) {
                        if (mPostBack != null) {
                            mPostBack.post(new Runnable() {
                                @Override
                                public void run() {
                                    mProgress.progress(Writer.this, fi);
                                }
                            });
                        }
                    }
                }
            } finally {
                IOUtils.closeQuietly(sevenZOutput);
                cleanup();
            }
        }
    }

    private String[] generateNames() {
        // Sort URIs by which ones have more sub folders
        Collections.sort(mFiles, new SlashComparator());
        final String[] names = new String[mFiles.size()];
        String excessParent;

        if (mFiles.get(0).isDirectory()) {
            excessParent = mFiles.get(0).getParent().getPath();
            if (!excessParent.endsWith(File.separator))
                excessParent += File.separator;
        } else {
            String fp = mFiles.get(0).getPath();
            int excessSlashesCount = SlashComparator.countOccurrences(fp);
            int excessSlashLastIndex = Util.nthOccurrence(fp,
                    File.separatorChar, excessSlashesCount - 1);
            excessParent = fp.substring(0, excessSlashLastIndex);
        }

        for (int i = 0; i < mFiles.size(); i++) {
            String newPath = mFiles.get(i).getPath();
            if (newPath.startsWith(excessParent))
                newPath = newPath.substring(excessParent.length(), newPath.length());
            if (mFiles.get(i).isDirectory())
                newPath += File.separator;
            names[i] = newPath;
        }

        return names;
    }

    public Writer complete() throws Exception {
        if (mFileTarget != null) {
            completeSevenZip();
            return this;
        } else if (mStream == null)
            throw new IllegalStateException("This writer has already been used and cannot be used again.");
        synchronized (mFiles) {
            try {
                final String[] names = generateNames();
                for (int i = 0; i < mFiles.size(); i++) {
                    final File fi = mFiles.get(i);
                    final ArchiveEntry entry = generateEntry(names[i]);
                    if (fi.isDirectory()) {
                        mStream.putArchiveEntry(entry);
                        mStream.closeArchiveEntry();
                        Log.v("CramWriter", "Wrote folder entry " + entry.getName());
                        continue;
                    }
                    InputStream is = null;
                    try {
                        is = mCram.getInputStream(fi);
                        byte[] contents = IOUtils.toByteArray(is);
                        if (entry instanceof ZipArchiveEntry)
                            ((ZipArchiveEntry) entry).setSize(contents.length);
                        else if (entry instanceof TarArchiveEntry)
                            ((TarArchiveEntry) entry).setSize(contents.length);
                        mStream.putArchiveEntry(entry);
                        mStream.write(contents);
                        mStream.closeArchiveEntry();
                        Log.v("CramWriter", "Wrote " + contents.length + " bytes to entry " + entry.getName());
                    } finally {
                        IOUtils.closeQuietly(is);
                    }
                    if (mProgress != null) {
                        if (mPostBack != null) {
                            mPostBack.post(new Runnable() {
                                @Override
                                public void run() {
                                    mProgress.progress(Writer.this, fi);
                                }
                            });
                        }
                    }
                }
            } finally {
                cleanup();
                mCram.uploadAndCleanup();
            }
        }
        return this;
    }

    public Writer complete(@NonNull Future<Writer, File[]> future) {
        mComplete = future;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    complete();
                    if (mPostBack != null && mComplete != null) {
                        mPostBack.post(new Runnable() {
                            @Override
                            public void run() {
                                mComplete.complete(Writer.this, mFiles.toArray(new File[mFiles.size()]), null);
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (mPostBack != null && mComplete != null) {
                        mPostBack.post(new Runnable() {
                            @Override
                            public void run() {
                                mComplete.complete(Writer.this, null, e);
                            }
                        });
                    }
                }
            }
        }).start();
        return this;
    }

    private void cleanup() {
        mProgress = null;
        mComplete = null;
        mFiles.clear();
        mPostBack = null;
        IOUtils.closeQuietly(mStream);
        mStream = null;
    }

    @NonNull
    private ArchiveEntry generateEntry(@NonNull String name) {
        ArchiveEntry entry;
        switch (mMime) {
            case Zip:
                entry = new ZipArchiveEntry(name);
                break;
            case Jar:
                entry = new JarArchiveEntry(name);
                break;
            case Tar:
            case CompressedTar:
                entry = new TarArchiveEntry(name);
                break;
            default:
                throw new IllegalStateException("Can't generate entry for MIME " + mMime);
        }
        return entry;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        cleanup();
    }
}
