package com.jadx.dexeditor;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.jadx.dexeditor.fragments.BrowseFragment;
import com.jadx.dexeditor.fragments.InfoFragment;
import com.jadx.dexeditor.fragments.SearchFragment;
import com.jadx.dexeditor.fragments.SmaliFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /** 对话框内显示的最大字符数，超过则截断（避免大文本布局/渲染卡死） */
    private static final int MAX_DIALOG_TEXT = 6000;

    public interface Host {
        DexLoader getDexLoader();
        void openClass(String classType);
    }

    private Toolbar toolbar;
    private BottomNavigationView bottomNav;
    private final DexLoader dexLoader = new DexLoader(this);
    private BrowseFragment browseFragment;
    private SearchFragment searchFragment;
    private InfoFragment infoFragment;
    private Fragment activeMainFragment;

    private final ActivityResultLauncher<String> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadFromUri(uri);
            });

    private final ActivityResultLauncher<Uri> openFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) loadFromFolder(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_browse) showMainTab(getOrCreateBrowse(), "browse");
            else if (id == R.id.nav_search) showMainTab(getOrCreateSearch(), "search");
            else if (id == R.id.nav_info) showMainTab(getOrCreateInfo(), "info");
            return true;
        });
        if (savedInstanceState == null) {
            browseFragment = new BrowseFragment();
            searchFragment = new SearchFragment();
            infoFragment = new InfoFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, infoFragment, "info").hide(infoFragment)
                    .add(R.id.fragment_container, searchFragment, "search").hide(searchFragment)
                    .add(R.id.fragment_container, browseFragment, "browse")
                    .commitNow();
            activeMainFragment = browseFragment;
        } else {
            FragmentManager fm = getSupportFragmentManager();
            browseFragment = (BrowseFragment) fm.findFragmentByTag("browse");
            searchFragment = (SearchFragment) fm.findFragmentByTag("search");
            infoFragment = (InfoFragment) fm.findFragmentByTag("info");
            activeMainFragment = findVisibleMainFragment();
        }
        // 处理通过 VIEW intent 打开的文件
        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if (intent != null) {
                Uri data = intent.getData();
                if (data != null) {
                    loadFromUri(data);
                }
            }
        }
    }

    private Fragment findVisibleMainFragment() {
        if (browseFragment != null && browseFragment.isVisible()) return browseFragment;
        if (searchFragment != null && searchFragment.isVisible()) return searchFragment;
        if (infoFragment != null && infoFragment.isVisible()) return infoFragment;
        return browseFragment;
    }

    private BrowseFragment getOrCreateBrowse() {
        if (browseFragment == null) browseFragment = new BrowseFragment();
        return browseFragment;
    }

    private SearchFragment getOrCreateSearch() {
        if (searchFragment == null) searchFragment = new SearchFragment();
        return searchFragment;
    }

    private InfoFragment getOrCreateInfo() {
        if (infoFragment == null) infoFragment = new InfoFragment();
        return infoFragment;
    }

    private void showMainTab(Fragment target, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment existing = fm.findFragmentByTag(tag);
        FragmentTransaction ft = fm.beginTransaction();
        if (browseFragment != null && browseFragment != existing) ft.hide(browseFragment);
        if (searchFragment != null && searchFragment != existing) ft.hide(searchFragment);
        if (infoFragment != null && infoFragment != existing) ft.hide(infoFragment);
        SmaliFragment smaliFrag = (SmaliFragment) fm.findFragmentByTag("smali");
        if (smaliFrag != null) ft.remove(smaliFrag);
        if (existing == null) ft.add(R.id.fragment_container, target, tag);
        else ft.show(existing);
        ft.commitAllowingStateLoss();
        activeMainFragment = existing == null ? target : existing;
        if (activeMainFragment instanceof BrowseFragment) ((BrowseFragment) activeMainFragment).refresh();
        else if (activeMainFragment instanceof InfoFragment) ((InfoFragment) activeMainFragment).refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open_file) { openFileLauncher.launch("*/*"); return true; }
        else if (id == R.id.action_open_folder) { openFolderLauncher.launch(null); return true; }
        else if (id == R.id.action_reload) { reloadCurrent(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void reloadCurrent() {
        if (!dexLoader.isLoaded()) {
            Toast.makeText(this, R.string.no_file_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        File f = dexLoader.getLoadedFile();
        if (f != null && f.exists()) {
            loadFileAsync(() -> {
                try { dexLoader.load(f); } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
    }

    private void loadFromUri(Uri uri) {
        loadFileAsync(() -> {
            try { dexLoader.loadFromUri(uri); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private void loadFromFolder(Uri folderUri) {
        loadFileAsync(() -> {
            List<Uri> dexUris = listDexInTree(folderUri);
            if (dexUris.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, R.string.no_dex_in_folder, Toast.LENGTH_SHORT).show());
                return;
            }
            try { dexLoader.loadMultipleUris(dexUris); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private List<Uri> listDexInTree(Uri treeUri) {
        List<Uri> result = new ArrayList<>();
        try {
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            android.database.Cursor cursor = getContentResolver().query(
                    childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        String docId = cursor.getString(0);
                        String name = cursor.getString(1);
                        if (name != null && name.toLowerCase().endsWith(".dex")) {
                            result.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId));
                        }
                    }
                } finally { cursor.close(); }
            }
        } catch (Throwable e) {
            Toast.makeText(this, "list dex failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return result;
    }

    private void loadFileAsync(Runnable task) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.loading));
        pd.setCancelable(true);
        pd.show();
        new Thread(() -> {
            String error = null;
            String fullTrace = null;
            try {
                task.run();
            } catch (Throwable e) {
                fullTrace = formatError(e);
                error = extractSummary(e);
                DexEditorApp.setLastError(fullTrace);
                android.util.Log.e("DexEditor", "loadFileAsync failed", e);
            }
            final String finalError = error;
            final String finalTrace = fullTrace;
            runOnUiThread(() -> {
                try {
                    if (pd.isShowing()) pd.dismiss();
                } catch (Throwable ignored) {}
                if (finalError != null) {
                    showErrorDialog(finalError, finalTrace);
                    return;
                }
                try {
                    onLoaded();
                } catch (Throwable t) {
                    String trace = formatError(t);
                    DexEditorApp.setLastError("onLoaded crashed:\n" + trace);
                    showErrorDialog(extractSummary(t), trace);
                }
            });
        }).start();
    }

    private static String formatError(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        sb.append("Root cause: ").append(root.getClass().getName())
                .append(": ").append(root.getMessage()).append("\n\n");
        sb.append("Full stack trace:\n");
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        sb.append(sw.toString());
        return sb.toString();
    }

    private static String extractSummary(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        if (msg == null) msg = root.getClass().getName();
        if (msg == null) msg = e.toString();
        return msg;
    }

    /** 在手机上直接显示错误对话框，不用 adb 也能看到完整堆栈 */
    private void showErrorDialog(String summary, String fullTrace) {
        // 注意：不要在这里再写 setLastError，loadFileAsync 已写过，重复写会触发 SharedPreferences 同步开销
        final String displayText;
        final String copyText = fullTrace != null ? fullTrace : summary;
        if (copyText != null && copyText.length() > MAX_DIALOG_TEXT) {
            displayText = copyText.substring(0, MAX_DIALOG_TEXT)
                    + "\n\n…（已截断 " + (copyText.length() - MAX_DIALOG_TEXT)
                    + " 字符，完整内容请点“复制”）";
        } else {
            displayText = copyText;
        }
        ScrollView scroll = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setPadding(48, 32, 48, 32);
        // setTextIsSelectable(true) 在大文本上会显著拖慢布局测量，这里关闭
        tv.setText(displayText);
        scroll.addView(tv);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.load_failed, ""))
                .setMessage(summary)
                .setView(scroll)
                .setPositiveButton("保存到文件", (d, w) -> {
                    // 在子线程写文件，避免大文本 IO 阻塞 UI
                    final String textToSave = copyText;
                    new Thread(() -> {
                        final File saved = DexEditorApp.saveErrorLogToFile(textToSave);
                        runOnUiThread(() -> {
                            if (saved != null) {
                                Toast.makeText(this,
                                        "已保存：" + saved.getAbsolutePath(),
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this,
                                        "保存失败（无法写入存储）",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("关闭", null)
                .setCancelable(true)
                .show();
    }

    private void onLoaded() {
        SmaliUtils.clearCache();
        if (searchFragment != null) searchFragment.clearResults();
        // 加载完成后无论当前 Tab 是哪个，都强制刷新 browse 和 info，
        // 否则当 activeMainFragment 不是 BrowseFragment 时类列表不会刷新。
        android.util.Log.i("MainActivity", "onLoaded: browseFragment="
                + (browseFragment != null) + " infoFragment=" + (infoFragment != null)
                + " isLoaded=" + dexLoader.isLoaded()
                + " container=" + (dexLoader.getContainer() != null));
        if (browseFragment != null) browseFragment.refresh();
        if (infoFragment != null) infoFragment.refresh();
        toolbar.setSubtitle(dexLoader.getDisplayName());
        // 诊断 Toast：让用户在手机上也能看到加载状态
        int entries = 0;
        if (dexLoader.getContainer() != null) {
            try {
                entries = dexLoader.getContainer().getDexEntryNames().size();
            } catch (Throwable ignored) {}
        }
        Toast.makeText(this,
                "Loaded: " + dexLoader.getDisplayName()
                        + " (entries=" + entries + ")",
                Toast.LENGTH_LONG).show();
    }

    public DexLoader getDexLoader() { return dexLoader; }

    public void openClass(String classType) {
        if (classType == null) return;
        SmaliFragment frag = new SmaliFragment();
        Bundle args = new Bundle();
        args.putString(SmaliFragment.ARG_CLASS_TYPE, classType);
        frag.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .hide(activeMainFragment == null ? browseFragment : activeMainFragment)
                .add(R.id.fragment_container, frag, "smali")
                .addToBackStack("smali")
                .commitAllowingStateLoss();
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        SmaliFragment smaliFrag = (SmaliFragment) fm.findFragmentByTag("smali");
        if (smaliFrag != null && smaliFrag.isVisible()) {
            fm.popBackStack("smali", FragmentManager.POP_BACK_STACK_INCLUSIVE);
            if (activeMainFragment != null) fm.beginTransaction().show(activeMainFragment).commitAllowingStateLoss();
            return;
        }
        super.onBackPressed();
    }
}
