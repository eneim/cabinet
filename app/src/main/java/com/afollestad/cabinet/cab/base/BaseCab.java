package com.afollestad.cabinet.cab.base;

import android.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.fragments.content.DirectoryFragment;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.materialcab.MaterialCab;

import java.io.Serializable;
import java.lang.reflect.Field;

public abstract class BaseCab implements Serializable, MaterialCab.Callback {

    protected BaseCab() {
    }

    private transient MaterialCab mCab;
    private transient AppCompatActivity context;
    private transient DirectoryFragment fragment;
    private transient Toast mToast;

    protected void showToast(String message) {
        if (mToast != null)
            mToast.cancel();
        mToast = Toast.makeText(getMainActivity(), message, Toast.LENGTH_LONG);
        mToast.show();
    }

    public final BaseCab start() {
        if (mCab == null)
            mCab = new MaterialCab(context, R.id.cab_attacher);
        mCab.setBackgroundColor(((ThemableActivity) context)
                .getThemeUtils().primaryColor())
                .start(this);
        return this;
    }

    protected MaterialCab getCab() {
        return mCab;
    }

    public BaseCab setActivity(AppCompatActivity context) {
        this.context = context;
        if (mCab == null)
            start();
        else
            mCab.setContext(context);
        invalidate();
        return this;
    }

    public BaseCab setFragment(Fragment fragment) {
        this.context = (AppCompatActivity) fragment.getActivity();
        if (fragment instanceof DirectoryFragment)
            this.fragment = (DirectoryFragment) fragment;
        else this.fragment = null;
        invalidate();
        return this;
    }

    public final boolean isActive() {
        return mCab != null && mCab.isActive();
    }

    protected DirectoryFragment getFragment() {
        return fragment;
    }

    protected MainActivity getMainActivity() {
        return (MainActivity) context;
    }

    protected abstract int getMenu();

    protected abstract CharSequence getTitle();

    void invalidate() {
        if (mCab == null)
            start();
        mCab.setTitle(getTitle());
    }

    public final void finish() {
        if (mCab != null)
            mCab.finish();
    }

    @Override
    public boolean onCabCreated(MaterialCab materialCab, Menu menu) {
        materialCab.setTitle(getTitle());
        if (getMenu() != -1)
            materialCab.setMenu(getMenu());

        if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Field field = menu.getClass().
                        getDeclaredField("mOptionalIconsVisible");
                field.setAccessible(true);
                field.setBoolean(menu, true);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @Override
    public boolean onCabItemClicked(MenuItem menuItem) {
        finish();
        return true;
    }

    @Override
    public boolean onCabFinished(MaterialCab materialCab) {
        return true;
    }
}