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
    private Thread treeWorker;

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
        // 每次刷新都重新获取 host，避免持有旧 Activity 引用导致 dexLoader 状态不一致
        MainActivity.Host h = null;
        if (getActivity() instanceof MainActivity.Host) {
            h = (MainActivity.Host) getActivity();
            host = h;
        } else if (host == null) {
            showEmpty(getString(R.string.no_file_loaded));
            return;
        } else {
            h = host;
        }
        android.util.Log.i("BrowseFragment", "refresh called, classListView="
                + (classListView != null) + " host=" + (h != null));
        if (classListView == null) {
            return;
        }
        DexLoader loader = h.getDexLoader();
        boolean loaded = loader != null && loader.isLoaded();
        android.util.Log.i("BrowseFragment", "loader=" + (loader != null)
                + " isLoaded=" + loaded);
        if (!loaded) {
            showEmpty(getString(R.string.no_file_loaded));
            return;
        }
        // 取消上一次未完成的构建
        if (treeWorker != null) {
            treeWorker.interrupt();
            treeWorker = null;
        }
        // 先显示加载提示
        showEmpty("加载中…");
        final DexLoader finalLoader = loader;
        treeWorker = new Thread(() -> {
            android.util.Log.i("BrowseFragment", "treeWorker started");
            final List<ClassNode> roots;
            final String[] errorMsg = new String[1];
            try {
                roots = buildTree(finalLoader);
            } catch (Throwable t) {
                android.util.Log.e("BrowseFragment", "buildTree failed", t);
                errorMsg[0] = "构建类树失败: " + t.getClass().getSimpleName()
                        + ": " + t.getMessage();
                if (getActivity() == null) return;
                final String m = errorMsg[0];
                getActivity().runOnUiThread(() -> showEmpty(m));
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                android.util.Log.i("BrowseFragment", "treeWorker interrupted");
                return;
            }
            if (getActivity() == null) {
                android.util.Log.i("BrowseFragment", "activity null after build");
                return;
            }
            android.util.Log.i("BrowseFragment",
                    "treeWorker done, roots=" + (roots == null ? 0 : roots.size()));
            final String msg = errorMsg[0];
            getActivity().runOnUiThread(() -> {
                if (msg != null) {
                    showEmpty(msg);
                    return;
                }
                if (roots == null || roots.isEmpty()) {
                    showEmpty("No classes found");
                    return;
                }
                if (emptyView != null) emptyView.setVisibility(View.GONE);
                classListView.setVisibility(View.VISIBLE);
                adapter.setData(roots);
                android.util.Log.i("BrowseFragment", "adapter.setData done, items="
                        + adapter.getItemCount());
            });
        });
        treeWorker.setDaemon(true);
        treeWorker.start();
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

    private List<ClassNode> buildTree(DexLoader loader) throws Throwable {
        ClassNode root = new ClassNode("Classes");
        Map<String, ClassNode> packageNodes = new HashMap<>();
        MultiDexContainer<? extends DexFile> container = loader.getContainer();
        if (container == null) {
            return new ArrayList<>();
        }
        int entryCount = 0;
        int classCount = 0;
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
            if (entry == null) {
                continue;
            }
            entryCount++;
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
                classCount++;
            }
        }
        android.util.Log.i("BrowseFragment",
                "buildTree done: entries=" + entryCount + " classes=" + classCount
                        + " rootChildren=" + root.getChildren().size());
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
