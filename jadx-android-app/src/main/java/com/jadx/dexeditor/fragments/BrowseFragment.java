package com.jadx.dexeditor.fragments;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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

import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.MainActivity;
import com.jadx.dexeditor.R;
import com.jadx.dexeditor.adapter.ClassTreeAdapter;
import com.jadx.dexeditor.model.ClassNode;

public class BrowseFragment extends Fragment implements ClassTreeAdapter.OnNodeClickListener {
    private ClassTreeAdapter adapter;
    private TextView emptyText;
    private View progressBar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new ClassTreeAdapter(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse, container, false);
        emptyText = view.findViewById(R.id.empty_text);
        progressBar = view.findViewById(R.id.loading_progress);
        RecyclerView recycler = view.findViewById(R.id.tree_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        ClassNode root = DexLoader.getInstance().getRoot();
        adapter.setRoot(root);
        updateEmptyState(false);
    }

    public void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        updateEmptyState(loading);
    }

    private void updateEmptyState(boolean loading) {
        if (emptyText == null) return;
        boolean empty = !loading
                && (DexLoader.getInstance().getRoot() == null
                || DexLoader.getInstance().getClassCount() == 0);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClassClicked(String classType) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openClass(classType);
        }
    }

    @Override
    public boolean onClassLongClicked(String classType, String displayName) {
        new AlertDialog.Builder(requireContext())
                .setTitle(displayName)
                .setItems(new CharSequence[]{getString(R.string.copy_name)}, (dialog, which) ->
                        copyToClipboard(displayName))
                .show();
        return true;
    }

    private void copyToClipboard(String text) {
        Context ctx = getContext();
        if (ctx == null) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("class", text));
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showToast(getString(R.string.copied_to_clipboard));
        }
    }
}
