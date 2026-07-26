package com.jadx.dexeditor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;

/**
 * 全局路径配置（参考 blackdex 的目录策略，默认放在 Download/dex52pj）。
 * <p>
 * 用户可在设置页自定义：
 * - cacheDir：处理 APK / DEX 时的临时缓存目录
 * - unpackDir：脱壳文件输出目录
 * - outputDir：成品 APK / DEX 输出目录
 * - keystoreDir：内置签名密钥存放目录
 * <p>
 * 默认值：
 * - 公共目录 Download/dex52pj/{cache,unpacked,output,keystore}
 * - 若公共目录不可写（Android 10+ scoped storage），回退到应用专属外部目录
 */
public final class PathConfig {

    public static final String DEFAULT_ROOT_NAME = "dex52pj";

    private static final String KEY_CACHE_DIR = "pref_cache_dir";
    private static final String KEY_UNPACK_DIR = "pref_unpack_dir";
    private static final String KEY_OUTPUT_DIR = "pref_output_dir";
    private static final String KEY_KEYSTORE_DIR = "pref_keystore_dir";

    private static volatile PathConfig instance;

    private final SharedPreferences prefs;
    private volatile File defaultRoot;

    private PathConfig(Context ctx) {
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
        defaultRoot = resolveDefaultRoot(ctx.getApplicationContext());
    }

    public static synchronized PathConfig init(Context ctx) {
        if (instance == null) {
            instance = new PathConfig(ctx.getApplicationContext());
        }
        return instance;
    }

    public static PathConfig get() {
        if (instance == null) {
            throw new IllegalStateException("PathConfig not initialized. Call init() first.");
        }
        return instance;
    }

    /** 默认根目录：Download/dex52pj */
    public File getDefaultRoot() {
        return defaultRoot;
    }

    private static File resolveDefaultRoot(Context ctx) {
        // 优先 Download/dex52pj
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File root = new File(downloads, DEFAULT_ROOT_NAME);
        try {
            if ((root.exists() || root.mkdirs()) && root.canWrite()) {
                return root;
            }
        } catch (Throwable ignored) {
        }
        // fallback: 应用专属外部目录
        File appSpecific = ctx.getExternalFilesDir(null);
        File fallback = new File(appSpecific, DEFAULT_ROOT_NAME);
        // noinspection ResultOfMethodCallIgnored
        fallback.mkdirs();
        return fallback;
    }

    public File getCacheDir() {
        return ensure(resolvePath(KEY_CACHE_DIR, "cache"));
    }

    public File getUnpackDir() {
        return ensure(resolvePath(KEY_UNPACK_DIR, "unpacked"));
    }

    public File getOutputDir() {
        return ensure(resolvePath(KEY_OUTPUT_DIR, "output"));
    }

    public File getKeyStoreDir() {
        return ensure(resolvePath(KEY_KEYSTORE_DIR, "keystore"));
    }

    public String getCacheDirPref() {
        return prefs.getString(KEY_CACHE_DIR, "");
    }

    public String getUnpackDirPref() {
        return prefs.getString(KEY_UNPACK_DIR, "");
    }

    public String getOutputDirPref() {
        return prefs.getString(KEY_OUTPUT_DIR, "");
    }

    public String getKeyStoreDirPref() {
        return prefs.getString(KEY_KEYSTORE_DIR, "");
    }

    public void setCacheDir(String path) {
        prefs.edit().putString(KEY_CACHE_DIR, path).apply();
    }

    public void setUnpackDir(String path) {
        prefs.edit().putString(KEY_UNPACK_DIR, path).apply();
    }

    public void setOutputDir(String path) {
        prefs.edit().putString(KEY_OUTPUT_DIR, path).apply();
    }

    public void setKeyStoreDir(String path) {
        prefs.edit().putString(KEY_KEYSTORE_DIR, path).apply();
    }

    public void resetToDefault() {
        prefs.edit()
                .remove(KEY_CACHE_DIR)
                .remove(KEY_UNPACK_DIR)
                .remove(KEY_OUTPUT_DIR)
                .remove(KEY_KEYSTORE_DIR)
                .apply();
    }

    private File resolvePath(String key, String subDir) {
        String custom = prefs.getString(key, "");
        if (custom != null && !custom.isEmpty()) {
            return new File(custom);
        }
        return new File(defaultRoot, subDir);
    }

    private static File ensure(File dir) {
        if (dir == null) return null;
        if (!dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }
}
