package com.afollestad.cabinet.plugins;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Used to return results in the plugin service.
 *
 * @author Aidan Follestad (afollestad)
 */
public class PluginErrorResult implements Parcelable, Serializable {

    private static final long serialVersionUID = 2568371528989642943L;

    private final String mError;

    public PluginErrorResult(Parcel in) {
        mError = in.readString();
    }

    public PluginErrorResult(String error) {
        mError = error;
    }

    public String getError() {
        return mError;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mError);
    }

    public static final Creator<PluginErrorResult> CREATOR = new Creator<PluginErrorResult>() {
        @Override
        public PluginErrorResult[] newArray(int size) {
            return new PluginErrorResult[size];
        }

        @Override
        public PluginErrorResult createFromParcel(Parcel source) {
            return new PluginErrorResult(source);
        }
    };
}