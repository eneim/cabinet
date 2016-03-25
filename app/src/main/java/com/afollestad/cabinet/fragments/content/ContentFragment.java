package com.afollestad.cabinet.fragments.content;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.adapters.FileAdapter;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.base.LeakDetectFragment;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.utils.ViewUtils;
import com.afollestad.cabinet.views.BreadCrumbLayout;
import com.afollestad.materialdialogs.util.DialogUtils;
import com.pluscubed.recyclerfastscroll.RecyclerFastScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Content Fragment
 */
public abstract class ContentFragment extends LeakDetectFragment {

    protected static final String STATE_PATH = "path";
    public FileAdapter mAdapter;
    protected BreadCrumbLayout.Crumb crumb;
    protected GridLayoutManager mLayoutManager;
    protected RecyclerView mRecyclerView;
    protected File mDirectory;
    private RecyclerFastScroller mFastScroller;
    private boolean mSavedInstanceStateNull;
    private View mEmpty;

    public static String getDirectoryFileCount(Context context, List<? extends File> files) {
        if (files == null || files.isEmpty()) {
            return context.getString(R.string.empty);
        }
        int fileCount = 0;
        int folderCount = 0;
        for (File e : files) {
            if (e.isDirectory()) folderCount++;
            else fileCount++;
        }
        if (fileCount == 1 && folderCount == 0) {
            return context.getString(R.string.one_file);
        } else if (fileCount == 0 && folderCount == 1) {
            return context.getString(R.string.one_folder);
        } else if (fileCount > 0 && folderCount == 0) {
            return context.getString(R.string.x_files, fileCount);
        } else if (fileCount == 0 && folderCount > 0) {
            return context.getString(R.string.x_folders, folderCount);
        } else if (fileCount == 1 && folderCount == 1) {
            return context.getString(R.string.one_file_one_folder);
        } else if (fileCount != 1 && folderCount == 1) {
            return context.getString(R.string.x_files_one_folder, fileCount);
        } else if (fileCount == 1) {
            return context.getString(R.string.one_file_x_folders, folderCount);
        } else {
            return context.getString(R.string.x_files_x_folders, fileCount, folderCount);
        }
    }

    protected abstract FileAdapter newAdapter();

    public final void setListShown(boolean shown) {
        View v = getView();
        if (v != null) {
            if (shown) {
                v.findViewById(android.R.id.progress).setVisibility(View.INVISIBLE);
                invalidateEmpty(false);
            } else {
                v.findViewById(android.R.id.progress).setVisibility(View.VISIBLE);
                invalidateEmpty(true);
            }
        }
    }

    public void invalidateEmpty(boolean overrideNotEmpty) {
        if (mEmpty != null) {
            boolean showEmpty = mAdapter.getItemCount() == 0 && !overrideNotEmpty;
            mEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        }
    }

