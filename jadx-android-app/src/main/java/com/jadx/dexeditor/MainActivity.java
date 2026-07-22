package com.jadx.dexeditor;

import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.jadx.dexeditor.fragments.BrowseFragment;
import com.jadx.dexeditor.fragments.InfoFragment;
import com.jadx.dexeditor.fragments.SearchFragment;
import com.jadx.dexeditor.fragments.SmaliFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_BROWSE = "browse";
    private static final String TAG_INFO = "info";
    private static final String TAG_SEARCH = "search";

    private Toolbar toolbar;
    private BottomNavigationView bottomNav;
    private BrowseFragment browseFragment;
    private SearchFragment searchFragment;
    private InfoFragment infoFragment;
    private Fragment activeFragment;
    private Thread loadThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        bottomNav = findViewById(R.id.bottom_nav);
        setSupportActionBar(toolbar);
        toolbar.setSubtitle(R.string.toolbar_subtitle);

        getSupportFragmentManager().addOnBackStackChangedListener(this::updateBottomNavVisibility);
        updateBottomNavVisibility();

        if (savedInstanceState == null) {
            setupFragments();
        } else {
            FragmentManager fm = getSupportFragmentManager();
            browseFragment = (BrowseFragment) fm.findFragmentByTag(TAG_BROWSE);
            searchFragment = (SearchFragment) fm.findFragmentByTag(TAG_SEARCH);
            infoFragment = (InfoFragment) fm.findFragmentByTag(TAG_INFO);
            activeFragment = findVisibleMainFragment();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_browse) { switchFragment(browseFragment); return true; }
            if (id == R.id.nav_search) { switchFragment(searchFragment); return true; }
            if (id == R.id.nav_info) { switchFragment(infoFragment); return true; }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_browse);
        }
        updateToolbarTitle();

        // 让 DexLoader 拿到 Context 以便处理 URI
        DexLoader.getInstance().init(getApplicationContext());
    }

    private void setupFragments() {
        browseFragment = new BrowseFragment();
        searchFragment = new SearchFragment();
        infoFragment = new InfoFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, browseFragment, TAG_BROWSE)
                .add(R.id.fragment_container, searchFragment, TAG_SEARCH).hide(searchFragment)
                .add(R.id.fragment_container, infoFragment, TAG_INFO).hide(infoFragment)
                .commitNow();
        activeFragment = browseFragment;
    }

    private void switchFragment(Fragment target) {
        if (target == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
        if (target instanceof InfoFragment) {
            ((InfoFragment) target).refresh();
        }
    }

    private Fragment findVisibleMainFragment() {
        if (browseFragment != null && browseFragment.isVisible()) return browseFragment;
        if (searchFragment != null && searchFragment.isVisible()) return searchFragment;
        if (infoFragment != null && infoFragment.isVisible()) return infoFragment;
        return browseFragment != null ? browseFragment : infoFragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open_file) {
            openFileLauncher.launch("*/*");
            return true;
        }
        if (id == R.id.action_open_folder) {
            openFolderLauncher.launch(null);
            return true;
        }
        if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        TextView message = new TextView(this);
        message.setText(R.string.about_content);
        message.setMovementMethod(LinkMovementMethod.getInstance());
        message.setPadding(64, 48, 64, 0);
        message.setTextSize(14);
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setView(message)
                .setPositiveButton(android.R.string.ok, (DialogInterface.OnClickListener) (d, w) -> d.dismiss())
                .show();
    }

    private void loadFromUri(final Uri uri) {
        if (loadThread != null && loadThread.isAlive()) return;
        if (browseFragment != null) browseFragment.setLoading(true);
        toolbar.setTitle(R.string.loading);
        loadThread = new Thread(() -> {
            try {
                final File loaded = DexLoader.getInstance().loadFromUri(uri);
                final String name = DexLoader.getInstance().getFileName() != null
                        ? DexLoader.getInstance().getFileName()
                        : loaded.getName();
                final int count = DexLoader.getInstance().getClassCount();
                mainHandler.post(() -> onLoaded(name,
                        getString(R.string.loaded_count, count)));
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(() -> onLoadFailed(msg != null ? msg : ""));
            }
        });
        loadThread.start();
    }

    private void loadFromFolder(final Uri treeUri) {
        if (loadThread != null && loadThread.isAlive()) return;
        if (browseFragment != null) browseFragment.setLoading(true);
        toolbar.setTitle(R.string.loading);
        loadThread = new Thread(() -> {
            try {
                List<Uri> dexUris = listDexInTree(treeUri);
                if (dexUris.isEmpty()) {
                    mainHandler.post(() -> onLoadFailed(getString(R.string.no_dex_in_folder)));
                    return;
                }
                List<File> files = new ArrayList<>();
                for (Uri u : dexUris) {
                    files.add(copyUriToCache(u, "dex"));
                }
                int fileCount = files.size();
                int classCount = DexLoader.getInstance().loadMultiple(files);
                final String toast = getString(R.string.loaded_multiple, fileCount, classCount);
                mainHandler.post(() -> onLoaded(toast, toast));
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(() -> onLoadFailed(msg != null ? msg : ""));
            }
        });
        loadThread.start();
    }

    private File copyUriToCache(Uri uri, String ext) throws java.io.IOException {
        String suffix = (ext == null || ext.isEmpty()) ? ".dex" : "." + ext;
        File outFile = File.createTempFile("loaded_", suffix, getCacheDir());
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.OutputStream out = new java.io.FileOutputStream(outFile)) {
            if (in == null) throw new java.io.IOException(getString(R.string.open_failed));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return outFile;
    }

    private List<Uri> listDexInTree(Uri treeUri) {
        List<Uri> result = new ArrayList<>();
        ContentResolver resolver = getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        Cursor cursor = resolver.query(childrenUri,
                new String[]{"document_id", "_display_name"}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    String docId = cursor.getString(0);
                    String name = cursor.getString(1);
                    if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".dex")) {
                        result.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId));
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor.close();
        }
        return result;
    }

    private void onLoaded(String title, String toast) {
        if (isFinishing()) return;
        toolbar.setTitle(title);
        if (browseFragment != null) {
            browseFragment.setLoading(false);
            browseFragment.refresh();
        }
        if (infoFragment != null) infoFragment.refresh();
        if (searchFragment != null) searchFragment.clearResults();
        Fragment visible = findVisibleMainFragment();
        if (visible instanceof InfoFragment) ((InfoFragment) visible).refresh();
        else if (visible instanceof BrowseFragment) ((BrowseFragment) visible).refresh();
        showToast(toast);
    }

    private void onLoadFailed(String message) {
        if (isFinishing()) return;
        toolbar.setTitle(R.string.no_file_selected);
        if (browseFragment != null) {
            browseFragment.setLoading(false);
            browseFragment.refresh();
        }
        showToast(getString(R.string.load_error_prefix) + message);
    }

    private void updateToolbarTitle() {
        if (DexLoader.getInstance().isLoaded() && DexLoader.getInstance().getFileName() != null) {
            toolbar.setTitle(DexLoader.getInstance().getFileName());
        } else {
            toolbar.setTitle(R.string.no_file_selected);
        }
    }

    public void openClass(String classType) {
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .add(R.id.fragment_container, SmaliFragment.newInstance(classType), "smali")
                .addToBackStack(null)
                .commit();
    }

    private void updateBottomNavVisibility() {
        FragmentManager fm = getSupportFragmentManager();
        boolean smaliOnTop = fm.getBackStackEntryCount() > 0;
        bottomNav.setVisibility(smaliOnTop ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadThread != null) {
            loadThread.interrupt();
        }
    }
}
