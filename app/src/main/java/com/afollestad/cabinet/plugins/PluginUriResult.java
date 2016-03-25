package com.afollestad.cabinet.plugins;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Used to return results in the plugin service.
 *
 * @author Aidan Follestad (afollestad)
 */
public class PluginUriResult implements Parcelable, Serializable {

    private static final long serialVersionUID = 2568771528989642943L;

    private final String mError;
    private final Uri mUri;

    public PluginUriResult(Parcel in) {
        mError = in.readString();
        mUri = in.readParcelable(Uri.class.getClassLoader());
    }

    public PluginUriResult(String error, Uri uri) {
        mError = error;
        mUri = uri;
    }

    public String getError() {
        return mError;
    }

    public Uri getUri() {
        return mUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mError);
        dest.writeParcelable(mUri, flags);
    }

    public static final Creator<PluginUriResult> CREATOR = new Creator<PluginUriResult>() {
        @Override
        public PluginUriResult[] newArray(int size) {
            return new PluginUriResult[size];
        }

        @Override
        public PluginUriResult createFromParcel(Parcel source) {
            return new PluginUriResult(source);
        }
    };
}