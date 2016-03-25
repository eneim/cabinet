package com.afollestad.cabinet.file;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.afollestad.cabinet.App;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootTools.containers.Mount;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Local File Utils - static utility methods
 */
public class LocalFileUtils {

    /**
     * @param rw true is RW, false is RO
     * @return whether the mount before changing was RW (rather than RO)
     */
    public static boolean remount(LocalFile file, boolean rw, Context context) throws TimeoutException, RootDeniedException, IOException {
        String flag = rw ? "rw " : "ro ";
        Mount mount = getMountable(file, context);
        if (!mount.getFlags().contains(flag.trim())) {
            App.runCommand(App.isRootAvailable(), "mount -o remount," + flag + mount.getMountPoint());
        }

        //TODO: Use return value of this method to restore RW/RO after operation is done
        return mount.getFlags().contains("rw");
    }

    public static Mount getMountable(LocalFile from, Context context) throws IOException, TimeoutException, RootDeniedException {
        if (from == null) return null;
        LocalFile lastParent = (LocalFile) from.getParent();
        List<Mount> mounts = getMounts(context);
        while (true) {
            if (lastParent == null)
                return new Mount(null, new java.io.File("/"), null, "ro");
            lastParent.initFileInfo();
            if (lastParent.isSymlink()) {
                lastParent = (LocalFile) lastParent.getRealFile();
            }
            if (mounts != null) {
                for (Mount mount : mounts) {
                    if (mount.getMountPoint().getPath().equals(lastParent.getPath()))
                        return mount;
                }
            }
            lastParent = (LocalFile) lastParent.getParent();
        }
    }

    private static List<Mount> getMounts(Context context) throws TimeoutException, IOException {
        File mountsCachePath = context.getCacheDir();
        try {
            mountsCachePath = File.createTempFile("cabinet", "mounts", mountsCachePath);
            App.runCommand(false, "cat /proc/mounts > " + mountsCachePath.getPath(), "chmod 0777 " + mountsCachePath);
        } catch (RootDeniedException ignored) {
        }

        LineNumberReader lnr = null;
        FileReader fr = null;

        try {
            fr = new FileReader(mountsCachePath);
            lnr = new LineNumberReader(fr);
            String line;
            List<Mount> mounts = new ArrayList<>();
            while ((line = lnr.readLine()) != null) {
                String[] fields = line.split(" ");
                mounts.add(new Mount(new File(fields[0]), // device
                        new File(fields[1]), // mountPoint
                        fields[2], // fstype
                        fields[3] // flags
                ));
            }

            return mounts;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
                if (lnr != null) {
                    lnr.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    static void updateMediaDatabase(Context context, LocalFile file, MediaUpdateType type) {
        if (file == null || context == null) return;
        final String mime = file.getMimeType();
        if (mime == null ||
                (!mime.startsWith("image/") &&
                        !mime.startsWith("audio/") &&
                        !mime.startsWith("video/") &&
                        !mime.equals("ogg"))) {
            String extension = file.getExtension();
            if (extension.equalsIgnoreCase("mkv"))
                return;
        }
        try {
            if (type == MediaUpdateType.ADD) {
                if (file.getPath() == null) return;
                Log.i("UpdateMediaDatabase", "Scanning " + file.getPath());
                MediaScannerConnection.scanFile(context,
                        new String[]{file.getPath()}, null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.i("Scanner", "Scanned " + path + ":");
                                Log.i("Scanner", "-> uri=" + uri);
                            }
                        }
                );
            } else {
                ContentResolver r = context.getContentResolver();
                r.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "_data = ?", new String[]{file.getPath()});
                r.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "_data = ?", new String[]{file.getPath()});
            }
        } catch (Throwable ignored) {
        }
    }

    enum MediaUpdateType {
        ADD,
        REMOVE
    }
}