package com.afollestad.cabinet.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.content.DirectoryFragment;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginFramework;
import com.afollestad.cabinet.plugins.base.PluginConstants;
import com.afollestad.cabinet.ui.AudioInitActivity;
import com.afollestad.cabinet.ui.MainActivity;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.cabinet.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class AudioService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private MediaPlayer mPlayer;
    private AudioManager mAudioManager;
    private RemoteViews mRemoteViews;

    private String mTitle;
    private List<File> mQueue;
    private int mQueuePosition;

    private final static String ACTION_TOGGLE = "com.afollestad.cabinet.services.AudioService.TOGGLE";
    private final static String ACTION_PREVIOUS = "com.afollestad.cabinet.services.AudioService.PREVIOUS";
    private final static String ACTION_CLOSE = "com.afollestad.cabinet.services.AudioService.CLOSE";
    private final static String ACTION_NEXT = "com.afollestad.cabinet.services.AudioService.NEXT";

    private final static int FOREGROUND_ID = 9001;
    private final static int FOLDER_ID = 9002;
    private final static int TOGGLE_ID = 9003;
    private final static int PREVIOUS_ID = 9004;
    private final static int NEXT_ID = 9005;
    private final static int CLOSE_ID = 9006;

    private final BroadcastReceiver ACTION_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TOGGLE.equals(intent.getAction())) {
                if (mPlayer != null) {
                    if (mPlayer.isPlaying()) {
                        // Pause
                        dropFocus(true);
                    } else if (requestFocus()) {
                        // Play
                        refreshNotification(true);
                        start();
                    }
                }
            } else if (ACTION_PREVIOUS.equals(intent.getAction())) {
                if (mPlayer.getCurrentPosition() <= 3000) {
                    if (mQueue.size() > 1 && mQueuePosition > 0) {
                        // Play previous file in queue
                        mQueuePosition--;
                        playCurrentPosition();
                    } else if (mPlayer.isPlaying()) {
                        // Can't go back, stop audio
                        dropFocus(true);
                    }
                } else {
                    // More than 3 seconds in, go to beginning, another quick tap will go to previous
                    final boolean play = !mPlayer.isPlaying();
                    mPlayer.seekTo(0);
                    if (play)
                        start();
                }
            } else if (ACTION_NEXT.equals(intent.getAction())) {
                tryPlayNext();
            } else if (ACTION_CLOSE.equals(intent.getAction())) {
                // Stop audio, remove service from foreground, and kill it
                dropFocus(false);
                if (mPlayer != null)
                    mPlayer.release();
                mPlayer = null;
                try {
                    stopForeground(true);
                    stopSelf();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_CLOSE);
        registerReceiver(ACTION_RECEIVER, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            if (mPlayer.isPlaying())
                mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        mRemoteViews = null;
        mAudioManager = null;

        try {
            unregisterReceiver(ACTION_RECEIVER);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reinitPlayer(Uri source) {
        try {
            if (mPlayer != null) {
                mPlayer.reset();
                mPlayer.release();
            }
            mPlayer = new MediaPlayer();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(this, source);
            mPlayer.prepare();
        } catch (Throwable t) {
            Toast.makeText(this, t.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            try {
                stopForeground(true);
            } catch (Throwable t2) {
                t2.printStackTrace();
            }
            stopSelf();
        }
    }

    private void playCurrentPosition() {
        if (mPlayer != null) {
            // Cleanup and reinitialize
            if (mPlayer.isPlaying())
                mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        File current = mQueue.get(mQueuePosition);
        reinitPlayer(current.getUri());
        mTitle = current.getName();
        refreshNotification(true);
        start();
    }

    private void tryPlayNext() {
        if (mQueue != null && mQueue.size() > 1 && (mQueuePosition + 1) < mQueue.size()) {
            mQueuePosition++;
            if (mPlayer == null)
                reinitPlayer(mQueue.get(mQueuePosition).getUri());
            // Play next file in queue
            playCurrentPosition();
        } else if (mPlayer != null && mPlayer.isPlaying()) {
            // Reached end of queue, stop audio
            dropFocus(true);
        } else {
            refreshNotification(false);
        }
    }

    private boolean requestFocus() {
        final int result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void dropFocus(boolean refresh) {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
            if (refresh)
                refreshNotification(false);
        }
        if (mAudioManager != null)
            mAudioManager.abandonAudioFocus(this);
        if (!refresh && mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void refreshNotification(boolean playing) {
        final PendingIntent toggleIntent = PendingIntent.getBroadcast(this, TOGGLE_ID,
                new Intent(ACTION_TOGGLE), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent prevIntent = PendingIntent.getBroadcast(this, PREVIOUS_ID,
                new Intent(ACTION_PREVIOUS), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent nextIntent = PendingIntent.getBroadcast(this, NEXT_ID,
                new Intent(ACTION_NEXT), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent closeIntent = PendingIntent.getBroadcast(this, CLOSE_ID,
                new Intent(ACTION_CLOSE), PendingIntent.FLAG_CANCEL_CURRENT);
        String clickUri = Uri.fromFile(Environment.getExternalStorageDirectory()).toString();
        if (mQueue != null && mQueue.size() > 0 && mQueuePosition >= 0 && mQueuePosition < mQueue.size())
            clickUri = mQueue.get(mQueuePosition).getParent().getUri().toString();
        final PendingIntent folderIntent = PendingIntent.getActivity(this, FOLDER_ID,
                new Intent(this, MainActivity.class).putExtra(PluginConstants.SHORTCUT_PATH, clickUri),
                PendingIntent.FLAG_CANCEL_CURRENT);

        mRemoteViews = new RemoteViews(getPackageName(), R.layout.notification_audio);
        mRemoteViews.setTextViewText(R.id.title, mTitle);
        mRemoteViews.setImageViewResource(R.id.play, playing ?
                R.drawable.ic_audio_pause : R.drawable.ic_audio_play);
        mRemoteViews.setOnClickPendingIntent(R.id.play, toggleIntent);
        mRemoteViews.setOnClickPendingIntent(R.id.restart, prevIntent);
        mRemoteViews.setOnClickPendingIntent(R.id.forward, nextIntent);
        mRemoteViews.setOnClickPendingIntent(R.id.close, closeIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .setColor(ThemeUtils.primaryColor(this))
                .setContent(mRemoteViews)
                .setContentIntent(folderIntent);
        if (playing) {
            startForeground(FOREGROUND_ID, builder.build());
        } else {
            stopForeground(false);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(FOREGROUND_ID, builder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(AudioInitActivity.EXTRA_URI))
            return START_NOT_STICKY;
        final Uri uri = intent.getParcelableExtra(AudioInitActivity.EXTRA_URI);
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("content")) {
            Toast.makeText(this, "You cannot play audio from external storage yet. Stay tuned.", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        final PluginFramework pf = new PluginFramework(this);
        final List<Plugin> plugins = pf.query();

        final File individual = File.fromUri(this, plugins, uri, true);
        final File tree = File.fromUri(this, plugins, uri, false);

        if (individual == null || tree == null) {
            Toast.makeText(this, "Invalid file URI: " + uri.toString(), Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }

        mQueue = new ArrayList<>();
        if (tree.getParent() != null) {
            try {
                // Build a queue based on every audio file in the folder the initial file is contained in
                final List<File> parentContents = tree.getParent().listFiles();
                mQueuePosition = 0;
                if (parentContents != null) {
                    Collections.sort(parentContents, DirectoryFragment.getComparator(this, Utils.getSorter(this)));
                    if (parentContents.size() > 0) {
                        int traverseIndex = 0;
                        for (final File brother : parentContents) {
                            // getMimeType of File requires context to be an Activity, so we use the static method instead
                            final String mime = File.getMimeType(brother.getExtension());
                            if (mime == null || !mime.startsWith("audio"))
                                continue;
                            if (brother.equals(individual))
                                mQueuePosition = traverseIndex;
                            mQueue.add(brother);
                            traverseIndex++;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                return START_NOT_STICKY;
            }
        }
        if (mQueue.size() == 0)
            mQueue.add(individual);

        playCurrentPosition();
        return START_STICKY;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (mPlayer == null) {
            if (mQueue.size() > 0 && mQueuePosition > 0 && mQueuePosition < mQueue.size()) {
                reinitPlayer(mQueue.get(mQueuePosition).getUri());
            } else {
                return;
            }
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            mPlayer.pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            mPlayer.setVolume(1.0f, 1.0f);
            start();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            mPlayer.stop();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            mPlayer.setVolume(0.2f, 0.2f);
        }
    }

    private void start() {
        mPlayer.start();
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                tryPlayNext();
            }
        });
    }
}