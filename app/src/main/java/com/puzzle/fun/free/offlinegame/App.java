package com.puzzle.fun.free.offlinegame;

import android.app.Application;
import android.util.Log;
import com.bidderdesk.BaseApp;
import com.bidderdesk.SdkManager;
import com.bidderdesk.SpUtil;

public class App extends BaseApp {
    private static final String TAG = "AppLifecycle";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: application started");
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
}
