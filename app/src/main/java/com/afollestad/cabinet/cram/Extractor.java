package com.afollestad.cabinet.cram;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.afollestad.cabinet.file.base.File;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.utils.IOUtils;

import java.io.OutputStream;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class Extractor {

    private final Cram mCram;
    private ArchiveInputStream mStream;
    private File mStreamFile;
    private String mTarget;
    private Handler mPostBack;
    private Future<Extractor, File> mComplete;
    private ProgressFuture<Extractor, File> mProgress;
    private File mDest;

    public Extractor(Cram cram, ArchiveInputStream stream) {
        mCram = cram;
        mStream = stream;
        mPostBack = new Handler();
    }

    public Extractor(Cram cram, File file) {
        mCram = cram;
        mStreamFile = file;
        mPostBack = new Handler();
    }

    public Extractor to(@NonNull File destinationFolder) {
        mDest = destinationFolder;
        return this;
    }

    public Extractor target(@NonNull String entryName) {
        mTarget = entryName;
        return this;
    }

    public Extractor handler(Handler handler) {
        mPostBack = handler;
        return this;
    }

    public Extractor progress(ProgressFuture<Extractor, File> future) {
        mProgress = future;
        return this;
    }

    private File completeSevenZip(Context context) throws Exception {
        SevenZFile sevenZFile = new SevenZFile(mStreamFile.toJavaFile());
        ArchiveEntry ent;
        while ((ent = sevenZFile.getNextEntry()) != null) {
            if (!ent.isDirectory() && (mTarget == null || (mTarget != null && mTarget.equals(ent.getName())))) {
                final File destFile = File.getNewFile(mCram.mContext, mDest, ent.getName(), false, true);
                //noinspection ResultOfMethodCallIgnored
                destFile.getParent().mkdir(context);
                OutputStream os = null;
                try {
                    os = mCram.getOutputStream(destFile);
                    IOUtils.copy(mStream, os);
                } finally {
                    IOUtils.closeQuietly(os);
                }
                if (mTarget != null)
                    return destFile;
                if (mProgress != null) {
                    mPostBack.post(new Runnable() {
                        @Override
                        public void run() {
                            mProgress.progress(Extractor.this, destFile);
                        }
                    });
                }
            }
        }
        return null;
    }

    public File complete(Context context) throws Exception {
        if (mStreamFile != null) {
            return completeSevenZip(context);
        } else if (mStream == null)
            throw new IllegalStateException("This extractor has already been used and cannot be used again.");
        else if (mDest == null)
            throw new IllegalStateException("You have not set a destination folder.");
        try {
            ArchiveEntry ent;
            while ((ent = mStream.getNextEntry()) != null) {
                if (!ent.isDirectory() && (mTarget == null || (mTarget != null && mTarget.equals(ent.getName())))) {
                    final File destFile = File.getNewFile(mCram.mContext, mDest, ent.getName(), false, true);
                    //noinspection ResultOfMethodCallIgnored
                    destFile.getParent().mkdir(context);
                    OutputStream os = null;
                    try {
                        os = mCram.getOutputStream(destFile);
                        IOUtils.copy(mStream, os);
                    } finally {
                        IOUtils.closeQuietly(os);
                    }
                    if (mTarget != null)
                        return destFile;
                    if (mProgress != null) {
                        mPostBack.post(new Runnable() {
                            @Override
                            public void run() {
                                mProgress.progress(Extractor.this, destFile);
                            }
                        });
                    }
                }
            }
        } finally {
            cleanup();
        }
        return mDest;
    }

    public Extractor complete(@NonNull Future<Extractor, File> future, final Context context) {
        mComplete = future;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File result = complete(context);
                    if (mComplete == null || mPostBack == null)
                        return;
                    mPostBack.post(new Runnable() {
                        @Override
                        public void run() {
                            mComplete.complete(Extractor.this, result, null);
                        }
                    });
                } catch (final Exception e) {
                    if (mComplete == null || mPostBack == null)
                        return;
                    mPostBack.post(new Runnable() {
                        @Override
                        public void run() {
                            mComplete.complete(Extractor.this, null, e);
                        }
                    });
                }
            }
        }).start();
        return this;
    }

    public void cleanup() {
        mPostBack = null;
        mComplete = null;
        IOUtils.closeQuietly(mStream);
        mStream = null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        cleanup();
    }
}