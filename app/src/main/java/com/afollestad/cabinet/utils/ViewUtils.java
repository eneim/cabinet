package com.afollestad.cabinet.utils;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ViewUtils {

    public static void setViewBackground(View view, Drawable background) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(background);
        } else {
            //noinspection deprecation
            view.setBackgroundDrawable(background);
        }
    }

    public interface LayoutCallback {
        void onLayout(View view);
    }

    public static void waitForLayout(final View view, final LayoutCallback callback) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                removeLayoutListener(view, this);
                if (callback != null)
                    callback.onLayout(view);
            }
        });
    }

    private static void removeLayoutListener(View view, ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        } else {
            //noinspection deprecation
            view.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        }
    }

    public static void applyTopPadding(int paddingTop, View... paddedViews) {
        for (View v : paddedViews) {
            if (v == null) continue;
            v.setPadding(v.getPaddingLeft(),
                    paddingTop,
                    v.getPaddingRight(),
                    v.getPaddingBottom());
        }
    }

    private ViewUtils() {
    }
}
