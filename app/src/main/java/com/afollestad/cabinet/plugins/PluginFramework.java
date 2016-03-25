package com.afollestad.cabinet.plugins;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.afollestad.cabinet.plugins.base.PluginConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class PluginFramework {

    protected final Context mContext;
    protected final PackageManager mPackageManager;

    public PluginFramework(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
    }

    public List<Plugin> query() {
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> results = pm.queryIntentServices(new Intent(PluginConstants.QUERY_ACTION),
                PackageManager.GET_META_DATA | PackageManager.GET_SERVICES);
        List<Plugin> plugins = new ArrayList<>();
        for (ResolveInfo res : results)
            plugins.add(new Plugin(this, res));
        return plugins;
    }
}