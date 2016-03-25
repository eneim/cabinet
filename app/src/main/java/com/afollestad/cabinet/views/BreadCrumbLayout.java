package com.afollestad.cabinet.views;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.DocumentFileWrapper;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.PluginDataProvider;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class BreadCrumbLayout extends HorizontalScrollView implements View.OnClickListener {

    public static class Crumb implements Serializable {

        public Crumb(File file) {
            mFile = file;
        }

        private final File mFile;
        private int mScrollPos;
        private int mScrollOffset;
        private String mQuery;

        public String getQuery() {
            return mQuery;
        }

        public void setQuery(String query) {
            this.mQuery = query;
        }

        public int getScrollPosition() {
            return mScrollPos;
        }

        public int getScrollOffset() {
            return mScrollOffset;
        }

        public void setScrollPosition(int scrollY) {
            this.mScrollPos = scrollY;
        }

        public void setScrollOffset(int scrollOffset) {
            this.mScrollOffset = scrollOffset;
        }

        public String getTitle(Context context) {
            if (mFile.getPluginPackage() != null) {
                if (PluginFileImpl.isRootPluginFolder(mFile.getUri(), ((PluginFileImpl) mFile).getPlugin())) {
                    final String pluginAcc = mFile.getPluginAccount();
                    if (pluginAcc != null) {
                        final String accDisplay = PluginDataProvider.getAccountDisplay(context, mFile.getPluginPackage(), pluginAcc);
                        if (accDisplay == null || accDisplay.trim().isEmpty())
                            return pluginAcc;
                        return accDisplay;
                    }
                }
            }
            String title = mFile.getDisplay(context);
            if (title == null)
                title = mFile.getName();
            else if (title.equals(context.getString(R.string.storage)))
                title = context.getString(R.string.internal_storage);
            return title;
        }

        public File getFile() {
            return mFile;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Crumb) && ((Crumb) o).getFile() != null &&
                    ((Crumb) o).getFile().equals(getFile());
        }

        @Override
        public String toString() {
            if (getQuery() != null)
                return "Search (" + getQuery() + "): " + getFile().toString();
            else if (getFile() != null)
                return getFile().toString();
            else
                return super.toString();
        }
    }

    public interface SelectionCallback {
        void onCrumbSelection(Crumb crumb, int index);
    }

    public BreadCrumbLayout(Context context) {
        super(context);
        init();
    }

    public BreadCrumbLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BreadCrumbLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // Stores currently visible crumbs
    private List<Crumb> mCrumbs;
    // Used in setActiveOrAdd() between clearing crumbs and adding the new set, nullified afterwards
    private List<Crumb> mOldCrumbs;
    // Stores user's navigation history, like a fragment back stack
    private List<Crumb> mHistory;

    private LinearLayout mChildFrame;
    private int mActive;
    private SelectionCallback mCallback;
    private FragmentManager mFragmentManager;

    private void init() {
        setMinimumHeight((int) getResources().getDimension(R.dimen.breadcrumb_height));
        setClipToPadding(false);
        setHorizontalScrollBarEnabled(false);
        mCrumbs = new ArrayList<>();
        mHistory = new ArrayList<>();
        mChildFrame = new LinearLayout(getContext());
        addView(mChildFrame, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void addHistory(Crumb crumb) {
        mHistory.add(crumb);
    }

    public Crumb lastHistory() {
        if (mHistory.size() == 0) return null;
        return mHistory.get(mHistory.size() - 1);
    }

    public boolean popHistory() {
        if (mHistory.size() == 0) return false;
        mHistory.remove(mHistory.size() - 1);
        return mHistory.size() != 0;
    }

//    public int historySize() {
//        return mHistory.size();
//    }

    public void clearHistory() {
        mHistory.clear();
    }

    public void reverseHistory() {
        Collections.reverse(mHistory);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAlpha(View view, int alpha) {
        if (view instanceof ImageView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ((ImageView) view).setImageAlpha(alpha);
        } else {
            ViewCompat.setAlpha(view, alpha);
        }
    }

    public void setFragmentManager(FragmentManager fm) {
        this.mFragmentManager = fm;
    }

    public void addCrumb(@NonNull Crumb crumb, boolean refreshLayout) {
        LinearLayout view = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.bread_crumb, this, false);
        view.setTag(mCrumbs.size());
        view.setOnClickListener(this);

        ImageView iv = (ImageView) view.getChildAt(1);
        Drawable arrow = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_right_arrow, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            assert arrow != null;
            arrow.setAutoMirrored(true);
        }

        iv.setImageDrawable(arrow);
        iv.setVisibility(View.GONE);

        mChildFrame.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mCrumbs.add(crumb);
        if (refreshLayout) {
            mActive = mCrumbs.size() - 1;
            requestLayout();
        }
        invalidateActivatedAll();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        //RTL works fine like this
        View child = mChildFrame.getChildAt(mActive);
        if (child != null)
            smoothScrollTo(child.getLeft(), 0);
    }

    public Crumb findCrumb(@NonNull File forDir) {
        for (int i = 0; i < mCrumbs.size(); i++) {
            if (mCrumbs.get(i).getFile().equals(forDir))
                return mCrumbs.get(i);
        }
        return null;
    }

    public void clearCrumbs() {
        try {
            mOldCrumbs = new ArrayList<>(mCrumbs);
            mCrumbs.clear();
            mChildFrame.removeAllViews();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public Crumb getCrumb(int index) {
        return mCrumbs.get(index);
    }

    public void setCallback(SelectionCallback callback) {
        mCallback = callback;
    }

    private boolean setActive(Crumb newActive) {
        mActive = mCrumbs.indexOf(newActive);
        invalidateActivatedAll();
        boolean success = mActive > -1;
        if (success)
            requestLayout();
        return success;
    }

    void invalidateActivatedAll() {
        for (int i = 0; i < mCrumbs.size(); i++) {
            Crumb crumb = mCrumbs.get(i);
            invalidateActivated(mChildFrame.getChildAt(i), mActive == mCrumbs.indexOf(crumb), false, i < mCrumbs.size() - 1)
                    .setText(crumb.getTitle(getContext()));
        }
    }

    void removeCrumbAt(int index) {
        mCrumbs.remove(index);
        mChildFrame.removeViewAt(index);
    }

    public boolean trim(String str, boolean dir, boolean archive) {
        if (!dir && !archive) return false;
        int index = -1;
        for (int i = mCrumbs.size() - 1; i >= 0; i--) {
            File fi = mCrumbs.get(i).getFile();
            if (fi.getUri() != null && fi.getUri().toString().equals(str)) {
                index = i;
                break;
            }
        }

        boolean removedActive = index >= mActive;
        if (index > -1) {
            while (index <= mCrumbs.size() - 1)
                removeCrumbAt(index);
            if (mChildFrame.getChildCount() > 0) {
                int lastIndex = mCrumbs.size() - 1;
                invalidateActivated(mChildFrame.getChildAt(lastIndex), mActive == lastIndex, false, false);
            }
        }
        return removedActive || mCrumbs.size() == 0;
    }

    public boolean trim(File file) {
        return trim(file.getUri().toString(), file.isDirectory(), file.isViewableArchive(getContext()));
    }

    void updateIndices() {
        for (int i = 0; i < mChildFrame.getChildCount(); i++)
            mChildFrame.getChildAt(i).setTag(i);
    }

    public static boolean isStorageOrSd(File fi) {
        if (fi == null) return false;
        else if (fi.getPluginPackage() != null)
            return File.isRootPluginFolder(fi.getUri(), ((PluginFileImpl) fi).getPlugin());
        return fi.isSDCardDirectory() || fi.isStorageDirectory() || (fi.isDocumentTreeFile() && fi.getParent() == null);
    }

    public void setActiveOrAdd(@NonNull Crumb crumb, boolean forceRecreate) {
        if (forceRecreate || !setActive(crumb)) {
            clearCrumbs();
            final List<File> newPathSet = new ArrayList<>();

            newPathSet.add(0, crumb.getFile());

            File p = crumb.getFile();
            if (!isStorageOrSd(p)) {
                while ((p = p.getParent()) != null) {
                    newPathSet.add(0, p);
                    if (isStorageOrSd(p))
                        break;
                }
            }

            for (int index = 0; index < newPathSet.size(); index++) {
                final File fi = newPathSet.get(index);
                crumb = new Crumb(fi);

                // Restore scroll positions saved before clearing
                if (mOldCrumbs != null) {
                    for (Iterator<Crumb> iterator = mOldCrumbs.iterator(); iterator.hasNext(); ) {
                        Crumb old = iterator.next();
                        if (old.equals(crumb)) {
                            crumb.setScrollPosition(old.getScrollPosition());
                            crumb.setScrollOffset(old.getScrollOffset());
                            crumb.setQuery(old.getQuery());
                            iterator.remove(); // minimize number of linear passes by removing un-used crumbs from history
                            break;
                        }
                    }
                }

                addCrumb(crumb, true);
            }

            // History no longer needed
            mOldCrumbs = null;
        } else {
            if (isStorageOrSd(crumb.getFile())) {
                Crumb c = mCrumbs.get(0);
                while (c != null && !isStorageOrSd(c.getFile())) {
                    removeCrumbAt(0);
                    if (mCrumbs.size() > 0)
                        c = mCrumbs.get(0);
                }
                updateIndices();
                requestLayout();
            }
        }
    }

    public int size() {
        return mCrumbs.size();
    }

    private TextView invalidateActivated(View view, boolean isActive, boolean noArrowIfAlone, boolean allowArrowVisible) {
        LinearLayout child = (LinearLayout) view;
        TextView tv = (TextView) child.getChildAt(0);
        tv.setTextColor(ContextCompat.getColor(getContext(), isActive ? R.color.crumb_active : R.color.crumb_inactive));
        ImageView iv = (ImageView) child.getChildAt(1);
        setAlpha(iv, isActive ? 255 : 109);
        if (noArrowIfAlone && getChildCount() == 1)
            iv.setVisibility(View.GONE);
        else if (allowArrowVisible)
            iv.setVisibility(View.VISIBLE);
        else
            iv.setVisibility(View.GONE);
        return tv;
    }

    public int getActiveIndex() {
        return mActive;
    }

    @Override
    public void onClick(View v) {
        if (mCallback != null) {
            int index = (Integer) v.getTag();
            mCallback.onCrumbSelection(mCrumbs.get(index), index);
        }
    }


    public static class SavedStateWrapper implements Serializable {

        public final int mActive;
        public final List<Crumb> mCrumbs;
        public final int mVisibility;

        public SavedStateWrapper(BreadCrumbLayout view) {
            mActive = view.mActive;
            mCrumbs = view.mCrumbs;
            mVisibility = view.getVisibility();
        }
    }

    public SavedStateWrapper getStateWrapper() {
        return new SavedStateWrapper(this);
    }

    public void restoreFromStateWrapper(SavedStateWrapper mSavedState, Activity context) {
        if (mSavedState != null) {
            mActive = mSavedState.mActive;
            for (Crumb c : mSavedState.mCrumbs) {
                if (c.getFile().isDocumentTreeFile())
                    ((DocumentFileWrapper) c.getFile()).restoreWrapped(context);
                addCrumb(c, false);
            }
            requestLayout();
            setVisibility(mSavedState.mVisibility);
        }
    }
}