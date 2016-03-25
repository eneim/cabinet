package com.afollestad.cabinet.bookmarks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Will be removed soon.
 */
@Deprecated
public class PinsLegacy {

    public static class Item {

        public Item(JSONObject json) {
            isMain = json.optBoolean("main");
            mPath = json.optString("path");
            mOrder = json.optInt("order");
            if (json.optBoolean("remote")) {
                isRemote = true;
                mHost = json.optString("host");
                mPort = json.optInt("port");
                mUser = json.optString("user");
                mPass = json.optString("pass");
                mIdentity = json.optString("identity");
                mIdentityPassphrase = json.optString("identityPassphrase");
            }
            if (!json.isNull("nickname"))
                mNickname = json.optString("nickname", null);
            if (!json.isNull("package")) {
                mPackage = json.optString("package");
                mPluginName = json.optString("plugin_name");
            }
        }

        public final int mOrder;
        private boolean isRemote;
        private boolean isMain;
        private final String mPath;
        private String mHost;
        private int mPort;
        private String mUser;
        private String mPass;
        private String mNickname;
        private String mIdentity;
        private String mIdentityPassphrase;
        private String mPackage;
        private String mPluginName;
        private Drawable mIcon;

        public void setMain(boolean isMain) {
            this.isMain = isMain;
        }

        public void setNickname(String nickname) {
            this.mNickname = nickname;
        }

        public String getNickname() {
            return mNickname;
        }

        public boolean isMain() {
            return isMain;
        }

        public boolean isRemote() {
            return isRemote;
        }

        public String getPath() {
            return mPath;
        }

        public String getHost() {
            return mHost;
        }

        public int getPort() {
            return mPort;
        }

        public String getUser() {
            return mUser;
        }

        public String getPass() {
            return mPass;
        }

        public String getPackage() {
            return mPackage;
        }

        public String getPluginName() {
            return mPluginName;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            try {
                json.put("main", isMain);
                json.put("path", mPath);
                json.put("remote", isRemote);
                json.put("order", mOrder);
                if (isRemote) {
                    json.put("host", mHost);
                    json.put("port", mPort);
                    json.put("user", mUser);
                    json.put("pass", mPass);
                    json.put("identity", mIdentity);
                    json.put("identityPassphrase", mIdentityPassphrase);
                }
                if (mNickname != null)
                    json.put("nickname", mNickname);
                if (mPackage != null)
                    json.put("package", mPackage);
                if (mPluginName != null)
                    json.put("plugin_name", mPluginName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return json;
        }

        @Override
        public String toString() {
            JSONObject jsonObject = toJSON();
            jsonObject.remove("main");
            return jsonObject.toString();
        }

        public File toFile() {
            if (isRemote) {
                return null;
            } else {
                if (mPath.startsWith("content://")) {
                    return null;
                } else if (mPackage != null) {
                    return null;
                }
                return new LocalFile(Uri.parse("file://" + getPath()));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Item)) return false;
            final Item op = (Item) o;
            boolean equal = op.getPath().equals(getPath()) && op.getPort() == getPort();
            if (getHost() != null)
                equal = equal && getHost().equals(op.getHost());
            if (getUser() != null)
                equal = equal && getUser().equals(op.getUser());
            if (getPass() != null)
                equal = equal && getPass().equals(op.getPass());
            if (getPackage() != null)
                equal = equal && getPackage().equals(op.getPackage());
            return equal;
        }
    }

    @SuppressLint("CommitPrefEdits")
    public static void clear(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove("pins").commit();
    }

    public static List<Item> getAll(Context context) {
        List<Item> items = new ArrayList<>();
        if (context == null) return items;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String shortcuts = prefs.getString("pins", null);
        if (shortcuts == null) return items;
        try {
            JSONArray shortcutsJson = new JSONArray(shortcuts);
            for (int i = 0; i < shortcutsJson.length(); i++) {
                items.add(new Item(shortcutsJson.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }
}