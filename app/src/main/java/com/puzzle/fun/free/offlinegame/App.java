package com.puzzle.fun.free.offlinegame;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.bidderdesk.BaseApp;
import com.bidderdesk.SdkManager;
import com.bidderdesk.SpUtil;
import com.tencent.mmkv.MMKV;

public class App extends BaseApp {
    private static final String TAG = "AppLifecycle";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: application started");
        if (!isMainProcess()) return;
        MMKV.initialize(this);
        // Place app-wide initialization here (analytics, ads SDK, etc.).
        SdkManager.Companion.getInstance().initializeSdk(this);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "onLowMemory: system is running low on memory");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.i(TAG, "onTrimMemory: level=" + level);
    }
    private boolean isMainProcess() {
        int pid = android.os.Process.myPid();
        String processName = "";
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == pid) {
                processName = info.processName;
                break;
            }
        }
        return getPackageName().equals(processName);
    }
}
