package com.afollestad.cabinet.cram;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.afollestad.cabinet.utils.BackgroundThread;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class Reader {

    private ArchiveInputStream mStream;
    private Handler mPostBack;
    private Future<Reader, ArchiveEntry[]> mComplete;
    private ArchiveEntry[] mCache;

    public Reader(ArchiveInputStream stream) {
        mStream = stream;
        mPostBack = new Handler();
    }

    public Reader handler(Handler handler) {
        mPostBack = handler;
        return this;
    }

    @WorkerThread
    public ArchiveEntry[] entries() throws Exception {
        if (mCache != null)
            return mCache;
        BackgroundThread.lock();
        try {
            Map<String, CramEntry> toAdd = new HashMap<>();
            ArchiveEntry ent;
            while ((ent = mStream.getNextEntry()) != null) {
                if (BackgroundThread.stopUntilNotLocked) {
                    break;
                }
                Util.processEntry(ent, toAdd);
            }
            List<ArchiveEntry> entries = new ArrayList<ArchiveEntry>(toAdd.values());
            Collections.sort(entries, new EntryComparator());
            mCache = entries.toArray(new ArchiveEntry[entries.size()]);
            return mCache;
        } finally {
            BackgroundThread.removeLock();
        }
    }

    public Reader entries(@NonNull Future<Reader, ArchiveEntry[]> future) {
        if (mCache != null) {
            future.complete(Reader.this, mCache, null);
            return this;
        }
        mComplete = future;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArchiveEntry[] results = entries();
                    if (mComplete == null || mPostBack == null)
                        return;
                    mPostBack.post(new Runnable() {
                        @Override
                        public void run() {
                            mComplete.complete(Reader.this, results, null);
                        }
                    });
                } catch (final Exception e) {
                    if (mComplete == null || mPostBack == null)
                        return;
                    mPostBack.post(new Runnable() {
                        @Override
                        public void run() {
                            mComplete.complete(Reader.this, null, e);
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
        mCache = null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        cleanup();
    }
}
