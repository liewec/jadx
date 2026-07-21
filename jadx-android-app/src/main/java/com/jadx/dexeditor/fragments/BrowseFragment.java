package com.jadx.dexeditor.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.SmaliUtils;
import com.jadx.dexeditor.adapter.ClassTreeAdapter;
import com.jadx.dexeditor.model.ClassNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowseFragment extends Fragment {
    private RecyclerView classListView;
    private TextView emptyView;
    private ClassTreeAdapter adapter;
    private MainActivity.Host host;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() instanceof MainActivity.Host) {
            host = (MainActivity.Host) getActivity();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_browse, container, false);
        classListView = v.findViewById(R.id.class_list);
        emptyView = v.findViewById(R.id.empty_view);
        classListView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ClassTreeAdapter();
        adapter.setOnClassClickListener(classType -> {
            if (host != null) {
                host.openClass(classType);
            }
        });
        classListView.setAdapter(adapter);
        refresh();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            refresh();
        }
    }

    public void refresh() {
        if (classListView == null) {
            return;
        }
        DexLoader loader = host == null ? null : host.getDexLoader();
        if (loader == null || !loader.isLoaded()) {
            showEmpty(getString(R.string.no_file_loaded));
            return;
        }
        List<ClassNode> roots = buildTree(loader);
        if (roots.isEmpty()) {
            showEmpty("No classes found");
            return;
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        classListView.setVisibility(View.VISIBLE);
        adapter.setData(roots);
    }

    private void showEmpty(String msg) {
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(msg);
        }
        if (classListView != null) {
            classListView.setVisibility(View.GONE);
        }
    }

    private List<ClassNode> buildTree(DexLoader loader) {
        ClassNode root = new ClassNode("Classes");
        Map<String, ClassNode> packageNodes = new HashMap<>();
        MultiDexContainer<? extends DexFile> container = loader.getContainer();
        if (container == null) {
            return new ArrayList<>();
        }
        try {
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
                if (entry == null) {
                    continue;
                }
                DexFile dex = entry.getDexFile();
                for (ClassDef cd : dex.getClasses()) {
                    String type = cd.getType();
                    String javaName = SmaliUtils.descriptorToJavaName(type);
                    int lastDot = javaName.lastIndexOf('.');
                    ClassNode pkgNode;
                    if (lastDot > 0) {
                        String pkgPath = javaName.substring(0, lastDot);
                        pkgNode = packageNodes.get(pkgPath);
                        if (pkgNode == null) {
                            pkgNode = ensurePackageNode(root, pkgPath, packageNodes);
                        }
                    } else {
                        pkgNode = root;
                    }
                    ClassNode classNode = new ClassNode(cd);
                    pkgNode.addChild(classNode);
                }
            }
        } catch (Throwable e) {
            return new ArrayList<>();
        }
        return root.getChildren();
    }

    private ClassNode ensurePackageNode(ClassNode root, String pkgPath, Map<String, ClassNode> packageNodes) {
        ClassNode existing = packageNodes.get(pkgPath);
        if (existing != null) {
            return existing;
        }
        int lastDot = pkgPath.lastIndexOf('.');
        ClassNode parent;
        String name;
        if (lastDot > 0) {
            String parentPath = pkgPath.substring(0, lastDot);
            parent = ensurePackageNode(root, parentPath, packageNodes);
            name = pkgPath.substring(lastDot + 1);
        } else {
            parent = root;
            name = pkgPath;
        }
        ClassNode node = new ClassNode(name);
        parent.addChild(node);
        packageNodes.put(pkgPath, node);
        return node;
    }
}
