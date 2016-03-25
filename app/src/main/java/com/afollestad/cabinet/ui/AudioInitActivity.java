package com.afollestad.cabinet.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.afollestad.cabinet.services.AudioService;

/**
 * @author Aidan Follestad (afollestad)
 */
public class AudioInitActivity extends Activity {

    public static final String EXTRA_URI = "audio_uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri != null) {
            startService(new Intent(this, AudioService.class)
                    .setData(getIntent().getData())
                    .putExtra(EXTRA_URI, uri));
        }
        finish();
    }
}
