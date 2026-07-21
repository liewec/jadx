package com.jadx.dexeditor.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.adapter.SearchResultAdapter;
import com.jadx.dexeditor.model.SearchResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment implements SearchResultAdapter.OnResultClickListener {
    private static final int MAX_RESULTS = 500;

    private SearchResultAdapter adapter;
    private TextView emptyText;
    private EditText keywordEdit;
    private ProgressBar progressBar;
    private TextView resultCountText;
    private Button searchButton;
    private Spinner typeSpinner;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new SearchResultAdapter(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);
        typeSpinner = view.findViewById(R.id.search_type_spinner);
        keywordEdit = view.findViewById(R.id.keyword_edit);
        searchButton = view.findViewById(R.id.search_button);
        resultCountText = view.findViewById(R.id.result_count_text);
        emptyText = view.findViewById(R.id.empty_text);
        progressBar = view.findViewById(R.id.loading_progress);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.search_type_class),
                        getString(R.string.search_type_method),
                        getString(R.string.search_type_string)
                });
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);

        RecyclerView recycler = view.findViewById(R.id.result_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        searchButton.setOnClickListener(v -> doSearch());
        keywordEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                doSearch();
                return true;
            }
            return false;
        });
        return view;
    }

    private void doSearch() {
        if (!DexLoader.getInstance().isLoaded()) {
            return;
        }
        final String keyword = keywordEdit.getText().toString().trim();
        if (keyword.isEmpty()) {
            adapter.setItems(null);
            resultCountText.setVisibility(View.GONE);
            emptyText.setVisibility(View.GONE);
            return;
        }
        final int searchKind = typeSpinner.getSelectedItemPosition();
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<SearchResult> results = DexLoader.getInstance().search(searchKind, keyword);
            if (results.size() > MAX_RESULTS) {
                results = results.subList(0, MAX_RESULTS);
            }
            final List<SearchResult> sub = results;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                adapter.setItems(sub);
                int count = sub.size();
                resultCountText.setVisibility(View.VISIBLE);
                resultCountText.setText(getString(R.string.search_results_count, count));
                emptyText.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onResultClicked(SearchResult result) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openClass(result.getClassType());
        }
    }

    public void clearResults() {
        if (adapter != null) {
            adapter.setItems(null);
        }
        if (resultCountText != null) {
            resultCountText.setVisibility(View.GONE);
        }
        if (emptyText != null) {
            emptyText.setVisibility(View.GONE);
        }
        if (keywordEdit != null) {
            keywordEdit.setText("");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
