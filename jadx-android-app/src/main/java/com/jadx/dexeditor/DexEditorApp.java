package com.jadx.dexeditor;

import android.app.Application;

public class DexEditorApp extends Application {

    private static String lastError = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
    }

    /** 保存最近一次错误堆栈，供 Info Tab 查看 */
    public static void setLastError(String error) {
        lastError = error;
    }

    public static String getLastError() {
        return lastError;
    }

    public static void clearLastError() {
        lastError = null;
    }
}
