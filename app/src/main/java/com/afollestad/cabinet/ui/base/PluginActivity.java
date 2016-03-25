package com.afollestad.cabinet.ui.base;

import com.afollestad.cabinet.plugins.Plugin;
import com.afollestad.cabinet.plugins.PluginFramework;

import java.util.Iterator;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class PluginActivity extends ThemableActivity {

    private List<Plugin> mPlugins;

    public final List<Plugin> getPlugins() {
        return getPlugins(false);
    }

    public final List<Plugin> getPlugins(boolean checkNew) {
        if (mPlugins == null) {
            final PluginFramework pf = new PluginFramework(this);
            mPlugins = pf.query();
        } else if (checkNew) {
            final PluginFramework pf = new PluginFramework(this);
            final List<Plugin> possibleNew = pf.query();
            for (Plugin pnew : possibleNew) {
                boolean found = false;
                for (Plugin pold : mPlugins) {
                    if (pold.getPackage().equals(pnew.getPackage())) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    mPlugins.add(pnew);
            }
        }
        return mPlugins;
    }

    public void removePlugin(String pkg) {
        if (mPlugins == null) return;
        for (Iterator<Plugin> iterator = mPlugins.iterator(); iterator.hasNext(); ) {
            Plugin p = iterator.next();
            if (p.getPackage().equals(pkg))
                iterator.remove();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPlugins != null) {
            for (Plugin p : mPlugins)
                p.release();
            mPlugins = null;
        }
    }
}