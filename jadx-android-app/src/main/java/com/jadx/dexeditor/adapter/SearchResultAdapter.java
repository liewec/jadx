package com.jadx.dexeditor.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jadx.dexeditor.R;
import com.jadx.dexeditor.model.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {
    private final List<SearchResult> data = new ArrayList<>();
    private final Consumer<SearchResult> onClick;

    public SearchResultAdapter(Consumer<SearchResult> onClick) {
        this.onClick = onClick;
    }

    public void setData(List<SearchResult> newData) {
        data.clear();
        if (newData != null) {
            data.addAll(newData);
        }
        notifyDataSetChanged();
    }

    public void clear() {
        data.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResult r = data.get(position);
        holder.bind(r);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.search_result_title);
            subtitle = itemView.findViewById(R.id.search_result_subtitle);
        }

        void bind(SearchResult r) {
            if (title != null) {
                title.setText(r.getTitle());
            }
            if (subtitle != null) {
                subtitle.setText(r.getSubtitle());
            }
            itemView.setOnClickListener(v -> {
                if (onClick != null) {
                    onClick.accept(r);
                }
            });
        }
    }
}
