package com.afollestad.cabinet.fragments.base;

import android.app.DialogFragment;

import com.afollestad.cabinet.App;
import com.squareup.leakcanary.RefWatcher;

/**
 * @author Aidan Follestad (afollestad)
 */
public class LeakDetectDialogFragment extends DialogFragment {

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getActivity() != null) {
            RefWatcher refWatcher = App.getRefWatcher(getActivity());
            refWatcher.watch(this);
        }
    }
}
