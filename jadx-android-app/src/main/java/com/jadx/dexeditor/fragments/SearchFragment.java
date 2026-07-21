package com.jadx.dexeditor.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.SmaliUtils;
import com.jadx.dexeditor.adapter.SearchResultAdapter;
import com.jadx.dexeditor.model.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchFragment extends Fragment {
    private RadioGroup modeGroup;
    private EditText input;
    private Button btnSearch;
    private TextView resultCount;
    private TextView emptyView;
    private RecyclerView resultListView;
    private SearchResultAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_search, container, false);
        modeGroup = v.findViewById(R.id.search_mode_group);
        input = v.findViewById(R.id.search_input);
        btnSearch = v.findViewById(R.id.btn_search);
        resultCount = v.findViewById(R.id.result_count);
        emptyView = v.findViewById(R.id.empty_view);
        resultListView = v.findViewById(R.id.result_list);
        resultListView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SearchResultAdapter(this::onResultClick);
        resultListView.setAdapter(adapter);
        btnSearch.setOnClickListener(vw -> doSearch());
        input.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                doSearch();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 0) clearResults();
            }
        });
        return v;
    }

    public void clearResults() {
        if (adapter != null) adapter.clear();
        if (resultCount != null) { resultCount.setVisibility(View.GONE); resultCount.setText(""); }
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (input != null) input.setText("");
    }

    private void onResultClick(SearchResult r) {
        if (getActivity() instanceof MainActivity.Host) ((MainActivity.Host) getActivity()).openClass(r.getClassType());
    }

    private int currentMode() {
        int checked = modeGroup.getCheckedRadioButtonId();
        if (checked == R.id.radio_method) return SearchResult.MODE_METHOD;
        if (checked == R.id.radio_string) return SearchResult.MODE_STRING;
        return SearchResult.MODE_CLASS;
    }

    private void doSearch() {
        DexLoader loader = getLoader();
        if (loader == null || !loader.isLoaded()) { showEmpty(getString(R.string.no_file_loaded)); return; }
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (keyword.isEmpty()) return;
        int mode = currentMode();
        List<SearchResult> results = new ArrayList<>();
        MultiDexContainer<? extends DexFile> container = loader.getContainer();
        try {
            String lower = keyword.toLowerCase(Locale.US);
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
                if (entry == null) continue;
                DexFile dex = entry.getDexFile();
                for (ClassDef cd : dex.getClasses()) {
                    String type = cd.getType();
                    String javaName = SmaliUtils.descriptorToJavaName(type);
                    if (mode == SearchResult.MODE_CLASS) {
                        if (javaName.toLowerCase(Locale.US).contains(lower))
                            results.add(new SearchResult(mode, SmaliUtils.simpleName(type), javaName, type));
                    } else if (mode == SearchResult.MODE_METHOD) {
                        AtomicInteger hitCount = new AtomicInteger(0);
                        for (com.android.tools.smali.dexlib2.iface.Method m : cd.getMethods()) {
                            if (m.getName().toLowerCase(Locale.US).contains(lower)) hitCount.incrementAndGet();
                        }
                        if (hitCount.get() > 0)
                            results.add(new SearchResult(mode, SmaliUtils.simpleName(type), hitCount + " method(s)", type));
                    } else {
                        for (com.android.tools.smali.dexlib2.iface.Method m : cd.getMethods())
                            searchStringsInMethod(m, lower, type, results, mode);
                    }
                }
            }
        } catch (Throwable e) { showEmpty("search error: " + e.getMessage()); return; }
        renderResults(results);
    }

    private void searchStringsInMethod(com.android.tools.smali.dexlib2.iface.Method m, String lowerKeyword, String classType, List<SearchResult> results, int mode) {
        try {
            com.android.tools.smali.dexlib2.iface.MethodImplementation impl = m.getImplementation();
            if (impl == null) return;
            for (com.android.tools.smali.dexlib2.iface.instruction.Instruction inst : impl.getInstructions()) {
                if (inst instanceof com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction) {
                    Object ref = ((com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        String s = ((StringReference) ref).getString();
                        if (s != null && s.toLowerCase(Locale.US).contains(lowerKeyword)) {
                            String snippet = s.length() > 60 ? s.substring(0, 60) + "…" : s;
                            results.add(new SearchResult(mode, snippet, SmaliUtils.simpleName(classType) + "." + m.getName(), classType));
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void renderResults(List<SearchResult> results) {
        adapter.setData(results);
        if (results.isEmpty()) {
            resultCount.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.search_no_result);
        } else {
            emptyView.setVisibility(View.GONE);
            resultCount.setVisibility(View.VISIBLE);
            resultCount.setText(getString(R.string.search_result_count, results.size()));
        }
    }

    private void showEmpty(String msg) {
        adapter.clear();
        resultCount.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(msg);
    }

    private DexLoader getLoader() {
        if (getActivity() instanceof MainActivity.Host) return ((MainActivity.Host) getActivity()).getDexLoader();
        return null;
    }
}
