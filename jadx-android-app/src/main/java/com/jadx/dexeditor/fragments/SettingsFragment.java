package com.jadx.dexeditor.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.jadx.dexeditor.PathConfig;
import com.jadx.dexeditor.R;

/**
 * 设置页：路径配置。
 * 参考 blackdex 把产物默认放在 Download/dex52pj 的策略。
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        refreshSummaries();
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
