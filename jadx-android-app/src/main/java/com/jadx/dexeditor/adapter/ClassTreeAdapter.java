package com.jadx.dexeditor.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jadx.dexeditor.R;
import com.jadx.dexeditor.model.ClassNode;

import java.util.ArrayList;
import java.util.List;

public class ClassTreeAdapter extends RecyclerView.Adapter<ClassTreeAdapter.ViewHolder> {

    public interface OnNodeClickListener {
        void onClassClicked(String classType);

        boolean onClassLongClicked(String classType, String displayName);
    }

    private final OnNodeClickListener listener;
    private ClassNode root;
    private final List<ClassNode> visible = new ArrayList<>();

    public ClassTreeAdapter(OnNodeClickListener listener) {
        this.listener = listener;
    }

    public void setRoot(ClassNode root) {
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

    private void flatten(ClassNode node) {
        for (int i = 0; i < node.getChildren().size(); i++) {
            ClassNode child = node.getChildren().get(i);
            visible.add(child);
            if (child.isPackage() && child.isExpanded()) {
                flatten(child);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_class_tree, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassNode node = visible.get(position);
        holder.bind(node);
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            text = itemView.findViewById(R.id.item_text);
        }

        void bind(final ClassNode node) {
            String string;
            int indent = node.getDepth() * 24;
            text.setPadding(indent, text.getPaddingTop(), 0, text.getPaddingBottom());
            if (node.isPackage()) {
                icon.setImageResource(node.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder);
                icon.setColorFilter(itemView.getContext().getColor(R.color.package_icon));
                int count = node.countClasses();
                if (node.getName().isEmpty()) {
                    string = itemView.getContext().getString(R.string.default_package);
                } else {
                    string = node.getName() + "  (" + count + ")";
                }
                text.setText(string);
            } else {
                icon.setImageResource(R.drawable.ic_class);
                icon.setColorFilter(itemView.getContext().getColor(R.color.class_icon));
                text.setText(node.getName());
            }
            itemView.setOnClickListener(v -> {
                if (node.isPackage()) {
                    node.setExpanded(!node.isExpanded());
                    ClassTreeAdapter.this.rebuild();
                } else if (listener != null) {
                    listener.onClassClicked(node.getClassType());
                }
            });
            itemView.setOnLongClickListener(v -> {
                if (node.isClass() && listener != null) {
                    return listener.onClassLongClicked(node.getClassType(), node.getName());
                }
                return false;
            });
        }
    }
}
