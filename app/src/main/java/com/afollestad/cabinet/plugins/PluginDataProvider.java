package com.afollestad.cabinet.plugins;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;

import com.afollestad.cabinet.BuildConfig;
import com.afollestad.cabinet.sql.ProviderBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class PluginDataProvider extends ProviderBase {

    public final static Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".plugin_data_store");

    public PluginDataProvider() {
        super("plugin_data_store",
                "package TEXT NOT NULL, account_id TEXT, account_display TEXT, initial_path TEXT, current INTEGER");
    }

    public static Uri getUri(Context context, String pkg, boolean current) {
        Cursor cursor = context.getContentResolver().query(URI,
                null, "package = ?" + (current ? " AND current = 1" : ""), new String[]{pkg}, null);
        Uri uri = null;
        if (cursor != null) {
            final int PACKAGE_INDEX = cursor.getColumnIndex("package");
            final int ACCOUNT_ID_INDEX = cursor.getColumnIndex("account_id");
            final int INITIAL_PATH_INDEX = cursor.getColumnIndex("initial_path");

            if (cursor.moveToFirst()) {
                String str = "plugin://" + cursor.getString(PACKAGE_INDEX);
                if (!cursor.isNull(ACCOUNT_ID_INDEX))
                    str += ("/" + cursor.getString(ACCOUNT_ID_INDEX));
                String initPath = cursor.getString(INITIAL_PATH_INDEX);
                if (!initPath.startsWith("/"))
                    initPath += ("/" + initPath);
                str += initPath;
                uri = Uri.parse(str);
            }
            cursor.close();
        }
        return uri;
    }

    public static void addAccount(Context context, String pkg, String accountId, String accountDisplay, String initPath, boolean current) {
        ContentResolver cr = context.getContentResolver();
        if (current) {
            // Clear any marked as current for this plugin
            ContentValues values = new ContentValues();
            values.put("current", 0);
            cr.update(URI, values, "package = ? AND current = 1", new String[]{pkg});
        }

        ContentValues values = new ContentValues(5);
        values.put("package", pkg);
        values.put("account_id", accountId);
        values.put("account_display", accountDisplay);
        values.put("initial_path", initPath);
        values.put("current", current ? 1 : 0);
        cr.insert(URI, values);
    }

    public static void updateAccount(Context context, String pkg, String accountId, String accountDisplay, String initPath) {
        ContentValues values = new ContentValues(2);
        values.put("account_display", accountDisplay);
        values.put("initial_path", initPath);
        context.getContentResolver().update(URI, values,
                "package = ? AND account_id = ?", new String[]{pkg, accountId});
    }

    public static String removeAccount(Context context, String pkg, String accountId) {
        ContentResolver cr = context.getContentResolver();
        String newAccountId = null;
        String newInitPath = null;

        // Find the account that will be active after removal, if any
        Cursor cursor = cr.query(URI,
                new String[]{"package", "account_id", "initial_path"},
                "package = ?", new String[]{pkg}, null);
        if (cursor != null) {
            String previousId = null;
            String previousInitPath = null;
            boolean foundRemoved = false;
            while (cursor.moveToNext()) {
                final String currentId = cursor.getString(cursor.getColumnIndex("account_id"));
                final String currentInitPath = cursor.getString(cursor.getColumnIndex("initial_path"));
                if (currentId.equals(accountId)) {
                    foundRemoved = true;
                } else if (foundRemoved) {
                    newAccountId = currentId;
                    newInitPath = currentInitPath;
                } else {
                    previousId = currentId;
                    previousInitPath = currentInitPath;
                }
            }
            if (newAccountId == null) {
                newAccountId = previousId;
                newInitPath = previousInitPath;
            }
            cursor.close();
        }

        // Remove the account
        cr.delete(URI, "package = ? AND account_id = ?", new String[]{pkg, accountId});

        // Mark the new current account
        if (newAccountId != null) {
            ContentValues values = new ContentValues(1);
            values.put("current", 1);
            cr.update(URI, values, "package = ? AND account_id = ?", new String[]{pkg, newAccountId});
        }

        // Build the new URI
        String newUri = "plugin://" + pkg;
        if (newAccountId != null)
            newUri += ("/" + newAccountId);
        if (newInitPath != null) {
            if (!newInitPath.startsWith("/"))
                newUri += "/";
            newUri += newInitPath;
        } else newUri += "/";
        return newUri;
    }

    public static String[][] getAccounts(Context context, String pkg) {
        List<String[]> results = new ArrayList<>();
        Cursor cursor = context.getContentResolver().query(URI,
                new String[]{"account_id", "account_display"},
                "package = ?", new String[]{pkg}, null);
        if (cursor != null) {
            while (cursor.moveToNext())
                results.add(new String[]{cursor.getString(0), cursor.getString(1)});
            cursor.close();
        }
        return results.toArray(new String[results.size()][2]);
    }

    public static String getAccountDisplay(Context context, String pkg, String accountId) {
        Cursor cursor = context.getContentResolver().query(URI,
                new String[]{"account_display"},
                "package = ? AND account_id = ?", new String[]{pkg, accountId}, null);
        String display = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                display = cursor.getString(0);
                if (display == null || display.trim().isEmpty())
                    display = accountId;
            }
            cursor.close();
        }
        return display;
    }

    public static String[] getAccount(Context context, String pkg, String accountId) {
        Cursor cursor = context.getContentResolver().query(URI,
                new String[]{"account_display", "initial_path"},
                "package = ? AND account_id = ?", new String[]{pkg, accountId}, null);
        String[] current = null;
        if (cursor != null) {
            if (cursor.moveToFirst())
                current = new String[]{cursor.getString(0), cursor.getString(1)};
            cursor.close();
        }
        return current;
    }

    public static String[] getCurrentAccount(Context context, String pkg) {
        Cursor cursor = context.getContentResolver().query(URI,
                new String[]{"account_id", "account_display"},
                "package = ? AND current = 1", new String[]{pkg}, null);
        String[] current = null;
        if (cursor != null) {
            if (cursor.moveToFirst())
                current = new String[]{cursor.getString(0), cursor.getString(1)};
            cursor.close();
        }
        return current;
    }

    public static void setCurrent(Context context, String pkg, String accountId) {
        ContentResolver cr = context.getContentResolver();

        // Clear any marked as current for this plugin
        ContentValues values = new ContentValues();
        values.put("current", 0);
        cr.update(URI, values, "package = ? AND current = 1", new String[]{pkg});

        // Mark new as current
        values = new ContentValues();
        values.put("current", 1);
        int affected = cr.update(URI, values, "package = ? AND account_id = ?", new String[]{pkg, accountId});
        if (affected == 0)
            throw new IllegalArgumentException("Account ID " + accountId + " not found for plugin " + pkg);
    }

    public static void clean(Context context, List<Plugin> plugins) {
        if (plugins == null) return;
        else if (plugins.size() == 0) {
            context.getContentResolver().delete(URI, null, null);
            return;
        }
        final StringBuilder where = new StringBuilder();
        for (int i = 0; i < plugins.size(); i++) {
            Plugin p = plugins.get(i);
            if (i > 0) where.append(" AND ");
            where.append("package != ");
            where.append(DatabaseUtils.sqlEscapeString(p.getPackage()));
        }
        context.getContentResolver().delete(URI, where.toString(), null);
    }
}