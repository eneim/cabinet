package com.afollestad.cabinet.plugins;
import com.afollestad.cabinet.plugins.PluginFile;
import com.afollestad.cabinet.plugins.PluginLsResult;
import com.afollestad.cabinet.plugins.PluginErrorResult;
import com.afollestad.cabinet.plugins.PluginFileResult;
import com.afollestad.cabinet.plugins.PluginUriResult;
import android.net.Uri;

interface IPluginService {
    boolean authenticationNeeded();

    PluginErrorResult connect();

    PluginUriResult openFile(in PluginFile file, boolean watch);

    PluginFileResult upload(in Uri local, in PluginFile dest);

    PluginUriResult download(in PluginFile source, in Uri dest);

    PluginLsResult listFiles(in PluginFile parent);

    PluginFileResult makeFile(String displayName, in PluginFile parent);

    PluginFileResult makeFolder(String displayName, in PluginFile parent);

    PluginFileResult copy(in PluginFile source, in PluginFile dest);

    PluginErrorResult remove(in PluginFile file);

    PluginErrorResult chmod(int permissions, in PluginFile target);

    PluginErrorResult chown(int uid, in PluginFile target);

    boolean exists(String path);

    void disconnect();

    void exit();

    boolean isConnected();

    void openSettings(String accountId, String accountDisplay, String initPath);

    void addAccount(boolean initial);

    String getCurrentAccount();

    PluginErrorResult setCurrentAccount(String id);

    PluginErrorResult removeAccount(String id);
}