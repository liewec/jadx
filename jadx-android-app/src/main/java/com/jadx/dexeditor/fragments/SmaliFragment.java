package com.jadx.dexeditor.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.SmaliUtils;

import java.io.File;
import java.io.FileWriter;

public class SmaliFragment extends Fragment {
    public static final String ARG_CLASS_TYPE = "class_type";

    private TextView smaliCode;
    private TextView smaliTitle;
    private Button btnBack;
    private Button btnSaveSmali;
    private String classType;
    private ClassDef classDef;
    private String smaliText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_smali, container, false);
        smaliCode = v.findViewById(R.id.smali_code);
        smaliTitle = v.findViewById(R.id.smali_title);
        btnBack = v.findViewById(R.id.btn_back);
        btnSaveSmali = v.findViewById(R.id.btn_save_smali);

        Bundle args = getArguments();
        if (args != null) {
            classType = args.getString(ARG_CLASS_TYPE);
        }
        if (savedInstanceState != null) {
            classType = savedInstanceState.getString(ARG_CLASS_TYPE, classType);
        }

        if (smaliCode != null) {
            smaliCode.setTypeface(Typeface.MONOSPACE);
            smaliCode.setMovementMethod(new ScrollingMovementMethod());
            smaliCode.setTextIsSelectable(true);
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(btn -> getParentFragmentManager().popBackStack());
        }

        if (btnSaveSmali != null) {
            btnSaveSmali.setOnClickListener(btn -> saveSmali());
        }

        loadAndShow();
        return v;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (classType != null) {
            outState.putString(ARG_CLASS_TYPE, classType);
        }
    }

    private void loadAndShow() {
        if (classType == null) {
            if (smaliCode != null) smaliCode.setText("No class specified");
            return;
        }
        DexLoader loader = null;
        if (getActivity() instanceof MainActivity.Host) {
            loader = ((MainActivity.Host) getActivity()).getDexLoader();
        }
        if (loader == null || !loader.isLoaded()) {
            if (smaliCode != null) smaliCode.setText(getString(R.string.no_file_loaded));
            return;
        }
        classDef = findClassDef(loader);
        if (classDef == null) {
            if (smaliCode != null) smaliCode.setText("Class not found: " + classType);
            return;
        }
        smaliText = SmaliUtils.disassembleClass(classDef);
        if (smaliTitle != null) {
            smaliTitle.setText(SmaliUtils.simpleName(classType));
        }
        if (smaliCode != null) {
            smaliCode.setText(smaliText);
        }
    }

    private ClassDef findClassDef(DexLoader loader) {
        MultiDexContainer<? extends DexFile> container = loader.getContainer();
        if (container == null) {
            return null;
        }
        try {
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
                if (entry == null) {
                    continue;
                }
                DexFile dex = entry.getDexFile();
                for (ClassDef cd : dex.getClasses()) {
                    if (classType.equals(cd.getType())) {
                        return cd;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void saveSmali() {
        if (smaliText == null || smaliText.isEmpty()) {
            Toast.makeText(getContext(), "Nothing to save", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = classType == null ? "class" : SmaliUtils.simpleName(classType);
        File outDir = getContext() == null ? null : getContext().getCacheDir();
        if (outDir == null) {
            Toast.makeText(getContext(), "No cache dir", Toast.LENGTH_SHORT).show();
            return;
        }
        File outFile = new File(outDir, name + ".smali");
        try (FileWriter fw = new FileWriter(outFile)) {
            fw.write(smaliText);
            Toast.makeText(getContext(), "Saved: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
