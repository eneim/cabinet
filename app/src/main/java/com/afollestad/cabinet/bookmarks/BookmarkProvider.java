package com.afollestad.cabinet.bookmarks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.provider.DocumentFile;

import com.afollestad.cabinet.BuildConfig;
import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.sql.ProviderBase;
import com.afollestad.cabinet.ui.base.PluginActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class BookmarkProvider extends ProviderBase {

    public final static Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".nav_drawer_pins");

    public BookmarkProvider() {
        super("navigation_drawer_pins",
                "uri TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, nickname TEXT");
    }

    public static void addItem(Context context, Item item) {
        context.getContentResolver().insert(URI, item.values());
    }

    public static List<Item> getItems(Context context) {
        Cursor cursor = context.getContentResolver().query(URI,
                null, null, null, "name");
        List<Item> results = Item.loadAll(cursor);
        if (cursor != null)
            cursor.close();
        return results;
    }

    public static void update(Context context, Item item) {
        context.getContentResolver().update(URI,
                item.values(), item.getLookupWhere(), null);
    }

    public static boolean contains(Context context, Item item) {
        Cursor cursor = context.getContentResolver().query(URI,
                null, item.getLookupWhere(), null, null);
        boolean found = false;
        if (cursor != null) {
            found = cursor.getCount() > 0;
            cursor.close();
        }
        return found;
    }

    public static boolean removeItem(Context context, Item item) {
        return context.getContentResolver().delete(URI,
                item.getLookupWhere(), null) > 0;
    }

    public static boolean isEmpty(Context context) {
        Cursor cursor = context.getContentResolver().query(URI,
                null, null, null, null);
        boolean empty = true;
        if (cursor != null) {
            empty = cursor.getCount() == 0;
            cursor.close();
        }
        return empty;
    }

    public static void clean(Context context, List<Plugin> plugins) {
        if (plugins == null) return;
        final StringBuilder where = new StringBuilder("uri LIKE 'plugin://%'");
        if (plugins.size() > 0) {
            where.append(" AND (");
            for (int i = 0; i < plugins.size(); i++) {
                Plugin p = plugins.get(i);
                if (i > 0) where.append(" AND ");
                where.append("uri NOT LIKE 'plugin://");
                where.append(p.getPackage());
                where.append("%'");
            }
            where.append(")");
        }
        context.getContentResolver().delete(URI, where.toString(), null);
    }

    public static class Item {

        public final String name;
        public String uri;
        public String nickname;
        public int index = -1;

        public int actionId;
        public boolean isMain;
        private Drawable pluginIconCache;
        private Plugin mPlugin;

        public Item(String name, int actionId) {
            this.name = name;
            this.actionId = actionId;
        }

        public Item(String name, Uri uri) {
            this.name = name;
            this.uri = uri.toString();
        }

        public Item(java.io.File from) {
            name = from.getName();
            uri = "file://" + from.getPath();
        }

        public Item(File fi) {
            name = fi.getName();
            uri = fi.getUri().toString();
        }

        public Item(DocumentFile from, String name) {
            this.name = name;
            uri = from.getUri().toString();
        }

        public Item(Cursor from) {
            name = from.getString(from.getColumnIndex("name"));
            uri = from.getString(from.getColumnIndex("uri"));
            nickname = from.getString(from.getColumnIndex("nickname"));
        }

        public static List<Item> loadAll(Cursor from) {
            if (from == null || from.getCount() == 0)
                return new ArrayList<>();
            List<Item> results = new ArrayList<>();
            while (from.moveToNext())
                results.add(new Item(from));
            return results;
        }

        public Item asMain() {
            isMain = true;
            return this;
        }

        public boolean isDocumentTree() {
            return uri != null && uri.startsWith("content://");
        }

        public boolean isRootDocumentTree(Context context) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isDocumentTree() &&
                    (name.equals("sdcard") || name.equals(context.getString(R.string.external_storage)));
        }

        public boolean isPlugin() {
            return uri != null && uri.startsWith("plugin://");
        }

        public boolean isLocalRoot() {
            return uri != null && uri.equalsIgnoreCase("file:///");
        }

        public String getPluginPackage() {
            if (!isPlugin()) return null;
            return Uri.parse(uri).getAuthority();
        }

        private void addToQuery(StringBuilder query, String name, Object val) {
            if (query.length() > 0)
                query.append(" AND ");
            query.append(name);
            if (val == null) {
                query.append(" IS NULL");
            } else {
                query.append(" = ");
                if (val instanceof Integer)
                    query.append(val);
                else if (val instanceof Boolean)
                    query.append(((Boolean) val) ? 1 : 0);
                else
                    query.append(DatabaseUtils.sqlEscapeString((String) val));
            }
        }

        public String getLookupWhere() {
            StringBuilder where = new StringBuilder();
            addToQuery(where, "name", name);
            addToQuery(where, "uri", uri);
            return where.toString();
        }

        public ContentValues values() {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("uri", uri);
            values.put("nickname", nickname);
            return values;
        }

        public CharSequence getPluginName(PluginActivity context) {
            Plugin p = getPlugin(context);
            return p != null ? p.getName() : null;
        }

        public Drawable getPluginIcon(PluginActivity context) {
            if (pluginIconCache != null)
                return pluginIconCache;
            else {
                Plugin p = getPlugin(context);
                if (p != null)
                    pluginIconCache = p.getIcon();
            }
            return pluginIconCache;
        }

        public boolean pluginHasSettings(PluginActivity context, boolean perAccount) {
            return getPlugin(context).hasSettings(perAccount);
        }

        public boolean pluginHasAccounts(PluginActivity context) {
            return getPlugin(context).hasAccounts();
        }

        public void verifyPluginConnection(PluginActivity context, Handler handler, boolean bindOnly, Plugin.Callback callback) {
            Plugin p = getPlugin(context);
            if (p != null)
                p.verifyConnection(handler, callback, bindOnly);
        }

        public Plugin getPlugin(PluginActivity context) {
            return getPlugin(context, false);
        }

        public Plugin getPlugin(PluginActivity context, boolean cacheOnly) {
            if (mPlugin != null || cacheOnly)
                return mPlugin;
            final String pkg = Uri.parse(uri).getAuthority();
            final List<Plugin> plugins = context.getPlugins();
            for (Plugin p : plugins) {
                if (p.getPackage().equals(pkg)) {
                    mPlugin = p;
                    break;
                }
            }
            return mPlugin;
        }

        public File asFile(PluginActivity context) {
            if (uri == null)
                return null;
            Uri uriObj = Uri.parse(uri);
            return File.fromUri(context, uriObj, !isRootDocumentTree(context));
        }

        public String getDisplay(PluginActivity context, boolean allowNickname) {
            if (isPlugin()) {
                if (nickname != null && !nickname.isEmpty())
                    return nickname;
                else return getPluginName(context).toString();
            } else if (allowNickname && nickname != null && !nickname.isEmpty()) {
                return nickname;
            } else if (isRootDocumentTree(context)) {
                return name;
            } else {
                return asFile(context).getDisplay(context);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Item)) return false;
            Item oi = (Item) o;
            return name.equals(oi.name) && uri.equals(oi.uri);
        }

        @Override
        public String toString() {
            return "[Pin]: " + name + " (" + uri + ")";
        }
    }
}