package com.jadx.dexeditor;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DexEditorApp extends Application {

    private static final String PREFS_NAME = "dex_editor_prefs";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_ERROR_TIME = "last_error_time";
    private static final String ERROR_LOG_DIR = "error-logs";

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

    /**
     * 把错误日志保存到外部存储文件（root 设备可直接访问）。
     * 路径：/sdcard/Android/data/com.jadx.dexeditor/files/error-logs/error-YYYYMMDD-HHmmss.txt
     * 同时返回文件绝对路径（成功）或 null（失败）。
     */
    public static File saveErrorLogToFile(String content) {
        if (instance == null || content == null) return null;
        File baseDir = instance.getExternalFilesDir(null);
        if (baseDir == null) {
            // 回退到内部缓存（root 仍可访问，但用户更难找）
            baseDir = instance.getCacheDir();
        }
        File logDir = new File(baseDir, ERROR_LOG_DIR);
        if (!logDir.exists() && !logDir.mkdirs()) {
            return null;
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File outFile = new File(logDir, "error-" + timestamp + ".txt");
        try (FileWriter fw = new FileWriter(outFile)) {
            fw.write(content);
            fw.flush();
            return outFile;
        } catch (IOException e) {
            return null;
        }
    }
}
