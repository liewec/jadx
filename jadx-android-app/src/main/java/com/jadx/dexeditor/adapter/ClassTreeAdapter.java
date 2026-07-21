package com.jadx.dexeditor.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jadx.dexeditor.R;
import com.jadx.dexeditor.model.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ClassTreeAdapter extends RecyclerView.Adapter<ClassTreeAdapter.ViewHolder> {

    private static class FlatItem {
        final ClassNode node;
        final int depth;

        FlatItem(ClassNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private final List<FlatItem> flatItems = new ArrayList<>();
    private Consumer<String> onClassClickListener;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_class_node, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlatItem item = flatItems.get(position);
        holder.bind(item.node, item.depth);
    }

    @Override
    public int getItemCount() {
        return flatItems.size();
    }

    public void setData(List<ClassNode> roots) {
        flatItems.clear();
        if (roots != null) {
            for (ClassNode root : roots) {
                flatten(root, 0);
            }
        }
        notifyDataSetChanged();
    }

    private void flatten(ClassNode node, int depth) {
        flatItems.add(new FlatItem(node, depth));
        for (ClassNode child : node.getChildren()) {
            flatten(child, depth + 1);
        }
    }

    public void setOnClassClickListener(Consumer<String> listener) {
        this.onClassClickListener = listener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            TextView tv = itemView.findViewById(R.id.class_node_text);
            if (tv == null && itemView instanceof TextView) {
                tv = (TextView) itemView;
            }
            this.text = tv;
        }

        void bind(ClassNode node, int depth) {
            if (text == null) {
                return;
            }
            text.setText(node.getSimpleName());
            int pad = depth * 32 + 16;
            text.setPadding(pad, text.getPaddingTop(), text.getPaddingRight(), text.getPaddingBottom());
            text.setOnClickListener(v -> {
                if (onClassClickListener != null && node.getClassDef() != null) {
                    String type = node.getType();
                    if (type != null) {
                        onClassClickListener.accept(type);
                    }
                }
            });
        }
    }
}
