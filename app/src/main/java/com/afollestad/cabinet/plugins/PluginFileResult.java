package com.afollestad.cabinet.plugins;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Used to return results in the plugin service.
 *
 * @author Aidan Follestad (afollestad)
 */
public class PluginFileResult implements Parcelable, Serializable {

    private static final long serialVersionUID = 2568771528989642943L;

    private final String mError;
    private final PluginFile mFile;

    public PluginFileResult(Parcel in) {
        mError = in.readString();
        mFile = in.readParcelable(PluginFile.class.getClassLoader());
    }

    public PluginFileResult(String error, PluginFile file) {
        mError = error;
        mFile = file;
    }

    public String getError() {
        return mError;
    }

    public PluginFile getFile() {
        return mFile;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mError);
        dest.writeParcelable(mFile, flags);
    }

    public static final Creator<PluginFileResult> CREATOR = new Creator<PluginFileResult>() {
        @Override
        public PluginFileResult[] newArray(int size) {
            return new PluginFileResult[size];
        }

        @Override
        public PluginFileResult createFromParcel(Parcel source) {
            return new PluginFileResult(source);
        }
    };
}