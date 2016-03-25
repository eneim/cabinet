package com.afollestad.cabinet.glide;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.afollestad.cabinet.file.LocalFile;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * @author Aidan Follestad (afollestad)
 */
public class APKIconLoader implements ModelLoader<LocalFile, InputStream> {

    private final Context mContext;
    private boolean mCancelled;

    public APKIconLoader(Context context) {
        mContext = context;
    }

    private InputStream drawableToStream(Drawable d) {
        BitmapDrawable bitDw = (BitmapDrawable) d;
        Bitmap bitmap = bitDw.getBitmap();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] imageInByte = stream.toByteArray();
        ByteArrayInputStream is = new ByteArrayInputStream(imageInByte);
        bitmap.recycle();
        if (mCancelled)
            return null;
        return is;
    }

    @Override
    public DataFetcher<InputStream> getResourceFetcher(LocalFile model, int width, int height) {
        final String mPath = model.getPath();
        return new DataFetcher<InputStream>() {
            @Override
            public InputStream loadData(Priority priority) throws Exception {
                mCancelled = false;
                PackageManager pm = mContext.getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(mPath, 0);
                pi.applicationInfo.sourceDir = mPath;
                pi.applicationInfo.publicSourceDir = mPath;
                if (mCancelled) return null;
                return drawableToStream(pi.applicationInfo.loadIcon(pm));
            }

            @Override
            public void cleanup() {
            }

            @Override
            public String getId() {
                return mPath;
            }

            @Override
            public void cancel() {
                mCancelled = true;
            }
        };
    }

    public static class Loader implements ModelLoaderFactory<LocalFile, InputStream> {

        @Override
        public ModelLoader<LocalFile, InputStream> build(Context context, GenericLoaderFactory factories) {
            return new APKIconLoader(context);
        }

        @Override
        public void teardown() {
        }
    }
}