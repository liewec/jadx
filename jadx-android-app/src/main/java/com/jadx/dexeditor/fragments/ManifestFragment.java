package com.jadx.dexeditor.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import com.jadx.dexeditor.axml.ArscParser;
import com.jadx.dexeditor.axml.AxmlConverter;
import com.jadx.dexeditor.axml.XmlFormatter;
import com.jadx.dexeditor.widget.XmlAutoComplete;
import com.jadx.dexeditor.widget.XmlSyntaxHighlighter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * AndroidManifest.xml 编辑页：
 * - 选择 APK
 * - 反编译 AndroidManifest.xml（二进制 AXML → 文本 XML）
 * - 资源 ID 转名称（依赖同 APK 中的 resources.arsc）
 * - 文本 XML 格式化 / 语法高亮 / 自动补全
 * - 重编译（文本 XML → 二进制 AXML）并写回 APK
 */
public class ManifestFragment extends Fragment {

    private EditText xmlEdit;
    private TextView statusText;
    private ProgressBar progress;
    private Button btnDecode;
    private Button btnSave;
    private Button btnFormat;

    private File currentApk;
    private byte[] originalManifest;
    private Map<Integer, String> idToName = new HashMap<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> openApkLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) openApk(uri);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manifest, container, false);
        xmlEdit = view.findViewById(R.id.xml_edit);
        statusText = view.findViewById(R.id.status_text);
        progress = view.findViewById(R.id.loading_progress);
        btnDecode = view.findViewById(R.id.btn_decode);
        btnSave = view.findViewById(R.id.btn_save);
        btnFormat = view.findViewById(R.id.btn_format);

        view.findViewById(R.id.btn_open_apk).setOnClickListener(v ->
                openApkLauncher.launch("*/*"));
        btnDecode.setOnClickListener(v -> decodeManifest());
        btnSave.setOnClickListener(v -> saveManifest());
        btnFormat.setOnClickListener(v -> formatXml());

        // 语法高亮 + 自动补全
        int cTag = 0xFF1565C0;
        int cAttr = 0xFF6A1B9A;
        int cValue = 0xFF388E3C;
        int cComment = 0xFF9E9E9E;
        int cDecl = 0xFFEF6C00;
        int cEntity = 0xFFC62828;
        int cBracket = 0xFF455A64;
        int cColor = 0xFFD81B60;
        xmlEdit.addTextChangedListener(new XmlSyntaxHighlighter(xmlEdit,
                cTag, cAttr, cValue, cComment, cDecl, cEntity, cBracket, cColor));
        xmlEdit.addTextChangedListener(new XmlAutoComplete(xmlEdit));
        return view;
    }

    private void openApk(Uri uri) {
        setLoading(true);
        statusText.setText(R.string.loading);
        new Thread(() -> {
            try {
                File cached = copyToCache(uri, "apk");
                currentApk = cached;
                originalManifest = ApkBuilder.readEntry(cached, "AndroidManifest.xml");
                if (originalManifest == null) {
                    throw new RuntimeException("APK 中没有 AndroidManifest.xml");
                }
                // 读 resources.arsc
                byte[] arsc = ApkBuilder.readEntry(cached, "resources.arsc");
                Map<Integer, String> map = new HashMap<>();
                if (arsc != null) {
                    try {
                        map = ArscParser.parse(arsc);
                    } catch (Throwable t) {
                        // 解析失败也能继续
                    }
                }
                idToName = map;
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.apk_loaded, currentApk.getName(),
                            idToName.size()));
                    btnDecode.setEnabled(true);
                    decodeManifest();
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.load_error_prefix) + e.getMessage());
                });
            }
        }).start();
    }

    private void decodeManifest() {
        if (originalManifest == null) {
            Toast.makeText(requireContext(), R.string.please_open_apk, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        new Thread(() -> {
            try {
                String text = AxmlConverter.toTextXml(originalManifest, idToName);
                String formatted = XmlFormatter.format(text);
                mainHandler.post(() -> {
                    xmlEdit.setText(formatted);
                    setLoading(false);
                    statusText.setText(R.string.manifest_decoded);
                    btnSave.setEnabled(true);
                    btnFormat.setEnabled(true);
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("反编译失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private void formatXml() {
        Editable e = xmlEdit.getText();
        if (e == null || e.length() == 0) return;
        String formatted = XmlFormatter.format(e.toString());
        int cursor = Math.min(xmlEdit.getSelectionStart(), formatted.length());
        xmlEdit.setText(formatted);
        if (cursor >= 0) xmlEdit.setSelection(cursor);
    }

    private void saveManifest() {
        if (currentApk == null) {
            Toast.makeText(requireContext(), R.string.please_open_apk, Toast.LENGTH_SHORT).show();
            return;
        }
        Editable e = xmlEdit.getText();
        if (e == null || e.length() == 0) {
            Toast.makeText(requireContext(), R.string.manifest_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        statusText.setText(R.string.compiling);
        new Thread(() -> {
            try {
                byte[] newBinary = AxmlConverter.toBinary(e.toString());
                File outDir = PathConfig.get().getOutputDir();
                File outApk = new File(outDir,
                        currentApk.getName().replace(".apk", "") + "_edited.apk");
                ApkBuilder.replaceEntry(currentApk, "AndroidManifest.xml", newBinary, outApk);
                // 签名
                File signed = new File(outDir,
                        currentApk.getName().replace(".apk", "") + "_signed.apk");
                try {
                    ApkBuilder.signWithBuiltinKey(outApk, signed);
                    // noinspection ResultOfMethodCallIgnored
                    outApk.delete();
                    final File finalOut = signed;
                    mainHandler.post(() -> {
                        setLoading(false);
                        statusText.setText(getString(R.string.manifest_saved_signed, finalOut.getAbsolutePath()));
                        Toast.makeText(requireContext(),
                                getString(R.string.compile_success), Toast.LENGTH_SHORT).show();
                    });
                } catch (Throwable signErr) {
                    // 签名失败，保留未签名 APK
                    mainHandler.post(() -> {
                        setLoading(false);
                        statusText.setText(getString(R.string.manifest_saved_unsigned,
                                outApk.getAbsolutePath(), signErr.getMessage()));
                    });
                }
            } catch (Throwable err) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("编译失败：" + err.getMessage());
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
