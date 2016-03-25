package com.afollestad.cabinet.ui.base;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.Bundle;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.content.ContentFragment;
import com.afollestad.cabinet.views.BreadCrumbLayout;

/**
 * @author Aidan Follestad (afollestad)
 */
@SuppressLint("MissingSuperCall")
public class CrumbsActivity extends PluginActivity {

    public BreadCrumbLayout mCrumbs;

    @Override
    protected void onPause() {
        super.onPause();
        saveScrollPosition();
    }

    private void saveScrollPosition() {
        Fragment frag = getFragmentManager().findFragmentById(R.id.container);
        if (frag != null) {
            ((ContentFragment) frag).saveScrollPosition();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null)
            App.invalidateRootAvailability();

        mCrumbs = (BreadCrumbLayout) findViewById(R.id.breadCrumbs);
        mCrumbs.setFragmentManager(getFragmentManager());
        mCrumbs.setCallback(new BreadCrumbLayout.SelectionCallback() {
            @Override
            public void onCrumbSelection(BreadCrumbLayout.Crumb crumb, int index) {
                if (index == -1) {
                    onBackPressed();
                } else {
                    File activeFile = null;
                    if (mCrumbs.getActiveIndex() > -1)
                        activeFile = mCrumbs.getCrumb(mCrumbs.getActiveIndex()).getFile();
                    if (crumb.getFile() != null && activeFile != null &&
                            crumb.getFile().equals(activeFile)) {
                        Fragment frag = getFragmentManager().findFragmentById(R.id.container);
                        if (frag != null && frag instanceof ContentFragment)
                            ((ContentFragment) frag).jumpToTop(true);
                    } else {
                        switchDirectory(crumb, crumb.getFile() == null, true);
                    }
                }
            }
        });

        if (savedInstanceState != null && savedInstanceState.containsKey("breadcrumbs_state")) {
            mCrumbs.restoreFromStateWrapper((BreadCrumbLayout.SavedStateWrapper)
                    savedInstanceState.getSerializable("breadcrumbs_state"), this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCrumbs != null)
            outState.putSerializable("breadcrumbs_state", mCrumbs.getStateWrapper());
    }

    protected void switchDirectory(File to) {
        // Used by MainActivity
    }

    protected void switchDirectory(BreadCrumbLayout.Crumb to, boolean forceRecreate, boolean history) {
        // Used by MainActivity
    }
}
