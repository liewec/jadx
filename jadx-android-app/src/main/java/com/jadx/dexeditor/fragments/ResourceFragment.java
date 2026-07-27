package com.jadx.dexeditor.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jadx.dexeditor.PathConfig;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.adapter.EntryTreeAdapter;
import com.jadx.dexeditor.apk.ApkBuilder;
import com.jadx.dexeditor.model.EntryNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 资源页：以层级树展示 APK 内所有条目，支持展开/折叠，
 * 替换资源（图标、布局、字符串），重新打包并签名。
 */
public class ResourceFragment extends Fragment {

    private TextView statusText;
    private ProgressBar progress;
    private Button btnReplace;
    private Button btnPackSign;
    private RecyclerView recycler;
    private TextView emptyText;

    private File currentApk;
    private String selectedEntry;
    private final List<String> entries = new ArrayList<>();
    private final EntryTreeAdapter adapter = new EntryTreeAdapter(new EntryTreeAdapter.OnEntryClickListener() {
        @Override
        public void onEntryClicked(String fullPath) {
            selectedEntry = fullPath;
            adapter.setSelectedPath(fullPath);
            btnReplace.setEnabled(true);
            statusText.setText(getString(R.string.entry_selected, fullPath));
        }

        @Override
        public boolean onEntryLongClicked(String fullPath, String displayName) {
            copyToClipboard(displayName);
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            return true;
        }
    });

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> openApkLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) openApk(uri);
            });

    private final ActivityResultLauncher<String> replaceFileLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && selectedEntry != null) replaceEntry(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_resource, container, false);
        statusText = view.findViewById(R.id.status_text);
        progress = view.findViewById(R.id.loading_progress);
        btnReplace = view.findViewById(R.id.btn_replace);
        btnPackSign = view.findViewById(R.id.btn_pack_sign);
        recycler = view.findViewById(R.id.entry_recycler);
        emptyText = view.findViewById(R.id.empty_text);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btn_open_apk).setOnClickListener(v ->
                openApkLauncher.launch("*/*"));
        btnReplace.setOnClickListener(v -> {
            if (selectedEntry == null) {
                Toast.makeText(requireContext(), R.string.please_select_entry, Toast.LENGTH_SHORT).show();
                return;
            }
            replaceFileLauncher.launch("*/*");
        });
        btnPackSign.setOnClickListener(v -> packAndSign());
        return view;
    }

    private void openApk(Uri uri) {
        setLoading(true);
        new Thread(() -> {
            try {
                File cached = copyToCache(uri, "apk");
                currentApk = cached;
                List<String> list = ApkBuilder.listEntries(cached);
                Collections.sort(list);
                entries.clear();
                entries.addAll(list);
                final EntryNode root = buildTree(list);
                mainHandler.post(() -> {
                    setLoading(false);
                    adapter.setRoot(root);
                    boolean empty = entries.isEmpty();
                    recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                    emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
                    statusText.setText(getString(R.string.apk_entries, currentApk.getName(), entries.size()));
                    btnPackSign.setEnabled(!empty);
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.load_error_prefix) + e.getMessage());
                });
            }
        }).start();
    }

    /** 将扁平的 APK 条目列表构建为层级树。根节点的 children 为顶层条目。 */
    private static EntryNode buildTree(List<String> entries) {
        EntryNode root = new EntryNode(EntryNode.TYPE_DIR, "", "", 0);
        for (String path : entries) {
            String[] segs = path.split("/");
            EntryNode cur = root;
            StringBuilder acc = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                String seg = segs[i];
                if (seg.isEmpty()) continue;
                if (acc.length() > 0) acc.append("/");
                acc.append(seg);
                boolean isLast = (i == segs.length - 1);
                int type = isLast ? EntryNode.TYPE_FILE : EntryNode.TYPE_DIR;
                String fullPath = isLast ? path : acc + "/";
                EntryNode child = findChild(cur, seg, type);
                if (child == null) {
                    child = new EntryNode(type, seg, fullPath, cur.getDepth() + 1);
                    cur.getChildren().add(child);
                }
                cur = child;
            }
        }
        sortTree(root);
        // 顶层默认展开，便于看到一级目录
        root.setExpanded(true);
        for (EntryNode n : root.getChildren()) {
            if (n.isDir()) n.setExpanded(true);
        }
        return root;
    }

    private static EntryNode findChild(EntryNode parent, String name, int type) {
        for (EntryNode c : parent.getChildren()) {
            if (c.getName().equals(name) && c.getType() == type) return c;
        }
        return null;
    }

    /** 目录在前、文件在后，各自按名称排序；递归处理子节点。 */
    private static void sortTree(EntryNode node) {
        List<EntryNode> children = node.getChildren();
        Collections.sort(children, (a, b) -> {
            if (a.isDir() != b.isDir()) return a.isDir() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (EntryNode c : children) sortTree(c);
    }

    private void replaceEntry(Uri replacementUri) {
        if (currentApk == null) return;
        setLoading(true);
        new Thread(() -> {
            File tmp = null;
            try {
                tmp = File.createTempFile("replace_", ".bin", PathConfig.get().getCacheDir());
                try (InputStream in = requireContext().getContentResolver().openInputStream(replacementUri);
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    if (in == null) throw new RuntimeException("无法打开替换文件");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                File outApk = new File(PathConfig.get().getOutputDir(),
                        currentApk.getName().replace(".apk", "") + "_replaced.apk");
                byte[] bytes = readAllBytes(tmp);
                ApkBuilder.replaceEntry(currentApk, selectedEntry, bytes, outApk);
                currentApk = outApk;
                final String entryName = selectedEntry;
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.entry_replaced, entryName, outApk.getName()));
                    Toast.makeText(requireContext(), R.string.replace_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("替换失败：" + e.getMessage());
                });
            } finally {
                if (tmp != null) // noinspection ResultOfMethodCallIgnored
                    tmp.delete();
            }
        }).start();
    }

    private void packAndSign() {
        if (currentApk == null) {
            Toast.makeText(requireContext(), R.string.please_open_apk, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        new Thread(() -> {
            try {
                File outDir = PathConfig.get().getOutputDir();
                File signed = new File(outDir,
                        currentApk.getName().replace(".apk", "") + "_signed.apk");
                try {
                    ApkBuilder.signWithBuiltinKey(currentApk, signed);
                    mainHandler.post(() -> {
                        setLoading(false);
                        statusText.setText(getString(R.string.apk_signed, signed.getAbsolutePath()));
                        Toast.makeText(requireContext(), R.string.sign_success, Toast.LENGTH_SHORT).show();
                    });
                } catch (Throwable signErr) {
                    // 签名失败，把未签名 APK 拷到输出
                    File unsigned = new File(outDir,
                            currentApk.getName().replace(".apk", "") + "_unsigned.apk");
                    copyFile(currentApk, unsigned);
                    mainHandler.post(() -> {
                        setLoading(false);
                        statusText.setText(getString(R.string.sign_failed, signErr.getMessage(),
                                unsigned.getAbsolutePath()));
                    });
                }
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("打包失败：" + e.getMessage());
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

    private static byte[] readAllBytes(File f) throws Exception {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void copyToClipboard(String text) {
        Context ctx = getContext();
        if (ctx == null) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("entry", text));
        }
    }
}
