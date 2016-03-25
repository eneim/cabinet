package com.afollestad.cabinet.plugins;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to return results in the plugin service.
 *
 * @author Aidan Follestad (afollestad)
 */
public class PluginLsResult implements Parcelable, Serializable {

    private static final long serialVersionUID = 2568771528989642943L;

    private String mError;
    private final List<PluginFile> mResults;

    public PluginLsResult() {
        mResults = new ArrayList<>();
    }

    public PluginLsResult(Parcel in) {
        this();
        mError = in.readString();
        in.readTypedList(mResults, PluginFile.CREATOR);
    }

    public PluginLsResult(String error, List<PluginFile> results) {
        mError = error;
        mResults = results;
    }

    public String getError() {
        return mError;
    }

    public List<PluginFile> getResults() {
        return mResults;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mError);
        dest.writeTypedList(mResults);
    }

    public static final Creator<PluginLsResult> CREATOR = new Creator<PluginLsResult>() {
        @Override
        public PluginLsResult[] newArray(int size) {
            return new PluginLsResult[size];
        }

        @Override
        public PluginLsResult createFromParcel(Parcel source) {
            return new PluginLsResult(source);
        }
    };
}