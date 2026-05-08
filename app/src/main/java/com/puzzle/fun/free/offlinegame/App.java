package com.puzzle.fun.free.offlinegame;

import android.app.Application;
import android.util.Log;

public class App extends Application {
    private static final String TAG = "AppLifecycle";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: application started");
        // Place app-wide initialization here (analytics, ads SDK, etc.).
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
