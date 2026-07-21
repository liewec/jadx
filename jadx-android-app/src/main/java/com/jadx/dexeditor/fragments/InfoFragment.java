package com.jadx.dexeditor.fragments;

import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.R;

public class InfoFragment extends Fragment {
    private TextView content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_info, container, false);
        content = view.findViewById(R.id.info_content);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        if (content == null) return;
        DexLoader loader = DexLoader.getInstance();
        if (!loader.isLoaded()) {
            content.setText(R.string.info_no_data);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.info_file_name)).append("：\n  ").append(safe(loader.getFileName())).append("\n\n");
        sb.append(getString(R.string.info_file_size)).append("：\n  ")
                .append(Formatter.formatFileSize(getContext(), loader.getFileSize()))
                .append(" (").append(loader.getFileSize()).append(" 字节)\n\n");
        sb.append(getString(R.string.info_dex_count)).append("：").append(loader.getDexCount()).append("\n");
        sb.append(getString(R.string.info_class_count)).append("：").append(loader.getClassCount()).append("\n");
        sb.append(getString(R.string.info_method_count)).append("：").append(loader.getMethodCount()).append("\n");
        sb.append(getString(R.string.info_field_count)).append("：").append(loader.getFieldCount()).append("\n");
        sb.append(getString(R.string.info_string_count)).append("：").append(loader.getStringCount()).append("\n");
        sb.append(getString(R.string.info_package_count)).append("：").append(loader.getPackageCount()).append("\n");
        content.setText(sb.toString());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
