package com.jadx.dexeditor.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
import com.jadx.dexeditor.apk.ApkBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 资源页：查看 APK 内所有文件，替换资源（图标、布局、字符串），重新打包并签名。
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
    private final EntryAdapter adapter = new EntryAdapter();

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
                mainHandler.post(() -> {
                    setLoading(false);
                    adapter.notifyDataSetChanged();
                    boolean empty = entries.isEmpty();
                    recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                    emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
                    statusText.setText(getString(R.string.apk_entries, currentApk.getName(), entries.size()));
                    btnPackSign.setEnabled(true);
                });
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText(getString(R.string.load_error_prefix) + e.getMessage());
                });
            }
        }).start();
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
                byte[] data = ApkBuilder.readEntry(tmp, ""); // not used
                // 直接用 ApkBuilder.replaceEntry 不行（要 byte[]），改用：把 tmp 整体读入
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

    private final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 24, 32, 24);
            tv.setTextSize(13);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final String name = entries.get(position);
            ((TextView) holder.itemView).setText(name);
            holder.itemView.setBackgroundColor(
                    name.equals(selectedEntry) ? 0x332196F3 : 0x00000000);
            holder.itemView.setOnClickListener(v -> {
                selectedEntry = name;
                notifyDataSetChanged();
                btnReplace.setEnabled(true);
                statusText.setText(getString(R.string.entry_selected, name));
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
