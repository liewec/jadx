package com.jadx.dexeditor.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.jadx.dexeditor.PathConfig;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.apk.ApkBuilder;
import com.jadx.dexeditor.apk.Unpacker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 脱壳页：选择 APK → 自动检测壳 → 静态脱壳 → 输出 dex 到 unpack 目录。
 */
public class UnshellFragment extends Fragment {

    private TextView statusText;
    private TextView resultText;
    private ProgressBar progress;
    private Button btnDetect;
    private Button btnUnpack;

    private File currentApk;
    private Unpacker.DetectResult detectResult;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> openApkLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) openApk(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_unshell, container, false);
        statusText = view.findViewById(R.id.status_text);
        resultText = view.findViewById(R.id.result_text);
        progress = view.findViewById(R.id.loading_progress);
        btnDetect = view.findViewById(R.id.btn_detect);
        btnUnpack = view.findViewById(R.id.btn_unpack);

        view.findViewById(R.id.btn_open_apk).setOnClickListener(v ->
                openApkLauncher.launch("*/*"));
        btnDetect.setOnClickListener(v -> detect());
        btnUnpack.setOnClickListener(v -> unpack());
        return view;
    }

    private void openApk(Uri uri) {
        setLoading(true);
        new Thread(() -> {
            try {
                File cached = copyToCache(uri, "apk");
                currentApk = cached;
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.apk_loaded_simple, currentApk.getName()));
                    btnDetect.setEnabled(true);
                    btnUnpack.setEnabled(false);
                    resultText.setText("");
                    detect();
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.load_error_prefix) + e.getMessage());
                });
            }
        }).start();
    }

    private void detect() {
        if (currentApk == null) {
            Toast.makeText(requireContext(), R.string.please_open_apk, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        new Thread(() -> {
            try {
                detectResult = Unpacker.detect(currentApk);
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(detectResult.packed
                            ? getString(R.string.packer_detected, detectResult.packerName)
                            : getString(R.string.no_packer));
                    StringBuilder sb = new StringBuilder();
                    sb.append(detectResult.toString()).append("\n\n");
                    if (detectResult.packed) {
                        sb.append("可点击\"脱壳\"尝试静态提取 dex\n");
                    }
                    resultText.setText(sb.toString());
                    btnUnpack.setEnabled(detectResult.packed);
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("检测失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private void unpack() {
        if (currentApk == null) {
            Toast.makeText(requireContext(), R.string.please_open_apk, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        new Thread(() -> {
            try {
                File outDir = PathConfig.get().getUnpackDir();
                Unpacker.UnpackResult r = Unpacker.unpack(currentApk, outDir);
                StringBuilder sb = new StringBuilder();
                sb.append(detectResult != null ? detectResult.toString() : "").append("\n\n");
                sb.append("=== 脱壳结果 ===\n");
                sb.append("成功：").append(r.success).append("\n");
                sb.append("信息：").append(r.message).append("\n");
                if (!r.dexFiles.isEmpty()) {
                    sb.append("提取的 dex：\n");
                    for (File f : r.dexFiles) {
                        sb.append("  • ").append(f.getAbsolutePath())
                                .append("  (").append(f.length()).append(" 字节)\n");
                    }
                }
                mainHandler.post(() -> {
                    setLoading(false);
                    resultText.setText(sb.toString());
                    if (r.success) {
                        statusText.setText(getString(R.string.unpack_success, r.dexFiles.size()));
                    } else {
                        statusText.setText(R.string.unpack_failed);
                    }
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("脱壳失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private File copyToCache(Uri uri, String ext) throws Exception {
        String suffix = "." + ext;
        File outFile = File.createTempFile("apk_", suffix, PathConfig.get().getCacheDir());
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new RuntimeException("无法打开 URI");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return outFile;
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
