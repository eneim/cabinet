package com.afollestad.cabinet.cram;

import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.PluginUriResult;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.utils.Utils;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class Cram {

    protected final PluginActivity mContext;
    private java.io.File mCacheFile;
    private PluginFileImpl mRemoteFile;

    private Cram(PluginActivity context) {
        mContext = context;
    }

    @NonNull
    public static Cram with(PluginActivity context) {
        return new Cram(context);
    }

    public Reader reader(@NonNull File file) throws Exception {
        final ArchiveType mime = ArchiveType.fromMime(file.getMimeType());
        if (mime == ArchiveType.Unsupported)
            throw new UnsupportedFormatException("Unsupported MIME type: " + mime);
        InputStream is = getInputStream(file);
        if (mime == ArchiveType.CompressedTar) {
            GzipCompressorInputStream gzin = new GzipCompressorInputStream(is);
            return reader(gzin, mime);
        }
        return reader(is, mime);
    }

    public Reader reader(@NonNull InputStream target, @NonNull ArchiveType type) throws Exception {
        final String archiverName = getArchiverName(type);
        final ArchiveInputStream is = new ArchiveStreamFactory().createArchiveInputStream(archiverName, target);
        return new Reader(is);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Writer writer(@NonNull File file) throws Exception {
        final ArchiveType mime = ArchiveType.fromMime(file.getMimeType());
        if (mime == ArchiveType.Unsupported)
            throw new UnsupportedFormatException("Unsupported MIME type: " + mime);
        else if (mime == ArchiveType.SevenZip)
            return new Writer(this, file);
        final Uri uri = file.getUri();
        if (file.getPluginPackage() != null) {
            mRemoteFile = (PluginFileImpl) file;
            java.io.File cacheDir = new java.io.File(mContext.getExternalCacheDir(), "Cram");
            cacheDir.mkdirs();
            mCacheFile = new java.io.File(cacheDir, uri.getPath().replace(
                    File.separator, "_"));
            mCacheFile.createNewFile();
            file = new LocalFile(mCacheFile);
        }
        OutputStream os = getOutputStream(file);
        if (mime == ArchiveType.CompressedTar) {
            GzipCompressorOutputStream gzout = new GzipCompressorOutputStream(os);
            return writer(gzout, mime);
        }
        return writer(os, mime);
    }

    public Writer writer(@NonNull OutputStream target, @NonNull ArchiveType type) throws Exception {
        final String archiverName = getArchiverName(type);
        if (type == ArchiveType.SevenZip) {
            throw new UnsupportedOperationException("7z archives do not support streaming.");
        } else {
            final ArchiveOutputStream is = new ArchiveStreamFactory().createArchiveOutputStream(archiverName, target);
            return new Writer(this, is, type);
        }
    }

    public Extractor extractor(@NonNull File file) throws Exception {
        final ArchiveType type = ArchiveType.fromMime(file.getMimeType());
        if (type == ArchiveType.Unsupported)
            throw new UnsupportedFormatException("Unsupported MIME type: " + type);
        final Uri uri = file.getUri();
        if (type == ArchiveType.SevenZip && uri.getScheme() != null && !uri.getScheme().equalsIgnoreCase("file"))
            throw new UnsupportedOperationException("7z archives can only be extracted to local files.");
        final InputStream is = getInputStream(file);
        if (type == ArchiveType.CompressedTar) {
            GzipCompressorInputStream gzin = new GzipCompressorInputStream(is);
            return extractor(gzin, type);
        }
        return extractor(is, type);
    }

    public Extractor extractor(@NonNull InputStream target, @NonNull ArchiveType type) throws Exception {
        final String archiverName = getArchiverName(type);
        if (type == ArchiveType.SevenZip) {
            throw new UnsupportedOperationException("7z archives do not support streaming.");
        } else {
            final ArchiveInputStream is = new ArchiveStreamFactory().createArchiveInputStream(archiverName, target);
            return new Extractor(this, is);
        }
    }

    private String getArchiverName(ArchiveType type) {
        switch (type) {
            default:
                return ArchiveStreamFactory.ZIP;
            case Jar:
                return ArchiveStreamFactory.JAR;
            case Tar:
                return ArchiveStreamFactory.TAR;
            case CompressedTar:
                return ArchiveStreamFactory.TAR;
            case SevenZip:
                return ArchiveStreamFactory.SEVEN_Z;
        }
    }

    @NonNull
    protected OutputStream getOutputStream(@NonNull File file) throws Exception {
        final Uri uri = file.getUri();
        if (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase("file")) {
            return new FileOutputStream(uri.getPath());
        } else if (uri.getScheme().equalsIgnoreCase("content")) {
            if (mContext == null)
                throw new IllegalStateException("You must specify a context to use content URIs.");
            return mContext.getContentResolver().openOutputStream(uri);
        } else {
            throw new UnsupportedFormatException("Unsupported URI scheme: " + uri);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    protected InputStream getInputStream(@NonNull File file) throws Exception {
        Uri uri = file.getUri();
        if (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase("file")) {
            return new FileInputStream(uri.getPath());
        } else if (uri.getScheme().equalsIgnoreCase("content")) {
            if (mContext == null)
                throw new IllegalStateException("You must specify a context to use content URIs.");
            return mContext.getContentResolver().openInputStream(uri);
        } else if (uri.getScheme().equalsIgnoreCase("plugin")) {
            if (mContext == null)
                throw new IllegalStateException("You must specify a context to use plugin URIs.");

            java.io.File cacheDir = new java.io.File(mContext.getExternalCacheDir(), "Cram");
            cacheDir.mkdirs();
            java.io.File cacheFile = new java.io.File(cacheDir, uri.getPath().replace(
                    File.separator, "_"));
            cacheFile.createNewFile();

            PluginFileImpl pfi = (PluginFileImpl) file;
            PluginUriResult opened = ((PluginFileImpl) file).getPlugin().getService()
                    .download(pfi.getWrapper(), Uri.fromFile(cacheFile));
            if (opened.getError() != null)
                throw new Exception(opened.getError());
            uri = opened.getUri();

            Log.v("Cram", "Downloaded " + pfi.getUri() + " to " + uri);
            if (uri == null || (uri.getScheme() != null && !uri.getScheme().equalsIgnoreCase("file")))
                throw new Exception(pfi.getPluginPackage() + " didn't return a local file for openFile(), cannot archive it: " + uri);
            return new FileInputStream(uri.getPath());
        } else {
            throw new UnsupportedFormatException("Unsupported URI scheme: " + uri);
        }
    }

    protected void uploadAndCleanup() {
        if (mRemoteFile != null) {
            Log.v("Cram", "Uploading " + mCacheFile.getAbsolutePath() + " to " + mRemoteFile.getWrapper().getPath());
            try {
                mRemoteFile.getPlugin().getService()
                        .upload(Uri.fromFile(mCacheFile), mRemoteFile.getWrapper());
            } catch (RemoteException e) {
                Utils.showErrorDialog(mContext, R.string.failed_upload_plugin_archive, e);
            }
        }
        App.wipeDirectory(new java.io.File(mContext.getExternalCacheDir(), "Cram"));
    }
}