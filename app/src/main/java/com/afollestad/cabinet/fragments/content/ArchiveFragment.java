package com.afollestad.cabinet.fragments.content;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.adapters.ArchiveSubFileAdapter;
import com.afollestad.cabinet.adapters.FileAdapter;
import com.afollestad.cabinet.cab.base.BaseCab;
import com.afollestad.cabinet.cram.Cram;
import com.afollestad.cabinet.cram.Reader;
import com.afollestad.cabinet.file.ArchiveFile;
import com.afollestad.cabinet.file.ArchiveSubFile;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.PluginFileImpl;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.IPluginService;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginErrorResult;
import com.afollestad.cabinet.plugins.PluginUriResult;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.ui.base.PluginActivity;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.cabinet.zip.Unzipper;
import com.afollestad.cabinet.zip.Zipper;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

public class ArchiveFragment extends ContentFragment implements ArchiveSubFileAdapter.ItemClickListener {

    private final static int CHOOSE_DESTINATION_REQUEST = 7001;

    private ArchiveFile mZipFile;
    private Handler mHandler;
    private File mCacheFile;
    private Plugin.Callback mConnectionCallback;

    public static ArchiveFragment newInstance(File zipFile) {
        ArchiveFragment frag = new ArchiveFragment();
        frag.mDirectory = zipFile;
        Bundle b = new Bundle();
        b.putSerializable(STATE_PATH, zipFile);
        frag.setArguments(b);
        return frag;
    }

    @Override
    protected FileAdapter newAdapter() {
        return new ArchiveSubFileAdapter((ThemableActivity) getActivity(), this);
    }

