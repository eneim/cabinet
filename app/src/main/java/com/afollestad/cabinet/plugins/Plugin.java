package com.afollestad.cabinet.plugins;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

import com.afollestad.cabinet.plugins.base.PluginConstants;
import com.afollestad.cabinet.utils.BackgroundThread;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class Plugin {

    private final PluginFramework mFramework;
    private final ServiceInfo mInfo;
    private final CharSequence mName;
    private final String mPackage;
    private IPluginService mService;
    private ServiceConnection mConnection;
    private Callback mCallback;

    public static abstract class Callback {
        public boolean cancelled;

        public void onConnecting(Plugin plugin) {
            // Optional implementation
        }

        public abstract void onResult(Plugin plugin, Exception e);
    }

    protected Plugin(PluginFramework pf, ResolveInfo info) {
        mFramework = pf;
        mInfo = info.serviceInfo;
        mName = mInfo.loadLabel(mFramework.mPackageManager);
        mPackage = info.serviceInfo.packageName;
    }

    public CharSequence getName() {
        return mName;
    }

    public Drawable getIcon() {
        Drawable icon = mInfo.loadIcon(mFramework.mPackageManager);
        icon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
        return icon;
    }

    public String getPackage() {
        return mPackage;
    }

    public Bundle getMetaData() {
        return mInfo.metaData;
    }

    public Intent getServiceIntent() {
        ComponentName comp = new ComponentName(mInfo.applicationInfo.packageName, mInfo.name);
        return new Intent(PluginConstants.QUERY_ACTION).setComponent(comp);
    }

    public boolean hasSettings(boolean perAccount) {
        Bundle metaData = getMetaData();
        if (metaData == null) return false;
        String hasSettings = metaData.getString("has_settings");
        return hasSettings != null &&
                (hasSettings.equalsIgnoreCase("both") ||
                        ((perAccount && hasSettings.equalsIgnoreCase("account")) ||
                                (!perAccount && hasSettings.equalsIgnoreCase("global"))));
    }

    public boolean hasAccounts() {
        Bundle metaData = getMetaData();
        return metaData != null && metaData.getBoolean("has_accounts", false);
    }

    public void startService(Callback callback) {
        if (isBinded())
            release(); // release any previous connection
        mCallback = callback;
        final Intent serviceIntent = getServiceIntent();
        mFramework.mContext.startService(serviceIntent);
        createConnectionObject();
        mFramework.mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public boolean isBinded() {
        return mService != null && mConnection != null;
    }

    public IPluginService getService() {
        return mService;
    }

    @Override
    public String toString() {
        return mName.toString();
    }

    public void createConnectionObject() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                mService = IPluginService.Stub.asInterface(service);
                if (mCallback != null)
                    mCallback.onResult(Plugin.this, null);
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                mService = null;
                // This method is only invoked when the service quits from the other end or gets killed
                // Invoking exit() from the AIDL interface makes the Service kill itself, thus invoking this.
            }
        };
    }

    public void verifyConnection(final Handler uiHandler, final Callback callback, final boolean bindOnly) {
        boolean shouldBind = false;
        boolean shouldConnect = false;
        try {
            if (!isBinded()) {
                shouldBind = true;
            } else {
                shouldConnect = getService() == null || !getService().isConnected();
            }
        } catch (RemoteException e) {
            shouldBind = true;
        }
        if (shouldBind) {
            startService(new Plugin.Callback() {
                @Override
                public void onResult(Plugin plugin, Exception e) {
                    if (callback.cancelled) return;
                    if (e != null) {
                        callback.onResult(plugin, e);
                        return;
                    }
                    verifyConnection(uiHandler, callback, bindOnly);
                }
            });
        } else if (shouldConnect && !bindOnly) {
            BackgroundThread.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (callback.cancelled) return;
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onConnecting(Plugin.this);
                        }
                    });
                    try {
                        PluginErrorResult result = getService().connect();
                        if (callback.cancelled) return;
                        else if (result.getError() != null)
                            throw new Exception(result.getError());
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(Plugin.this, null);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.onResult(Plugin.this, e);
                    }
                }
            });
        } else {
            callback.onResult(Plugin.this, null);
        }
    }

    public void release() {
        if (isBinded()) {
            if (mService != null) {
                try {
                    // Stop the service if it's running but not connected
                    if (!mService.isConnected())
                        mFramework.mContext.stopService(getServiceIntent());
                } catch (Throwable ignored) {
                }
            }
            try {
                mFramework.mContext.unbindService(mConnection);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            mConnection = null;
        }
        mService = null;
    }
}