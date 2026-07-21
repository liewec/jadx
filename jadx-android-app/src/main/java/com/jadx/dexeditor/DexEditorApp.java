package com.jadx.dexeditor;

import android.app.Application;

public class DexEditorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
    }
}
