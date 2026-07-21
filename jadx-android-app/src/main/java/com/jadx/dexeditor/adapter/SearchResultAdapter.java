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

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    public interface OnResultClickListener {
        void onResultClicked(SearchResult result);
    }

    private final List<SearchResult> items = new ArrayList<>();
    private final OnResultClickListener listener;

    public SearchResultAdapter(OnResultClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SearchResult> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.result_title);
            subtitle = itemView.findViewById(R.id.result_subtitle);
        }

        void bind(final SearchResult result) {
            title.setText(result.getTitle());
            subtitle.setText(result.getSubtitle());
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onResultClicked(result);
                }
            });
        }
    }
}