    protected void invalidateCabAndFab() {
        MainActivity act = (MainActivity) getActivity();
        if (act != null) {
            act.toggleFab(true);
            BaseCab cab = act.getCab();
            if (cab != null)
                cab.setFragment(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        invalidateCabAndFab();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.archive_viewer, menu);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        setEmptyText(getString(R.string.no_files));
        return v;
    }


    @Override
    public void reload(final boolean showAnimation) {
        final View v = getView();
        final MainActivity act = (MainActivity) getActivity();
        if (act == null || v == null)
            return;
        setListShown(false);

        ((ImageView) v.findViewById(R.id.emptyImage)).setImageResource(
                Utils.resolveDrawable(act, R.attr.empty_image));

        mHandler = new Handler();
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                prepareAndReadFinal(showAnimation);
            }
        });
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.extract:
                startActivityForResult(new Intent(getActivity(), MainActivity.class)
                        .setAction(Intent.ACTION_GET_CONTENT)
                        .putExtra("extract_mode", true), CHOOSE_DESTINATION_REQUEST);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHOOSE_DESTINATION_REQUEST && resultCode == Activity.RESULT_OK) {
            final File dest = new LocalFile(new java.io.File(data.getData().getPath()));
            final List<File> target = new ArrayList<>();
            target.add(mCacheFile != null ? mCacheFile : mZipFile);

            Unzipper.unzip((PluginActivity) getActivity(), dest, target, new Zipper.ZipCallback() {
                @Override
                public void onComplete() {
                    if (getActivity() != null)
                        ((MainActivity) getActivity()).switchDirectory(dest);
                }
            });
        }
    }

    @Override
    public void onClick(int index) {
        final ArchiveSubFile entry = (ArchiveSubFile) mAdapter.getFiles().get(index);
        if (entry.isDirectory()) {
            saveScrollPosition();
            if (getActivity() != null)
                ((MainActivity) getActivity()).switchDirectory(entry);
        } else {
            final MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                    .progress(true, 0)
                    .content(Html.fromHtml(getString(R.string.extracting_file, entry.getName())))
                    .show();
            BackgroundThread.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        final File fi = findEntryAndExtract(entry.getName());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Utils.openFile(ArchiveFragment.this, fi, false);
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Utils.showErrorDialog(getActivity(), R.string.failed_extract_file, e);
                            }
                        });
                    }
                    if (dialog != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                            }
                        });
                    }
                }
            });
        }
    }

    private File findEntryAndExtract(String name) throws Exception {
        if (getActivity() == null)
            return null;
        java.io.File cacheDir = getActivity().getExternalCacheDir();
        assert cacheDir != null;
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        return Cram.with((PluginActivity) getActivity())
                .extractor(mCacheFile != null ? mCacheFile : mZipFile)
                .to(new LocalFile(cacheDir))
                .target(name)
                .complete(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        // Helps prevent callback from leaking, and cancels the connection process if possible
        if (mConnectionCallback != null)
            mConnectionCallback.cancelled = true;
    }

    @WorkerThread
    private void prepareAndReadFinal(final boolean showAnimation) {
        if (mZipFile == null) {
            if (getDirectory().getPluginPackage() != null) {
                PluginFileImpl pfi = (PluginFileImpl) getDirectory();
                mConnectionCallback = new Plugin.Callback() {
                    @Override
                    public void onResult(Plugin plugin, Exception e) {
                        if (getDirectory() == null || getActivity() == null || !isAdded())
                            return;
                        else if (e != null) {
                            errorListing(e.getLocalizedMessage());
                            return;
                        }
                        PluginFileImpl pfi = (PluginFileImpl) getDirectory();
                        try {
                            IPluginService service = pfi.getPlugin().getService();
                            if (pfi.getPluginAccount() != null) {
                                if (!pfi.getPluginAccount().equals(service.getCurrentAccount())) {
                                    if (service.isConnected())
                                        service.disconnect();
                                    PluginErrorResult result = service.setCurrentAccount(pfi.getPluginAccount());
                                    if (result != null && result.getError() != null)
                                        throw new Exception(result.getError());
                                    result = service.connect();
                                    if (result != null && result.getError() != null)
                                        throw new Exception(result.getError());
                                }
                            }
                            PluginUriResult result = service.openFile(pfi.getWrapper(), false);
                            if (getDirectory() == null || getActivity() == null || !isAdded())
                                return;
                            else if (result.getError() != null) {
                                errorListing(result.getError());
                                return;
                            }
                            mCacheFile = File.fromUri(getActivity(), null, result.getUri(), true);
                            mZipFile = new ArchiveFile(mCacheFile, pfi);
                            performRead(showAnimation);
                        } catch (Exception e2) {
                            e2.printStackTrace();
                            errorListing(e2.getLocalizedMessage());
                        }
                    }
                };
                pfi.verifyConnection(mHandler, true, mConnectionCallback);
                return;
            } else {
                if (getDirectory() instanceof ArchiveSubFile) {
                    mZipFile = ((ArchiveSubFile) getDirectory()).getTopFile();
                } else if (getDirectory() instanceof ArchiveFile) {
                    mZipFile = (ArchiveFile) getDirectory();
                } else {
                    mZipFile = new ArchiveFile(getDirectory(), null);
                }
            }
        }
        // Was not null or was just initialized now
        performRead(showAnimation);
    }

    @WorkerThread
    private void performRead(final boolean showAnimation) {
        if (!mZipFile.isInitialized() && getActivity() != null) {
            Reader reader = null;
            try {
                reader = Cram.with((PluginActivity) getActivity()).reader(mZipFile);
                mZipFile.putFiles(reader.entries());
            } catch (final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.showErrorDialog(getActivity(),
                                Html.fromHtml(getString(R.string.failed_read_file_error,
                                        mZipFile.getPath(), e.getLocalizedMessage())));
                    }
                });
            } finally {
                if (reader != null)
                    reader.cleanup();
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getDirectory() instanceof ArchiveFile)
                    mAdapter.set(((ArchiveFile) getDirectory()).subFiles, showAnimation);
                else
                    mAdapter.set(mZipFile.subFiles, showAnimation);
                successListing();
                restoreScrollPosition();
                invalidateSubtitle(mZipFile.subFiles);
            }
        });
    }

    @Override
    public String toString() {
        if (mDirectory != null)
            return mDirectory.toString();
        return null;
    }
}