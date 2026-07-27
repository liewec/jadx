package com.jadx.dexeditor.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.jadx.dexeditor.PathConfig;
import com.jadx.dexeditor.R;

/**
 * 设置页：路径配置。
 * 参考 blackdex 把产物默认放在 Download/dex52pj 的策略。
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** 官方网站（吾爱破解论坛） */
    private static final String OFFICIAL_WEBSITE_URL =
            "https://www.52pojie.cn/thread-2118606-1-1.html";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        refreshSummaries();
        Preference website = findPreference("pref_official_website");
        if (website != null) {
            website.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_WEBSITE_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Throwable e) {
                    Toast.makeText(requireContext(), "未找到可打开链接的浏览器",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        refreshSummaries();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        refreshSummaries();
    }

    private void refreshSummaries() {
        PathConfig cfg = PathConfig.get();
        setSummary("pref_cache_dir", cfg.getCacheDir().getAbsolutePath());
        setSummary("pref_unpack_dir", cfg.getUnpackDir().getAbsolutePath());
        setSummary("pref_output_dir", cfg.getOutputDir().getAbsolutePath());
        setSummary("pref_keystore_dir", cfg.getKeyStoreDir().getAbsolutePath());
    }

    private void setSummary(String key, String value) {
        EditTextPreference p = findPreference(key);
        if (p != null && !TextUtils.isEmpty(value)) {
            p.setSummary(value);
        }
    }
}
