package com.afollestad.cabinet.utils;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;

import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.wnafee.vector.compat.ResourcesCompat;

import java.util.HashMap;

/**
 * Keeps a map of inflated vector drawables so that duplicates are not put into memory. I thought of
 * this from Material Dialogs' TypefaceHelper which helps avoid duplicate Typeface allocation.
 *
 * @author Aidan Follestad (afollestad)
 */
public class VectorDrawableMap {

    public final static int HIDDEN_ALPHA = (int) (255 * 0.4f);
    private HashMap<DrawableId, Drawable> mMap;
    private final static Object LOCK = new Object();

    public VectorDrawableMap() {
        mMap = new HashMap<>();
    }

    public Drawable get(@Nullable ThemableActivity context, @DrawableRes int resId, boolean hidden) {
        if (context == null || mMap == null) return null;
        final int primaryColor = context.getThemeUtils().thumbnailColor();
        synchronized (LOCK) {
            Drawable d;
            DrawableId key = new DrawableId(resId, hidden);
            if (mMap.containsKey(key)) {
                d = mMap.get(key);
                if (d == null) return null;
            } else {
                d = ResourcesCompat.getDrawable(context, resId);
                if (d == null) return null;
                d = d.mutate();
                mMap.put(key, d);
            }
            if (hidden) {
                d.setAlpha(HIDDEN_ALPHA);
            } else {
                d.setAlpha(255);
            }
            d.setColorFilter(primaryColor, PorterDuff.Mode.SRC_ATOP);
            return d;
        }
    }

    public void clean() {
        mMap.clear();
        mMap = null;
    }

    private class DrawableId {

        @DrawableRes
        final int resId;
        final boolean hidden;

        public DrawableId(@DrawableRes int resId, boolean hidden) {
            this.resId = resId;
            this.hidden = hidden;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DrawableId) {
                DrawableId id = (DrawableId) o;
                return id.resId == resId && id.hidden == hidden;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return resId * (hidden ? 1 : -1);
        }
    }
}
