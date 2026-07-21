package com.jadx.dexeditor.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.jadx.dexeditor.DexEditorApp;
import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.SmaliUtils;

public class InfoFragment extends Fragment {
    /** 对话框内显示的最大字符数，超过则截断（避免大文本布局/渲染卡死） */
    private static final int MAX_DIALOG_TEXT = 6000;

    private TextView infoFileName;
    private TextView infoDexCount;
    private TextView infoClassCount;
    private TextView infoMethodCount;
    private TextView infoFieldCount;
    private TextView emptyView;
    private Thread countWorker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_info, container, false);
        infoFileName = v.findViewById(R.id.info_file_name);
        infoDexCount = v.findViewById(R.id.info_dex_count);
        infoClassCount = v.findViewById(R.id.info_class_count);
        infoMethodCount = v.findViewById(R.id.info_method_count);
        infoFieldCount = v.findViewById(R.id.info_field_count);
        emptyView = v.findViewById(R.id.empty_view);

        Button btnViewError = v.findViewById(R.id.btn_view_error);
        Button btnClearError = v.findViewById(R.id.btn_clear_error);
        btnViewError.setOnClickListener(btn -> showLastError());
        btnClearError.setOnClickListener(btn -> {
            DexEditorApp.clearLastError();
            Toast.makeText(getContext(), "已清除", Toast.LENGTH_SHORT).show();
        });

        refresh();
        return v;
    }

    /** 显示最近一次错误日志（手机端调试用） */
    private void showLastError() {
        String err = DexEditorApp.getLastError();
        if (err == null || err.isEmpty()) {
            Toast.makeText(getContext(), "没有错误日志", Toast.LENGTH_SHORT).show();
            return;
        }
        final String copyText = err;
        final String displayText;
        if (err.length() > MAX_DIALOG_TEXT) {
            displayText = err.substring(0, MAX_DIALOG_TEXT)
                    + "\n\n…（已截断 " + (err.length() - MAX_DIALOG_TEXT)
                    + " 字符，完整内容请点“复制”）";
        } else {
            displayText = err;
        }
        ScrollView scroll = new ScrollView(getContext());
        TextView tv = new TextView(getContext());
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setPadding(48, 32, 48, 32);
        // setTextIsSelectable(true) 在大文本上会显著拖慢布局测量，这里关闭
        tv.setText(displayText);
        scroll.addView(tv);

        new AlertDialog.Builder(requireContext())
                .setTitle("最近错误日志")
                .setView(scroll)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("error", copyText));
                        Toast.makeText(getContext(), "完整日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        DexLoader loader = null;
        if (getActivity() instanceof MainActivity.Host) {
            loader = ((MainActivity.Host) getActivity()).getDexLoader();
        }
        if (loader == null || !loader.isLoaded()) {
            showEmpty();
            return;
        }
        hideEmpty();
        if (infoFileName != null) infoFileName.setText(loader.getDisplayName());
        // 先显示占位，避免空字段
        if (infoDexCount != null) infoDexCount.setText("…");
        if (infoClassCount != null) infoClassCount.setText("…");
        if (infoMethodCount != null) infoMethodCount.setText("…");
        if (infoFieldCount != null) infoFieldCount.setText("…");

        // 取消上一次未完成的统计
        if (countWorker != null) {
            countWorker.interrupt();
            countWorker = null;
        }
        final MultiDexContainer<? extends DexFile> container = loader.getContainer();
        if (container == null) return;
        countWorker = new Thread(() -> {
            int dexCount = 0;
            int classCount = 0;
            int methodCount = 0;
            int fieldCount = 0;
            try {
                for (String entryName : container.getDexEntryNames()) {
                    if (Thread.interrupted()) return;
                    MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
                    if (entry == null) continue;
                    dexCount++;
                    DexFile dex = entry.getDexFile();
                    for (ClassDef cd : dex.getClasses()) {
                        if (Thread.interrupted()) return;
                        classCount++;
                        methodCount += SmaliUtils.countMethods(cd);
                        fieldCount += SmaliUtils.countFields(cd);
                    }
                }
            } catch (Throwable ignored) {
                return;
            }
            final int fDex = dexCount;
            final int fClass = classCount;
            final int fMethod = methodCount;
            final int fField = fieldCount;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                if (infoDexCount != null) infoDexCount.setText(String.valueOf(fDex));
                if (infoClassCount != null) infoClassCount.setText(String.valueOf(fClass));
                if (infoMethodCount != null) infoMethodCount.setText(String.valueOf(fMethod));
                if (infoFieldCount != null) infoFieldCount.setText(String.valueOf(fField));
            });
        });
        countWorker.setDaemon(true);
        countWorker.start();
    }

    private void showEmpty() {
        if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
        if (infoFileName != null) infoFileName.setVisibility(View.GONE);
        if (infoDexCount != null) infoDexCount.setVisibility(View.GONE);
        if (infoClassCount != null) infoClassCount.setVisibility(View.GONE);
        if (infoMethodCount != null) infoMethodCount.setVisibility(View.GONE);
        if (infoFieldCount != null) infoFieldCount.setVisibility(View.GONE);
    }

    private void hideEmpty() {
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (infoFileName != null) infoFileName.setVisibility(View.VISIBLE);
        if (infoDexCount != null) infoDexCount.setVisibility(View.VISIBLE);
        if (infoClassCount != null) infoClassCount.setVisibility(View.VISIBLE);
        if (infoMethodCount != null) infoMethodCount.setVisibility(View.VISIBLE);
        if (infoFieldCount != null) infoFieldCount.setVisibility(View.VISIBLE);
    }
}
