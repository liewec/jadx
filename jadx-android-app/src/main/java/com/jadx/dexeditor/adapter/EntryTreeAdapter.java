package com.jadx.dexeditor.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jadx.dexeditor.R;
import com.jadx.dexeditor.model.EntryNode;

import java.util.ArrayList;
import java.util.List;

/**
 * APK 资源条目树形适配器。
 * <p>
 * 将 APK 内的条目路径（如 {@code res/layout/main.xml}）按 {@code /} 拆分为多层节点，
 * 支持展开/折叠，与 BrowseFragment 的 ClassTreeAdapter 风格一致。
 */
public class EntryTreeAdapter extends RecyclerView.Adapter<EntryTreeAdapter.ViewHolder> {

    public interface OnEntryClickListener {
        /** 点击文件条目 */
        void onEntryClicked(String fullPath);

        /** 长按文件条目，返回是否消费 */
        boolean onEntryLongClicked(String fullPath, String displayName);
    }

    private final OnEntryClickListener listener;
    private EntryNode root;
    private final List<EntryNode> visible = new ArrayList<>();
    private String selectedPath;

    public EntryTreeAdapter(OnEntryClickListener listener) {
        this.listener = listener;
    }

    public void setRoot(EntryNode root) {
        this.root = root;
        rebuild();
    }

    public void rebuild() {
        visible.clear();
        if (root != null) {
            flatten(root);
        }
        notifyDataSetChanged();
    }

    private void flatten(EntryNode node) {
        for (int i = 0; i < node.getChildren().size(); i++) {
            EntryNode child = node.getChildren().get(i);
            visible.add(child);
            if (child.isDir() && child.isExpanded()) {
                flatten(child);
            }
        }
    }

    public void setSelectedPath(String path) {
        this.selectedPath = path;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entry_tree, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(visible.get(position));
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;
        final TextView meta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            text = itemView.findViewById(R.id.item_text);
            meta = itemView.findViewById(R.id.item_meta);
        }

        void bind(final EntryNode node) {
            int indent = node.getDepth() * 24;
            text.setPadding(indent, text.getPaddingTop(), 0, text.getPaddingBottom());

            if (node.isDir()) {
                icon.setImageResource(node.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder);
                icon.setColorFilter(itemView.getContext().getColor(R.color.package_icon));
                text.setText(node.getName());
                int count = node.countFiles();
                meta.setText("(" + count + ")");
            } else {
                icon.setImageResource(R.drawable.ic_class);
                icon.setColorFilter(itemView.getContext().getColor(R.color.class_icon));
                text.setText(node.getName());
                meta.setText(null);
            }

            boolean selected = !node.isDir() && node.getFullPath().equals(selectedPath);
            itemView.setBackgroundColor(selected ? 0x332196F3 : 0x00000000);

            itemView.setOnClickListener(v -> {
                if (node.isDir()) {
                    node.setExpanded(!node.isExpanded());
                    EntryTreeAdapter.this.rebuild();
                } else if (listener != null) {
                    listener.onEntryClicked(node.getFullPath());
                }
            });
            itemView.setOnLongClickListener(v -> {
                if (!node.isDir() && listener != null) {
                    return listener.onEntryLongClicked(node.getFullPath(), node.getName());
                }
                return false;
            });
        }
    }
}
