package com.afollestad.cabinet.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.cabinet.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Iconized PopupMenu
 */
public class IconizedMenu extends PopupMenu {
    private final Context mContext;

    /**
     * Construct a new PopupMenu.
     *
     * @param context Context for the PopupMenu.
     * @param anchor  Anchor view for this popup. The popup will appear below the anchor if there
     *                is room, or above it if there is not.
     */
    public IconizedMenu(ThemableActivity context, View anchor) {
        super(context, anchor);
        mContext = context;
        //Use reflection to call mPopup.setForceShowIcon(true), might break
        //in the future.
        try {
            Field[] fields = PopupMenu.class.getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(this);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper
                            .getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod(
                            "setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Inflate a menu resource into this PopupMenu. This is equivalent to calling
     * popupMenu.getMenuInflater().inflate(menuRes, popupMenu.getMenu()).
     *
     * @param menuRes Menu resource to inflate
     */
    public void inflate(int menuRes) {
        super.inflate(menuRes);
        final boolean darkMode = ThemeUtils.isDarkMode(mContext) || ThemeUtils.isTrueBlack(mContext);
        final int color = darkMode ? Color.WHITE : Utils.resolveColor(mContext, android.R.attr.textColorPrimary);
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (item.getIcon() != null)
                item.getIcon().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
    }
}