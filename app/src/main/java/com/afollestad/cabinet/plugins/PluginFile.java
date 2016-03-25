package com.afollestad.cabinet.plugins;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;

/**
 * Used to store file objects that are sent from the plugin service to the main app.
 *
 * @author Aidan Follestad (afollestad)
 */
public class PluginFile implements Parcelable, Serializable {

    private static final long serialVersionUID = 5568771528989642943L;

    private final String mPackage;
    private final String mPath;
    private final String mThumbnail;
    private final long mCreated;
    private final long mModified;
    private final boolean isDir;
    private final long mLength;
    private final boolean isHidden;
    private final String mPermissions;
    private PluginFile mParent;

    public PluginFile(Parcel in) {
        mPackage = in.readString();
        mPath = in.readString();
        mThumbnail = in.readString();
        mCreated = in.readLong();
        mModified = in.readLong();
        isDir = in.readInt() == 1;
        mLength = in.readLong();
        isHidden = in.readInt() == 1;
        mPermissions = in.readString();
        mParent = in.readParcelable(PluginFile.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackage);
        dest.writeString(mPath);
        dest.writeString(mThumbnail);
        dest.writeLong(mCreated);
        dest.writeLong(mModified);
        dest.writeInt(isDir ? 1 : 0);
        dest.writeLong(mLength);
        dest.writeInt(isHidden ? 1 : 0);
        dest.writeString(mPermissions);
        dest.writeParcelable(mParent, flags);
    }

    public PluginFile(JSONObject json) {
        mPackage = json.optString("package");
        mPath = json.optString("path");
        mThumbnail = json.optString("thumbnail");
        mCreated = json.optLong("created");
        mModified = json.optLong("modified");
        isDir = json.optBoolean("is_dir");
        mLength = json.optLong("length");
        isHidden = json.optBoolean("is_hidden");
        if (json.has("parent"))
            mParent = new PluginFile(json.optJSONObject("parent"));
        mPermissions = json.optString("permissions");
    }

    private PluginFile(Builder builder) {
        mPackage = builder.packageName;
        mPath = builder.path;
        mThumbnail = builder.thumbnail;
        mCreated = builder.created;
        mModified = builder.modified;
        isDir = builder.isDir;
        mLength = builder.length;
        isHidden = builder.hidden;
        mParent = builder.parent;
        mPermissions = builder.permissions;
    }

    public String getPackage() {
        return mPackage;
    }

    public String getPath() {
        return mPath;
    }

    public String getThumbnail() {
        return mThumbnail;
    }

    public long getCreated() {
        return mCreated;
    }

    public long getModified() {
        return mModified;
    }

    public boolean isDir() {
        return isDir;
    }

    public long getLength() {
        return mLength;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public PluginFile getParent() {
        if (mParent == null && !mPath.equals("/")) {
            Builder parentBuilder = new Builder(null, getPackage())
                    .isDir(true)
                    .hidden(false);
            String tempPath = mPath;
            if (tempPath.endsWith(File.separator))
                tempPath = tempPath.substring(0, tempPath.length() - 1);
            tempPath = tempPath.substring(0, tempPath.lastIndexOf(File.separatorChar));
            mParent = parentBuilder.path(tempPath).build();
        }
        return mParent;
    }

    public String getPermissions() {
        return mPermissions;
    }

    public static class Builder {

        protected final String packageName;
        protected final PluginFile parent;

        protected String path;
        protected String thumbnail;
        protected long created;
        protected long modified;
        protected boolean isDir;
        protected long length;
        protected boolean hidden;
        protected String permissions;

        public Builder(PluginFile parent, String packageName) {
            this.parent = parent;
            this.packageName = packageName;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder thumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
            return this;
        }

        public Builder created(long created) {
            this.created = created;
            return this;
        }

        public Builder modified(long modified) {
            this.modified = modified;
            return this;
        }

        public Builder isDir(boolean isDir) {
            this.isDir = isDir;
            return this;
        }

        public Builder length(long length) {
            this.length = length;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder permissions(String permissions) {
            this.permissions = permissions;
            return this;
        }

        public PluginFile build() {
            if (this.isDir)
                this.length = -1;
            if (this.path == null || this.path.trim().isEmpty())
                this.path = "/";
            return new PluginFile(this);
        }

        @Override
        public String toString() {
            return "PluginFile#Builder: " + packageName;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("package", mPackage);
            json.put("path", mPath);
            json.put("thumbnail", mThumbnail);
            json.put("created", mCreated);
            json.put("modified", mModified);
            json.put("is_dir", isDir);
            json.put("length", mLength);
            json.put("is_hidden", isHidden);
            if (mParent != null)
                json.put("parent", mParent.toJson());
            json.put("permissions", mPermissions);
        } catch (Exception ignored) {
        }
        return json;
    }

    public static final Creator<PluginFile> CREATOR = new Creator<PluginFile>() {
        @Override
        public PluginFile[] newArray(int size) {
            return new PluginFile[size];
        }

        @Override
        public PluginFile createFromParcel(Parcel source) {
            return new PluginFile(source);
        }
    };

    @Override
    public String toString() {
        return "[" + mPackage + "]: " + mPath;
    }
}