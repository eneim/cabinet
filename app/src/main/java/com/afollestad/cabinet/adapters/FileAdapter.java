package com.afollestad.cabinet.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.bookmarks.BookmarkProvider;
import com.afollestad.cabinet.cab.CopyCab;
import com.afollestad.cabinet.cab.CutCab;
import com.afollestad.cabinet.cab.base.BaseFileCab;
import com.afollestad.cabinet.file.FileInfo;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.content.ContentFragment;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.cabinet.utils.TimeUtils;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.utils.VectorDrawableMap;
import com.afollestad.cabinet.views.IconizedMenu;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.Request;
import com.stericson.RootShell.exceptions.RootDeniedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private static final int NORMAL = 0;
    private static final int GRID = 1;
    private static final int COMPACT = 2;

    protected final MainActivity mContext;
    protected final List<File> mFiles;

    protected final ItemClickListener mListener;
    protected final boolean mDirectoryCount;
    protected final boolean mLoadThumbnails;

    private final MenuClickListener mMenuListener;
    private final boolean mShowParentDir;
    private final List<String> mCheckedPaths;

    public boolean showLastModified;
    private boolean mGridMode;
    private boolean mCompactMode;
    private boolean mStoppedAnimation;
    private int mOffset;
    private Comparator<File> mComparator;

    private RecyclerView mRecyclerView;

    public FileAdapter(Activity context, ItemClickListener listener, MenuClickListener menuListener, boolean showParentDir) {
        mContext = (MainActivity) context;
        mFiles = new ArrayList<>();
        mListener = listener;
        mMenuListener = menuListener;
        mShowParentDir = showParentDir;
        mCheckedPaths = new ArrayList<>();
        mGridMode = Utils.getGridSize(context) > 1;
        mCompactMode = Utils.isCompactMode(context);
        mDirectoryCount = ThemeUtils.isDirectoryCount(context);
        mLoadThumbnails = ThemeUtils.isLoadThumbnails(context);
    }

    public FileAdapter(Activity context, ItemClickListener listener, MenuClickListener menuListener, boolean showParentDir, Comparator<File> comparator) {
        this(context, listener, menuListener, showParentDir);
        mComparator = comparator;
    }

    public static void loadThumbnail(final ThemableActivity context, final File fi, boolean isDirectory,
                                     boolean isHidden, String extension, final FileViewHolder holder, boolean loadThubmnails) {
        ImageView icon = holder.icon;
        if (icon == null) return;
        final String mime = fi.getMimeType();
        final VectorDrawableMap vectorMap = App.getVectorMap();

        if (isDirectory) {
            icon.setImageDrawable(vectorMap.get(context, R.drawable.ic_folder, isHidden));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else if (fi instanceof PluginFileImpl &&
                ((PluginFileImpl) fi).getWrapper().getThumbnail() != null) {
            final Drawable fallback = vectorMap.get(context, R.drawable.ic_file_misc, isHidden);
            if (!loadThubmnails) {
                icon.setImageDrawable(fallback);
            } else {
                final String thumbnail = ((PluginFileImpl) fi).getWrapper().getThumbnail();
                holder.iconLoadingRequest = Glide.with(context)
                        .load(thumbnail)
                        .error(fallback)
                        .placeholder(fallback)
                        .dontAnimate()
                        .into(icon)
                        .getRequest();
            }
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else if (mime != null) {
            if (mime.startsWith("image/") || mime.startsWith("video/") ||
                    fi.getExtension().equalsIgnoreCase("mkv")) {
                Drawable fallback = vectorMap.get(context, mime.startsWith("image/") ? R.drawable.ic_file_image : R.drawable.ic_file_video, isHidden);
                if (!loadThubmnails) {
                    icon.setImageDrawable(fallback);
                } else {
                    holder.iconLoadingRequest = Glide.with(context)
                            .load(fi.getUri())
                            .error(fallback)
                            .placeholder(fallback)
                            .dontAnimate()
                            .into(icon)
                            .getRequest();
                }
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else if (mime.equals("application/vnd.android.package-archive")) {
                Drawable fallback = vectorMap.get(context, R.drawable.ic_file_apk, isHidden);
                if (fi.isDocumentTreeFile() || fi.getPluginPackage() != null || !loadThubmnails) {
                    icon.setImageDrawable(fallback);
                } else {
                    holder.iconLoadingRequest = Glide.with(context)
                            .load(fi)
                            .error(fallback)
                            .placeholder(fallback)
                            .into(icon)
                            .getRequest();
                }
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                int resId = R.drawable.ic_file_misc;
                if (extension != null && !extension.trim().isEmpty()) {
                    final List<String> codeExts = Arrays.asList(context.getResources().getStringArray(R.array.code_extensions));
                    final List<String> archiveExts = Arrays.asList(context.getResources().getStringArray(R.array.supported_archive_extensions));
                    final List<String> textExts = Arrays.asList(context.getResources().getStringArray(R.array.other_text_extensions));
                    if (mime.startsWith("audio/") || mime.equals("application/ogg")) {
                        resId = R.drawable.ic_file_audio;
                    } else if (mime.equals("application/pdf")) {
                        resId = R.drawable.ic_file_pdf;
                    } else if (archiveExts.contains(extension)) {
                        resId = R.drawable.ic_file_zip;
                    } else if (mime.startsWith("model/")) {
                        resId = R.drawable.ic_file_model;
                    } else if (extension.equals("doc") || extension.equals("docx") ||
                            mime.startsWith("text/") || textExts.contains(extension)) {
                        resId = R.drawable.ic_file_doc;
                    } else if (extension.equals("ppt") || extension.equals("pptx")) {
                        resId = R.drawable.ic_file_ppt;
                    } else if (extension.equals("xls") || extension.equals("xlsx")) {
                        resId = R.drawable.ic_file_excel;
                    } else if (extension.equals("ttf")) {
                        resId = R.drawable.ic_file_font;
                    } else if (extension.equals("sh") || extension.equals("bat") ||
                            codeExts.contains(extension)) {
                        resId = R.drawable.ic_file_code;
                    }
                }
                icon.setImageDrawable(vectorMap.get(context, resId, isHidden));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        } else {
            icon.setImageDrawable(vectorMap.get(context, R.drawable.ic_file_misc, isHidden));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    private static void setupTouchDelegate(Context context, final View menu) {
        final int offset = context.getResources().getDimensionPixelSize(R.dimen.menu_touchdelegate);
        assert menu.getParent() != null;
        ((View) menu.getParent()).post(new Runnable() {
            public void run() {
                Rect delegateArea = new Rect();
                menu.getHitRect(delegateArea);
                delegateArea.top -= offset;
                delegateArea.bottom += offset;
                delegateArea.left -= offset;
                delegateArea.right += offset;
                TouchDelegate expandedArea = new TouchDelegate(delegateArea, menu);
                ((View) menu.getParent()).setTouchDelegate(expandedArea);
            }
        });
    }

    public void setComparator(Comparator<File> comparator) {
        mComparator = comparator;
        set(null, true);
    }

    private void sort() {
        if (mComparator != null) {
            Collections.sort(mFiles, mComparator);
        }
    }

    public void performSelect(int index) {
        if (index > mFiles.size() - 1)
            return;
        File file = mFiles.get(index);
        boolean checked = !isItemChecked(file);
        setItemChecked(file, checked);
        if (mListener != null)
            mListener.onItemLongClick(index, file, checked);
    }

    public void stopAnimation() {
        if (!mStoppedAnimation && mRecyclerView != null) {
            int childCount = mRecyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                mRecyclerView.getChildAt(i).clearAnimation();
            }
        }
        mStoppedAnimation = true;
    }

    @Override
    public boolean onFailedToRecycleView(FileViewHolder holder) {
        holder.itemView.clearAnimation();
        return super.onFailedToRecycleView(holder);
    }

    public void add(File file) {
        synchronized (mFiles) {
            mFiles.add(file);
            sort();
            notifyItemInserted(indexOfFile(file));
        }
    }

    public void addIfNotExists(File file) {
        synchronized (mFiles) {
            for (File fi : mFiles) {
                if (fi.equals(file))
                    return;
            }
            add(file);
        }
    }

    public void update(File old, File replace) {
        synchronized (mFiles) {
            for (int i = 0; i < mFiles.size(); i++) {
                if (mFiles.get(i).equals(old)) {
                    mFiles.set(i, replace);
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public void set(List<? extends File> files, boolean showAnimation) {
        synchronized (mFiles) {
            if (showAnimation) {
                mStoppedAnimation = false;
            }
            mOffset = 0;
            if (files != null) {
                mFiles.clear();
                mFiles.addAll(files);
            }
            sort();
            notifyDataSetChanged();
        }
    }

    public void updateFileInfoFromFile(File file) {
        synchronized (mFiles) {
            for (int i = 0, filesSize = mFiles.size(); i < filesSize; i++) {
                File fileInList = mFiles.get(i);
                if (fileInList.equals(file)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public void clear() {
        synchronized (mFiles) {
            mFiles.clear();
            notifyDataSetChanged();
        }
    }

    public void remove(File file) {
        synchronized (mFiles) {
            for (int i = 0; i < mFiles.size(); i++) {
                if (mFiles.get(i).equals(file)) {
                    mFiles.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
        }
    }

    public List<File> getFiles() {
        return mFiles;
    }

    public void changeLayout() {
        mGridMode = Utils.getGridSize(mContext) > 1;
        mCompactMode = Utils.isCompactMode(mContext);
        set(null, true);
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
    }

    @Override
    public int getItemViewType(int position) {
        return mGridMode ? GRID : mCompactMode ? COMPACT : NORMAL;
    }

    @Override
    public FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                viewType == GRID ? R.layout.list_item_file_grid
                        : viewType == COMPACT ? R.layout.list_item_file_compact
                        : R.layout.list_item_file,
                parent, false);
        if (mShowParentDir)
            v.findViewById(R.id.content2).setVisibility(View.VISIBLE);
        return new FileViewHolder(v);
    }

    @Override
    public void onViewDetachedFromWindow(FileViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.itemView.clearAnimation();
    }

    @Override
    public void onBindViewHolder(final FileViewHolder holder, int index) {
        final File file = mFiles.get(index);

        final FileInfo fileInfo = new FileInfo(file, mContext);

        if (mGridMode) {
            holder.icon.setFocusable(false);
            holder.icon.setFocusableInTouchMode(false);
        }

        if ((holder.fileAndFileInfo == null || !holder.fileAndFileInfo.file.equals(file))
                && !mStoppedAnimation) {
            holder.itemView.clearAnimation();
            Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.list_itemdown);
            animation.setInterpolator(new LinearOutSlowInInterpolator());
            animation.setStartOffset(mOffset);
            holder.itemView.startAnimation(animation);
            mOffset += 20;
        }

        if (holder.fileAndFileInfo == null || !holder.fileAndFileInfo.fileInfo.equals(fileInfo)) {
            loadThumbnail(mContext, file, holder);

            holder.title.setText(file.getName());

            holder.linkImage.setVisibility(View.GONE);

            if (file.isSymlink()) {
                //noinspection ConstantConditions
                holder.content.setText(file.getRealFile().getPath());
                holder.linkImage.setVisibility(View.VISIBLE);
                holder.linkImage.setColorFilter(mContext.getThemeUtils().thumbnailColor(), PorterDuff.Mode.SRC_ATOP);
            } else if (file.isDirectory()) {
                holder.content.setText(R.string.directory);
                if (!fileInfo.directoryFileCount.isEmpty()) {
                    holder.content.setText(fileInfo.directoryFileCount);
                } else if (mDirectoryCount) {
                    BackgroundThread.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                List<File> files = file.listFiles();
                                file.setDirectoryFileCount(ContentFragment.getDirectoryFileCount(mContext, files));
                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateFileInfoFromFile(file);
                                    }
                                });
                            } catch (RootDeniedException | TimeoutException | IOException | RemoteException |
                                    PluginFileImpl.ResultException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } else {
                if (!fileInfo.readAvailable) {
                    holder.content.setText(mContext.getString(R.string.superuser_not_available));
                } else {
                    holder.content.setText(File.readableFileSize(fileInfo.size));
                }
            }
        }

        if (mShowParentDir) {
            if (!file.isDirectory())
                holder.content2.setText(file.getParent().getPath());
            else holder.content2.setVisibility(View.GONE);
        } else if (showLastModified) {
            if (file.lastModified() == -1) {
                holder.content2.setVisibility(View.GONE);
            } else {
                holder.content2.setVisibility(View.VISIBLE);
                Calendar cal = new GregorianCalendar();
                cal.setTimeInMillis(file.lastModified());
                holder.content2.setText(mContext.getString(R.string.modified_x, TimeUtils.toString(mContext, cal, true, true)));
            }
        } else holder.content2.setVisibility(View.GONE);

        holder.view.setActivated(isItemChecked(file));

        holder.fileAndFileInfo = new FileAndFileInfo(file, fileInfo);
    }

    protected void loadThumbnail(ThemableActivity context, File file, FileViewHolder holder) {
        loadThumbnail(context, file, file.isDirectory(), file.isHidden(),
                file.getExtension(), holder, mLoadThumbnails);
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    /**
     * Returns if file infos are the same.
     */
    private int indexOfFile(File file) {
        for (int i = 0; i < mFiles.size(); i++) {
            if (mFiles.get(i).equals(file))
                return i;
        }
        return -1;
    }

    public void setItemChecked(File file, boolean checked) {
        if (mCheckedPaths.contains(file.getPath()) && !checked) {
            for (int i = 0; i < mCheckedPaths.size(); i++) {
                if (mCheckedPaths.get(i).equals(file.getPath())) {
                    mCheckedPaths.remove(i);
                    break;
                }
            }
        } else if (!mCheckedPaths.contains(file.getPath()) && checked) {
            mCheckedPaths.add(file.getPath());
        }
        notifyItemChanged(indexOfFile(file));
    }

    public void setItemsChecked(List<File> files, boolean checked) {
        for (File fi : files) {
            setItemChecked(fi, checked);
        }
    }

    boolean isItemChecked(File file) {
        return mCheckedPaths.contains(file.getPath());
    }

    public void resetChecked() {
        for (Iterator<String> iterator = mCheckedPaths.iterator(); iterator.hasNext(); ) {
            String path = iterator.next();
            iterator.remove();
            for (int i = 0; i < mFiles.size(); i++) {
                File f = mFiles.get(i);
                if (path != null && path.equals(f.getPath()))
                    notifyItemChanged(indexOfFile(f));
            }

        }
    }

    public List<File> checkAll() {
        List<File> newlySelected = new ArrayList<>();
        for (int i = 0; i < mFiles.size(); i++) {
            File file = mFiles.get(i);
            String path = file.getPath();
            if (!mCheckedPaths.contains(path)) {
                mCheckedPaths.add(path);
                newlySelected.add(file);
                notifyItemChanged(i);
            }
        }
        return newlySelected;
    }

    public void restoreCheckedPaths(List<File> paths) {
        if (paths == null) return;
        resetChecked();
        for (int i = 0; i < paths.size(); i++) {
            File fi = paths.get(i);
            mCheckedPaths.add(fi.getPath());
            notifyItemChanged(i);
        }
    }

    public interface ItemClickListener {
        void onItemClicked(int index, File file);

        void onItemLongClick(int index, File file, boolean added);
    }

    public interface MenuClickListener {
        void onMenuItemClick(File file, MenuItem item);
    }

    /**
     * Class for storing currently displayed FileInfo in holders
     */
    private class FileAndFileInfo {

        final FileInfo fileInfo;
        final File file;

        FileAndFileInfo(File file, FileInfo info) {
            this.fileInfo = new FileInfo(info);
            this.file = file;
        }
    }

    public class FileViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {

        final View view;
        final ImageView icon;
        final TextView title;
        final TextView content;
        final TextView content2;
        final ImageView linkImage;
        final View menu;

        FileAndFileInfo fileAndFileInfo;
        Request iconLoadingRequest;

        public FileViewHolder(View itemView) {
            super(itemView);
            view = itemView;
            icon = (ImageView) itemView.findViewById(R.id.image);
            title = (TextView) itemView.findViewById(android.R.id.title);
            content = (TextView) itemView.findViewById(android.R.id.content);
            content2 = (TextView) itemView.findViewById(R.id.content2);
            linkImage = (ImageView) itemView.findViewById(R.id.link);
            menu = itemView.findViewById(R.id.menu);

            view.setTag("0");
            menu.setTag("2");

            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            if (!mGridMode)
                icon.setOnClickListener(this);
            menu.setOnClickListener(this);

            setupTouchDelegate(mContext, menu);
        }

        @Override
        public void onClick(View view) {
            try {
                stopAnimation();
                int type = 1;
                if (view.getTag() != null && view.getTag() instanceof String)
                    type = Integer.parseInt((String) view.getTag());
                final int index = getLayoutPosition();
                if (index > mFiles.size() - 1)
                    return;
                File file = mFiles.get(index);
                if (type == 0) {  // item
                    if (mListener != null)
                        mListener.onItemClicked(index, file);
                } else if (type == 2) {  // menu
                    IconizedMenu popupMenu = new IconizedMenu(mContext, view);
                    popupMenu.inflate(file.isDirectory() ? R.menu.dir_options : R.menu.file_options);
                    boolean foundInCopyCab = false;
                    boolean foundInCutCab = false;
                    if (mContext.getCab() instanceof CopyCab) {
                        foundInCopyCab = ((BaseFileCab) mContext.getCab()).containsFile(file);
                    } else if (mContext.getCab() instanceof CutCab) {
                        foundInCutCab = ((BaseFileCab) mContext.getCab()).containsFile(file);
                    }
                    popupMenu.getMenu().findItem(R.id.copy).setVisible(!foundInCopyCab);
                    popupMenu.getMenu().findItem(R.id.move).setVisible(!foundInCutCab);
                    if (file.isDirectory()) {
                        popupMenu.getMenu().findItem(R.id.bookmark).setVisible(!
                                BookmarkProvider.contains(mContext, new BookmarkProvider.Item(file)));
                    } else {
                        MenuItem zip = popupMenu.getMenu().findItem(R.id.archive);
                        zip.setVisible(true);
                        if (file.isArchiveOrInArchive(mContext))
                            zip.setTitle(R.string.extract_to);
                        else zip.setTitle(R.string.archive);
                    }
                    popupMenu.setOnMenuItemClickListener(new IconizedMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            if (index > mFiles.size() - 1) return false;
                            mMenuListener.onMenuItemClick(mFiles.get(index), menuItem);
                            return true;
                        }
                    });
                    popupMenu.show();
                } else if (view instanceof ImageView) {
                    performSelect(index);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @SuppressLint("CommitPrefEdits")
        @Override
        public boolean onLongClick(View view) {
            stopAnimation();
            int index = getLayoutPosition();
            if (index > mFiles.size() - 1)
                return true;
            performSelect(index);
            return true;
        }
    }
}