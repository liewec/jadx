package com.jadx.dexeditor.fragments;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.SmaliUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmaliFragment extends Fragment {
    public static final String ARG_CLASS_TYPE = "class_type";

    private String classType;
    private String cachedSmali;
    private String cachedJava;
    private boolean javaMode = false;

    private EditText smaliEdit;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button compileButton;
    private ToggleButton toggleJava;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SmaliFragment newInstance(String classType) {
        SmaliFragment fragment = new SmaliFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CLASS_TYPE, classType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            classType = args.getString(ARG_CLASS_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_smali, container, false);
        smaliEdit = view.findViewById(R.id.smali_edit);
        statusText = view.findViewById(R.id.status_text);
        progressBar = view.findViewById(R.id.loading_progress);
        compileButton = view.findViewById(R.id.compile_button);
        Button copyButton = view.findViewById(R.id.copy_button);
        toggleJava = view.findViewById(R.id.toggle_java);

        compileButton.setOnClickListener(v -> compile());
        copyButton.setOnClickListener(v -> copyAll());
        toggleJava.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) showJava();
            else showSmali();
        });
        loadSmali();
        return view;
    }

    private void showSmali() {
        javaMode = false;
        compileButton.setVisibility(View.VISIBLE);
        smaliEdit.setFocusable(true);
        smaliEdit.setFocusableInTouchMode(true);
        if (cachedSmali != null) {
            smaliEdit.setText(cachedSmali);
            statusText.setText(null);
        } else {
            loadSmali();
        }
    }

    private void showJava() {
        javaMode = true;
        compileButton.setVisibility(View.GONE);
        smaliEdit.setFocusable(false);
        smaliEdit.setFocusableInTouchMode(false);
        if (cachedJava != null) {
            smaliEdit.setText(cachedJava);
            statusText.setText(null);
        } else {
            loadJava();
        }
    }

    private void loadJava() {
        if (TextUtils.isEmpty(classType)) {
            statusText.setText(R.string.disasm_failed);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.decompiling);
        final String type = classType;
        final File dexFile = DexLoader.getInstance().getLoadedFile();
        executor.execute(() -> {
            final String code = SmaliUtils.decompileToJava(dexFile, type);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                cachedJava = code;
                smaliEdit.setText(code);
                statusText.setText(null);
            });
        });
    }

    private void loadSmali() {
        if (TextUtils.isEmpty(classType)) {
            statusText.setText(R.string.disasm_failed);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.smali_loading);
        final String type = classType;
        executor.execute(() -> {
            ClassDef cls = DexLoader.getInstance().findClass(type);
            if (cls == null) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    statusText.setText(R.string.disasm_failed);
                });
                return;
            }
            try {
                final String smali = SmaliUtils.disassemble(cls);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    cachedSmali = smali;
                    if (!javaMode) {
                        smaliEdit.setText(smali);
                    }
                    statusText.setText(null);
                });
            } catch (final IOException e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    statusText.setText(getString(R.string.disasm_failed) + e.getMessage());
                });
            }
        });
    }

    private void compile() {
        final String smaliCode = smaliEdit.getText().toString();
        if (smaliCode.isEmpty()) {
            statusText.setText(R.string.compile_failed);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.compiling);
        executor.execute(() -> {
            File outDex = new File(requireContext().getCacheDir(),
                    "compiled_" + sanitizeFileName(classType) + ".dex");
            final String message;
            final boolean ok;
            try {
                ok = SmaliUtils.compile(smaliCode, outDex.getAbsolutePath());
                if (ok) {
                    long size = outDex.exists() ? outDex.length() : 0L;
                    message = getString(R.string.compile_success) + "\n" + outDex.getAbsolutePath()
                            + "\n" + getString(R.string.info_file_size) + ": " + size + " 字节";
                } else {
                    message = getString(R.string.compile_failed);
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    statusText.setText(R.string.compile_failed);
                    showResultDialog(getString(R.string.compile_failed) + "\n" + e.getMessage());
                });
                return;
            }
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                statusText.setText(ok ? R.string.compile_success : R.string.compile_failed);
                showResultDialog(message);
            });
        });
    }

    private void copyAll() {
        Context ctx = getContext();
        if (ctx == null) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("smali", smaliEdit.getText().toString()));
        }
        Toast.makeText(ctx, R.string.copy_success, Toast.LENGTH_SHORT).show();
    }

    private void showResultDialog(String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.compile_button)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String sanitizeFileName(String type) {
        if (type == null) return "class";
        String s = type;
        if (s.startsWith("L") && s.endsWith(";")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace('/', '.').replace('$', '_').replace('.', '_');
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
