package com.afollestad.cabinet.utils;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;

/**
 * @author Aidan Follestad (afollestad)
 */
public class StorageUtils {

    private StorageUtils() {
    }

    @SuppressLint("SdCardPath")
    public static final String[] EXT_SD_PATHS = {
            "/storage/extSdCard/" /* Samsung */,
            "/sdcard-ext" /* Old Motorola */,
            "/storage/sdcard1" /* Generic/New Motorola */,
            "/storage/external_SD" /* LG */,
            "/mnt/ext_sdcard" /* CM12? */
    };

    @SuppressLint("NewApi")
    public static boolean isMounted(java.io.File sdcardDir) {
        String mountState;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mountState = Environment.getExternalStorageState(sdcardDir);
        } else {
            //noinspection deprecation
            mountState = Environment.getStorageState(sdcardDir);
        }
        return mountState.equals(Environment.MEDIA_MOUNTED) ||
                mountState.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
    }

    public static SDCard getSDCard() {
        for (String path : EXT_SD_PATHS) {
            File fi = new LocalFile(Uri.parse("file://" + path));
            if (!fi.readUnavailableForOther())
                return new SDCard(fi, true);
        }
        return null;
    }

    public static class SDCard {

        public SDCard(File from, boolean write) {
            path = from.getPath();
            canWrite = write;
        }

        public final String path;
        public final boolean canWrite;

        public java.io.File asFile() {
            return new java.io.File(path);
        }
    }

    public static long getAvailableSpace(StatFs fs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return fs.getAvailableBytes();
        } else {
            // noinspection deprecation
            return (long) fs.getAvailableBlocks() * (long) fs.getBlockSize();
        }
    }

    public static long getTotalSpace(StatFs fs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return fs.getTotalBytes();
        } else {
            // noinspection deprecation
            return (long) fs.getBlockCount() * (long) fs.getBlockSize();
        }
    }

    public static long getUsedSpace(StatFs fs, long total) {
        return total - getAvailableSpace(fs);
    }
}