package com.afollestad.cabinet.ui;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.cabinet.App;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.DetailsDialog;
import com.afollestad.cabinet.plugins.PluginFramework;
import com.afollestad.cabinet.ui.base.ThemableActivity;
import com.afollestad.cabinet.utils.BackgroundThread;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.leakcanary.RefWatcher;
import com.stericson.RootShell.exceptions.RootDeniedException;

import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TextEditorActivity extends ThemableActivity {

    private java.io.File mTempFile;
    private EditText mInput;
    private Uri mUri;
    private int mFindStart = 0;
    private EditText mFindText;
    private EditText mReplaceText;
    private boolean mReadOnly;
    private int mInitialPerms;
    private int mOriginalSize;
    private Toast mToast;
    private Button mReplaceButton;
    private Button mReplaceAllButton;
    private MenuItem mFindAndReplace;

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null)
                    mToast.cancel();
                mToast = Toast.makeText(TextEditorActivity.this, message, Toast.LENGTH_SHORT);
                mToast.show();
            }
        });
    }

    private String trim(String text) {
        if (text.length() == 0) return text;
        else if (text.charAt(0) != '\n' && text.charAt(0) != ' ' &&
                text.charAt(text.length() - 1) != '\n' &&
                text.charAt(text.length() - 1) != ' ') {
            return text;
        }
        final StringBuilder b = new StringBuilder(text);
        while (true) {
            if (b.length() == 0) break;
            else if (b.charAt(0) == '\n' || b.charAt(0) == ' ') {
                b.deleteCharAt(0);
            } else {
                break;
            }
        }
        while (true) {
            if (b.length() == 0) break;
            else if (b.charAt(b.length() - 1) == '\n' || b.charAt(b.length() - 1) == ' ') {
                b.deleteCharAt(b.length() - 1);
            } else {
                break;
            }
        }
        try {
            return b.toString();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            Utils.showErrorDialog(this, e.getLocalizedMessage());
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_texteditor);

        Toolbar mToolbar = (Toolbar) findViewById(R.id.appbar_toolbar);
        findViewById(R.id.toolbar_directory).setBackgroundColor(getThemeUtils().primaryColor());

        mInput = (EditText) findViewById(R.id.input);
        mFindText = (EditText) findViewById(R.id.find);
        mReplaceText = (EditText) findViewById(R.id.replace);

        setSupportActionBar(mToolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView find = (TextView) findViewById(R.id.btnFind);
        find.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performFind(true);
            }
        });

        mReplaceButton = (Button) findViewById(R.id.btnReplace);
        mReplaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performReplace();
            }
        });

        mReplaceAllButton = (Button) findViewById(R.id.btnReplaceAll);
        mReplaceAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //noinspection StatementWithEmptyBody
                while (performReplace()) {
                    // Do nothing, it will stop on its own
                }
            }
        });

        if (getIntent().getData() != null && savedInstanceState == null) {
            load(getIntent().getData());
        } else {
            setProgress(false);
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable("uri");
            mOriginalSize = savedInstanceState.getInt("original_size");
            mFindStart = savedInstanceState.getInt("find_start");
            mReadOnly = savedInstanceState.getBoolean("read_only");
            final View findReplaceFrame = findViewById(R.id.findReplaceFrame);
            boolean findReplaceVisibility = savedInstanceState.getBoolean("find_replace_visibility");
            findReplaceFrame.setVisibility(findReplaceVisibility ?
                    View.VISIBLE : View.GONE);
            final int selectionStart = savedInstanceState.getInt("selection_start");
            final int selectionEnd = savedInstanceState.getInt("selection_end");
            if (selectionEnd >= selectionStart && selectionEnd < mInput.getText().toString().length()) {
                mInput.requestFocus();
                mInput.setSelection(selectionStart, selectionEnd);
            }
            if (findReplaceVisibility) {
                mFindText.requestFocus();
            }
            if (mReadOnly) {
                readOnly();
            }
        }

        mInput.post(new Runnable() {
            @Override
            public void run() {
                mInput.requestFocus();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        View findReplaceFrame = findViewById(R.id.findReplaceFrame);
        outState.putParcelable("uri", mUri);
        outState.putInt("find_start", mFindStart);
        outState.putInt("original_size", mOriginalSize);
        outState.putInt("selection_start", mInput.getSelectionStart());
        outState.putInt("selection_end", mInput.getSelectionEnd());
        outState.putBoolean("find_replace_visibility", findReplaceFrame.getVisibility() == View.VISIBLE);
        outState.putBoolean("read_only", mReadOnly);
    }

    private boolean performFind(boolean showErrorIfNone) {
        String findText = trim(mFindText.getText().toString());
        if (findText == null || findText.length() == 0) return false;
        String mainText = trim(mInput.getText().toString());
        if (mainText == null) return false;
        final boolean matchCase = ((CheckBox) findViewById(R.id.match_case)).isChecked();

        if (!matchCase) {
            findText = findText.toLowerCase(Locale.getDefault());
            mainText = mainText.toLowerCase(Locale.getDefault());
        }

        int mFindEnd;

        try {
            final Pattern p = Pattern.compile(findText);
            final Matcher m = p.matcher(mainText);
            if (mFindStart > mainText.length() - 1)
                mFindStart = 0;
            boolean found = m.find(mFindStart);
            if (!found && mFindStart > 0) {
                mFindStart = 0;
                found = m.find(mFindStart);
            }
            if (!found) {
                if (showErrorIfNone) {
                    try {
                        new MaterialDialog.Builder(this)
                                .title(R.string.no_occurences)
                                .content(R.string.no_occurences_desc)
                                .positiveText(android.R.string.ok)
                                .show();
                    } catch (WindowManager.BadTokenException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
            mFindStart = m.start();
            mFindEnd = m.end();
        } catch (PatternSyntaxException e) {
            mFindStart = mainText.indexOf(findText, mFindStart);
            if (mFindStart == -1) {
                mFindStart = mainText.indexOf(findText);
                if (mFindStart == -1) {
                    if (showErrorIfNone) {
                        try {
                            new MaterialDialog.Builder(this)
                                    .title(R.string.no_occurences)
                                    .content(R.string.no_occurences_desc)
                                    .positiveText(android.R.string.ok)
                                    .show();
                        } catch (WindowManager.BadTokenException e2) {
                            e2.printStackTrace();
                        }
                    }
                    return false;
                }
            }
            mFindEnd = mFindStart + mFindText.length();
        }

        mInput.requestFocus();
        mInput.setSelection(mFindStart, mFindEnd);
        mFindStart = mFindEnd + 1;
        return true;
    }

    private boolean performReplace() {
        try {
            int mLastReplaceStart = -1;
            if (mInput.getSelectionStart() >= 0 && mInput.getSelectionEnd() > mInput.getSelectionStart() &&
                    mLastReplaceStart != mInput.getSelectionStart()) {
                final int start = mInput.getSelectionStart();
                final int end = mInput.getSelectionEnd();
                final StringBuilder inputText = new StringBuilder(mInput.getText().toString());
                mInput.setText("");
                inputText.delete(start, end);
                inputText.insert(start, mReplaceText.getText().toString());
                mInput.setText(inputText.toString());
                return performFind(false);
            } else return performFind(true) && performReplace();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            Utils.showErrorDialog(this, e.getLocalizedMessage());
            return false;
        }
    }

    private void setProgress(boolean show) {
        mInput.setVisibility(show ? View.GONE : View.VISIBLE);
        findViewById(R.id.progress).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void load(final Uri uri) {
        mInitialPerms = -1;
        mUri = uri;
        setProgress(true);
        Log.v("TextEditorActivity", "Loading...");
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                InputStream is = null;
                BufferedReader reader = null;
                final StringBuilder sb = new StringBuilder();
                try {
                    if (uri.getScheme().equals("file")) {
                        final java.io.File mFile = new java.io.File(uri.getPath());
                        LocalFile lf = new LocalFile(mFile);

                        if (lf.writeUnavailableForOther()) {
                            try {
                                mInitialPerms = lf.prepRootWrite(TextEditorActivity.this);
                            } catch (final RootDeniedException e) {
                                e.printStackTrace();
                                mReadOnly = true;
                                Snackbar.make(
                                        findViewById(android.R.id.content),
                                        getString(R.string.text_editor_read_only),
                                        Snackbar.LENGTH_LONG).show();
                                if (lf.readUnavailableForOther()) {
                                    fatalErrorDismissDialog("File not readable");
                                    return;
                                }
                            } catch (final Exception e) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Utils.showErrorDialog(TextEditorActivity.this, e.getLocalizedMessage());
                                    }
                                });
                            }
                        }

                        String ext = File.getExtension(mFile.getName());
                        String mime;
                        if (ext.trim().isEmpty()) {
                            mime = "text/plain";
                        } else {
                            ext = ext.toLowerCase(Locale.getDefault());
                            mime = File.getMimeType(ext);
                        }
                        Log.v("TextEditorActivity", "Mime: " + mime);
                        List<String> textExts = Arrays.asList(getResources().getStringArray(R.array.other_text_extensions));
                        List<String> codeExts = Arrays.asList(getResources().getStringArray(R.array.code_extensions));
                        if (!mime.startsWith("text/") && !textExts.contains(ext) && !codeExts.contains(ext)) {
                            Log.v("TextEditorActivity", "Unsupported extension");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        new MaterialDialog.Builder(TextEditorActivity.this)
                                                .title(R.string.unsupported_extension)
                                                .content(R.string.unsupported_extension_desc)
                                                .positiveText(android.R.string.ok)
                                                .callback(new MaterialDialog.ButtonCallback() {
                                                    @Override
                                                    public void onPositive(MaterialDialog dialog) {
                                                        finish();
                                                    }
                                                }).show();
                                    } catch (WindowManager.BadTokenException e) {
                                        e.printStackTrace();
                                        finish();
                                    }
                                }
                            });
                            return;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setTitle(mFile.getName());
                            }
                        });
                        is = new FileInputStream(mFile);
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setTitle(new java.io.File(mUri.getPath()).getName());
                            }
                        });
                        is = getContentResolver().openInputStream(mUri);
                    }

                    Log.v("TextEditorActivity", "Reading file...");
                    reader = new BufferedReader(new InputStreamReader(is));
                    String line;
                    int index = 0;

                    while ((line = reader.readLine()) != null) {
                        if (index > 0)
                            sb.append("\n");
                        sb.append(line);
                        index++;
                    }

                    Log.v("TextEditorActivity", "Read " + index + " lines.");
                    Log.v("TextEditorActivity", "Setting text to input.");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mInput.setText(sb.toString());
                            } catch (OutOfMemoryError e) {
                                e.printStackTrace();
                                Utils.showErrorDialog(TextEditorActivity.this, e.getLocalizedMessage());
                            }
                            if (mReadOnly) {
                                readOnly();
                                supportInvalidateOptionsMenu();
                            }
                            Log.v("TextEditorActivity", "Done.");
                            mOriginalSize = mInput.getText().length();
                            setProgress(false);
                            sb.setLength(0);
                        }
                    });
                } catch (final Throwable e) {
                    e.printStackTrace();
                    fatalErrorDismissDialog(e.getLocalizedMessage());
                } finally {
                    IOUtils.closeQuietly(reader);
                    IOUtils.closeQuietly(is);
                }
            }
        });
    }

    private void readOnly() {
        mInput.setFocusable(false);
        mReplaceButton.setVisibility(View.GONE);
        mReplaceAllButton.setVisibility(View.GONE);
        mReplaceText.setVisibility(View.GONE);
    }

    private void fatalErrorDismissDialog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setProgress(false);
                try {
                    new MaterialDialog.Builder(TextEditorActivity.this)
                            .title(R.string.error)
                            .cancelable(false)
                            .content(message)
                            .positiveText(android.R.string.ok)
                            .dismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    finish();
                                }
                            }).show();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    private void save(final boolean exitAfter) {
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                OutputStream os = null;
                LocalFile lf = null;
                java.io.File mFile = null;
                try {

                    if (mUri.getScheme() == null || mUri.getScheme().equals("file")) {
                        lf = new LocalFile(mUri);
                        mFile = lf.toJavaFile();

                        if (lf.writeUnavailableForOther()) {
                            mTempFile = java.io.File.createTempFile(mFile.getName(), ".temp");
                            Log.v("TextEditorActivity", "Writing changes to " + mTempFile.getPath() + "...");
                        }

                        os = new FileOutputStream(mTempFile != null ? mTempFile : mFile);
                    } else if (mUri.getScheme().equals("content")) {
                        os = getContentResolver().openOutputStream(mUri);
                    } else {
                        throw new Exception("Unsupported URI scheme: " + mUri);
                    }

                    os.write(mInput.getText().toString().getBytes("UTF-8"));

                    if (mTempFile != null && mFile != null) {
                        try {
                            Log.v("TextEditorActivity", "Moving temporary file "
                                    + mTempFile.getPath() + " to " + mFile.getPath() + "...");
                            String command = "mv -f \"" + mTempFile.getAbsolutePath()
                                    + "\" \"" + mFile.getAbsolutePath() + "\"";
                            App.runCommand(true, command);
                        } finally {
                            Log.v("TextEditorActivity", "Deleting temp file " + mTempFile.getPath() + "...");
                            //noinspection ResultOfMethodCallIgnored
                            mTempFile.delete();
                            mTempFile = null;
                        }
                    }

                    showToast(getString(R.string.saved_x, new java.io.File(mUri.getPath()).getName()));
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(TextEditorActivity.this, e.getLocalizedMessage());
                        }
                    });
                } finally {
                    IOUtils.closeQuietly(os);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.v("TextEditorActivity", "Save complete!");
                        if (exitAfter) {
                            mOriginalSize = 0;
                            finishRootWrite();
                            finish();
                        } else {
                            mOriginalSize = mInput.getText().toString().length();
                            invalidateOptionsMenu();
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mFindAndReplace != null) {
            toggleFindAndReplace(mFindAndReplace);
            mFindAndReplace = null;
        } else {
            checkUnsavedChanges();
        }
    }

    private void checkUnsavedChanges() {
        if (mOriginalSize != mInput.getText().toString().length()) {
            try {
                new MaterialDialog.Builder(this)
                        .title(R.string.unsaved_changes)
                        .content(R.string.unsaved_changes_desc)
                        .positiveText(R.string.yes)
                        .negativeText(R.string.no)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                save(true);
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                finishRootWrite();
                                finish();
                            }
                        }).show();
            } catch (WindowManager.BadTokenException e) {
                e.printStackTrace();
                save(false);
            }
        } else {
            finishRootWrite();
            finish();
        }
    }

    private void finishRootWrite() {
        if (mInitialPerms != -1) {
            try {
                new LocalFile(mUri).finishRootWrite(mInitialPerms, TextEditorActivity.this);
            } catch (final Exception ignored) {
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.text_editor, menu);
        MenuItem item = menu.findItem(R.id.save);
        if (mReadOnly) {
            item.setEnabled(false);
            item.getIcon().setAlpha(66);
        } else {
            item.setEnabled(true);
            item.getIcon().setAlpha(255);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            checkUnsavedChanges();
            return true;
        } else if (item.getItemId() == R.id.save) {
            save(false);
            return true;
        } else if (item.getItemId() == R.id.details) {
            if (mUri == null)
                return false;
            File file = File.fromUri(this, new PluginFramework(this).query(), mUri, true);
            try {
                if (file instanceof LocalFile) {
                    ((LocalFile) file).initFileInfo();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                DetailsDialog.create(file, true).show(getFragmentManager(), "DETAILS_DIALOG");
            }
            return true;
        } else if (item.getItemId() == R.id.findAndReplace) {
            toggleFindAndReplace(item);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleFindAndReplace(MenuItem item) {
        View frame = findViewById(R.id.findReplaceFrame);
        if (frame.getVisibility() == View.GONE) {
            frame.setVisibility(View.VISIBLE);
            mFindText.requestFocus();
            mFindAndReplace = item;
        } else {
            frame.setVisibility(View.GONE);
            mInput.requestFocus();
            mFindAndReplace = null;
        }
        item.setIcon(mFindAndReplace != null ? R.drawable.ic_cab_goup : R.drawable.ic_search);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefWatcher refWatcher = App.getRefWatcher(this);
        refWatcher.watch(this);
    }
}