    protected void invalidateTitle() {
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            if (act.mCrumbs.getVisibility() == View.VISIBLE)
                act.setTitle(R.string.app_name);
            else {
                act.setTitle(mDirectory.getDisplay(act));
            }
        }
    }

    protected void invalidateSubtitle(List<? extends File> results) {
        if (getActivity() != null) {
            MainActivity act = (MainActivity) getActivity();
            if (act.getSupportActionBar() != null) {
                if (PreferenceManager.getDefaultSharedPreferences(act).getBoolean("directory_count_toolbar", true))
                    act.getSupportActionBar().setSubtitle(getDirectoryFileCount(getActivity(), results));
                else act.getSupportActionBar().setSubtitle(null);
            }
        }
    }

    public File getDirectory() {
        return mDirectory;
    }

    /**
     * Set the directory (different from the current one).
     */
    public void setDirectory(File directory) {
        saveScrollPosition();
        showAppBarAndInvalidateTopPadding();
        mDirectory = directory;
        invalidateCrumbs(false);
        invalidateTitle();
        getActivity().invalidateOptionsMenu();
        reload(true);
        invalidateCabAndFab();
    }

    public abstract void reload(boolean showAnimation);

    protected abstract void invalidateCabAndFab();

    public void successListing() {
        if (!isAdded() || isDetached())
            return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (getView() == null || !isAdded() || isDetached())
                    return;
                ((ImageView) getView().findViewById(R.id.emptyImage)).setImageResource(
                        Utils.resolveDrawable(getActivity(), R.attr.empty_image));
                String filterDisplay = getFilterDisplay();
                if (filterDisplay != null) {
                    setEmptyText(Html.fromHtml(getString(R.string.no_filter_files, filterDisplay)));
                } else {
                    setEmptyText(getString(R.string.no_files));
                }
                setListShown(true);
            }
        });
    }

    protected String getFilterDisplay() {
        if (getActivity() == null) return null;
        String filter = Utils.getFilter(getActivity());
        if (filter == null) return null;
        else if (filter.equals("archives")) {
            return getString(R.string.archives);
        } else {
            String[] splitFilter = filter.split(":");
            if (splitFilter.length == 2) {
                if (splitFilter[0].equals("mime")) {
                    switch (splitFilter[1]) {
                        case "text":
                            return getString(R.string.text);
                        case "image":
                            return getString(R.string.image);
                        case "audio":
                            return getString(R.string.audio);
                        case "video":
                            return getString(R.string.video);
                    }
                } else if (splitFilter[0].equals("ext")) {
                    return splitFilter[1];
                }
            }
            return splitFilter[0];
        }
    }

    public void errorListing(final String errorMessage) {
        if (!isAdded() || isDetached())
            return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (getView() == null || !isAdded() || isDetached())
                    return;
                mAdapter.set(new ArrayList<File>(), false);
                if (getView() != null) {
                    ((ImageView) getView().findViewById(R.id.emptyImage)).setImageResource(
                            Utils.resolveDrawable(getActivity(), R.attr.empty_image_error));
                    try {
                        String message = errorMessage;
                        if (message == null || message.trim().isEmpty())
                            message = getString(R.string.error);
                        setEmptyText(message);
                        setListShown(true);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    protected final void invalidateCrumbs(final boolean userChanged) {
        final MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            if (userChanged) {
                saveScrollPosition();
                //If user changed whether the crumbs are shown, invalidate padding
                if (act.invalidateShowCrumbs()) {
                    ViewUtils.waitForLayout(act.mCrumbs, new ViewUtils.LayoutCallback() {
                        @Override
                        public void onLayout(View view) {
                            showAppBarAndInvalidateTopPadding();
                        }
                    });
                }
            } else {
                act.invalidateShowCrumbs();
            }
            crumb = act.mCrumbs.findCrumb(mDirectory);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mSavedInstanceStateNull)
            mAdapter.stopAnimation();
        mSavedInstanceStateNull = false;
        invalidateCrumbs(false);
        invalidateTitle();
        reload(false);

        MainActivity act = (MainActivity) getActivity();
        if (act != null && !act.collapsingAppBar) {
            showAppBarAndInvalidateTopPadding();
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recyclerview, container, false);
        mFastScroller = (RecyclerFastScroller) view.findViewById(R.id.fastScroller);
        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);

        final MainActivity act = (MainActivity) getActivity();

        ViewUtils.waitForLayout(mRecyclerView, new ViewUtils.LayoutCallback() {
            @Override
            public void onLayout(View view) {
                jumpToTop(false);
            }
        });

        mFastScroller.setRecyclerView(mRecyclerView);

        View.OnTouchListener onTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mAdapter.stopAnimation();
                if (getActivity() != null) {
                    final MainActivity act = (MainActivity) getActivity();
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_UP:
                            act.finishWithAnimation(mRecyclerView, mFastScroller, mEmpty);
                            break;
                    }
                }
                return false;
            }
        };
        mFastScroller.setOnHandleTouchListener(onTouchListener);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                final MainActivity act = (MainActivity) getActivity();
                if (act != null) {
                    boolean wasHidden = false;
                    if (mLayoutManager != null) {
                        try {
                            final int lastVisible = mLayoutManager.findLastCompletelyVisibleItemPosition();
                            if (lastVisible == mAdapter.getItemCount() - 1) {
                                wasHidden = true;
                                act.toggleFab(false);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (!wasHidden) {
                        if (dy < 0) {
                            if (dy < -5)
                                act.toggleFab(false);
                        } else if (dy > 0) {
                            if (dy > 10)
                                act.toggleFab(true);
                        }
                    }
                }
            }
        });

        if (getActivity() != null)
            mFastScroller.setHandlePressedColor(DialogUtils.resolveColor(getActivity(), R.attr.fastscroll_handle_pressed));

        mLayoutManager = getNewGridLayoutManager(act);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mRecyclerView.setOnTouchListener(onTouchListener);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setSupportsChangeAnimations(false);
        mRecyclerView.setItemAnimator(defaultItemAnimator);

        mAdapter = newAdapter();
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                invalidateEmpty(false);
                invalidateSubtitle(mAdapter.getFiles());
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                invalidateEmpty(false);
                invalidateSubtitle(mAdapter.getFiles());
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        mEmpty = view.findViewById(android.R.id.empty);

        return view;
    }

    @NonNull
    protected GridLayoutManager getNewGridLayoutManager(final MainActivity act) {
        return new GridLayoutManager(getActivity(), Utils.getGridSize(getActivity())) {
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                int scrolled = super.scrollVerticallyBy(dy, recycler, state);
                int overscroll = dy - scrolled;
                if (overscroll <= 0) {
                    //Top overscroll
                    act.notifyScroll(dy, false, mRecyclerView, mFastScroller, mEmpty);
                }
                return scrolled;
            }
        };
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_PATH, mDirectory);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState == null) {
            mDirectory = (File) getArguments().getSerializable(STATE_PATH);
            mSavedInstanceStateNull = true;
        } else {
            mDirectory = (File) savedInstanceState.getSerializable(STATE_PATH);
            mSavedInstanceStateNull = false;
        }
    }

    public void saveScrollPosition() {
        if (crumb == null)
            return;
        try {
            final View firstChild = mRecyclerView.getChildAt(0);
            if (firstChild != null) {
                crumb.setScrollOffset((int) firstChild.getY() - mRecyclerView.getPaddingTop());
                crumb.setScrollPosition(mRecyclerView.getChildAdapterPosition(firstChild));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void restoreScrollPosition() {
        if (crumb == null)
            return;
        try {
            final int scrollPosition = crumb.getScrollPosition();
            int scrollOffset = crumb.getScrollOffset();
            if (scrollPosition == 0 && scrollOffset == 0 && getActivity() != null) {
                showAppBarAndInvalidateTopPadding();
            }
            if (scrollPosition < mAdapter.getItemCount()) {
                mLayoutManager.scrollToPositionWithOffset(scrollPosition, scrollOffset);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void runOnUiThread(Runnable runnable) {
        Activity act = getActivity();
        if (act != null && getView() != null)
            act.runOnUiThread(runnable);
    }

    /**
     * Only call on the UI thread;
     */
    final void setEmptyText(final CharSequence text) {
        View v = getView();
        if (v != null) {
            ((TextView) v.findViewById(R.id.emptyText)).setText(text);
        }
    }

    public final void jumpToTop(boolean animateChange) {
        if (getActivity() != null) {
            showAppBarAndInvalidateTopPadding();
            if (animateChange) {
                mAdapter.stopAnimation();
                mRecyclerView.smoothScrollToPosition(0);
            } else {
                mRecyclerView.scrollToPosition(0);
            }
        }
    }

    public void showAppBarAndInvalidateTopPadding() {
        if (getActivity() != null) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity.appBar != null) {
                ViewUtils.applyTopPadding(activity.appBar.getHeight(), mRecyclerView, mFastScroller, mEmpty);
                activity.notifyScroll(0, true);
            }
        }
    }
}
