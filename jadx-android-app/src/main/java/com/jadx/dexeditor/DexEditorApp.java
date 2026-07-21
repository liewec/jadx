package com.jadx.dexeditor;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class DexEditorApp extends Application {

    private static final String PREFS_NAME = "dex_editor_prefs";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_ERROR_TIME = "last_error_time";

    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
    }

    /** 保存最近一次错误堆栈到 SharedPreferences，跨进程持久化 */
    public static void setLastError(String error) {
        if (instance == null) return;
        SharedPreferences sp = instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(KEY_LAST_ERROR, error);
        ed.putLong(KEY_LAST_ERROR_TIME, System.currentTimeMillis());
        ed.apply();
    }

    public static String getLastError() {
        if (instance == null) return null;
        SharedPreferences sp = instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String err = sp.getString(KEY_LAST_ERROR, null);
        long time = sp.getLong(KEY_LAST_ERROR_TIME, 0);
        if (err == null || time == 0) return null;
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        return "时间: " + sdf.format(new java.util.Date(time)) + "\n\n" + err;
    }

    public static void clearLastError() {
        if (instance == null) return;
        SharedPreferences sp = instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_LAST_ERROR).remove(KEY_LAST_ERROR_TIME).apply();
    }
}
