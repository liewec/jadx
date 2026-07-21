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
    private TextView infoFileName;
    private TextView infoDexCount;
    private TextView infoClassCount;
    private TextView infoMethodCount;
    private TextView infoFieldCount;
    private TextView emptyView;

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
        ScrollView scroll = new ScrollView(getContext());
        TextView tv = new TextView(getContext());
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setPadding(48, 32, 48, 32);
        tv.setTextIsSelectable(true);
        tv.setText(err);
        scroll.addView(tv);

        new AlertDialog.Builder(requireContext())
                .setTitle("最近错误日志")
                .setView(scroll)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("error", err));
                        Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
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
        int dexCount = 0;
        int classCount = 0;
        int methodCount = 0;
        int fieldCount = 0;
        MultiDexContainer<? extends DexFile> container = loader.getContainer();
        if (container != null) {
            try {
                for (String entryName : container.getDexEntryNames()) {
                    MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
                    if (entry == null) {
                        continue;
                    }
                    dexCount++;
                    DexFile dex = entry.getDexFile();
                    for (ClassDef cd : dex.getClasses()) {
                        classCount++;
                        methodCount += SmaliUtils.countMethods(cd);
                        fieldCount += SmaliUtils.countFields(cd);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (infoFileName != null) infoFileName.setText(loader.getDisplayName());
        if (infoDexCount != null) infoDexCount.setText(String.valueOf(dexCount));
        if (infoClassCount != null) infoClassCount.setText(String.valueOf(classCount));
        if (infoMethodCount != null) infoMethodCount.setText(String.valueOf(methodCount));
        if (infoFieldCount != null) infoFieldCount.setText(String.valueOf(fieldCount));
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
