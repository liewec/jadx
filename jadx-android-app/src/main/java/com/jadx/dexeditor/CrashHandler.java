package com.jadx.dexeditor;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "DexEditorCrash";
    private static final String CRASH_DIR = "crashes";
    private static final String CRASH_FILE_PREFIX = "crash-";

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    public CrashHandler() {
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.context = null;
    }

    public CrashHandler(Context context) {
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.context = context != null ? context.getApplicationContext() : null;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            writeCrashLog(t, e);
        } catch (Throwable ignored) {
            Log.e(TAG, "Failed to write crash log", e);
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        }
    }

    private void writeCrashLog(Thread t, Throwable e) {
        File dir = getCrashDir();
        if (dir == null) {
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File file = new File(dir, CRASH_FILE_PREFIX + timestamp + ".txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Time: " + new Date());
            pw.println("Thread: " + t);
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            pw.println();
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            pw.println(sw.toString());
        } catch (IOException ignored) {
        }
    }

    private File getCrashDir() {
        if (context == null) {
            return null;
        }
        File extCache = context.getExternalCacheDir();
        if (extCache == null) {
            extCache = context.getCacheDir();
        }
        return new File(extCache, CRASH_DIR);
    }
}
