package com.jadx.dexeditor;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DexLoader {
    private static final String TAG = "DexLoader";
    private final Context context;
    private File loadedFile;
    private MultiDexContainer<? extends DexFile> container;
    private String displayName;

    public DexLoader(Context context) {
        this.context = context;
    }

    public boolean isLoaded() {
        return container != null;
    }

    public File getLoadedFile() {
        return loadedFile;
    }

    public MultiDexContainer<? extends DexFile> getContainer() {
        return container;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void load(File file) throws Exception {
        Log.i(TAG, "Loading file: " + file + " size=" + file.length());
        String name = file.getName().toLowerCase(Locale.US);
        MultiDexContainer<? extends DexFile> c;
        try {
            c = DexFileFactory.loadDexContainer(file, (Opcodes) null);
            List<String> entries = c.getDexEntryNames();
            Log.i(TAG, "loadDexContainer OK, entries=" + entries);
            if (entries.isEmpty()) {
                throw new IllegalStateException("File contains no DEX entries: " + file.getName());
            }
            // 修正 entry 名：单 .dex 文件被加载时 entry 名是绝对路径，改为简单文件名
            if (entries.size() == 1 && name.endsWith(".dex")) {
                MultiDexContainer.DexEntry<? extends DexFile> e = c.getEntry(entries.get(0));
                if (e != null) {
                    c = new SingleDexContainer(e.getDexFile(), file.getName());
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "loadDexContainer failed for " + name + ", trying loadDexFile", ex);
            if (name.endsWith(".dex")) {
                DexFile df = DexFileFactory.loadDexFile(file, (Opcodes) null);
                c = new SingleDexContainer(df, file.getName());
            } else {
                throw ex;
            }
        }
        synchronized (this) {
            this.loadedFile = file;
            this.container = c;
            this.displayName = file.getName();
        }
    }

    public void loadFromUri(Uri uri) throws Exception {
        Log.i(TAG, "loadFromUri: " + uri);
        File tmp = copyUriToTemp(uri);
        Log.i(TAG, "Copied to temp file: " + tmp + " size=" + tmp.length());
        load(tmp);
    }

    public void loadMultipleUris(List<Uri> uris) throws Exception {
        if (uris == null || uris.isEmpty()) {
            throw new IllegalArgumentException("No URIs to load");
        }
        if (uris.size() == 1) {
            loadFromUri(uris.get(0));
            return;
        }
        File tmpDir = new File(context.getCacheDir(), "multi_dex_" + System.currentTimeMillis());
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            throw new IllegalStateException("Cannot create temp dir: " + tmpDir);
        }
        List<File> files = new ArrayList<>();
        for (int i = 0; i < uris.size(); i++) {
            File src = copyUriToTemp(uris.get(i));
            File renamed = new File(tmpDir, String.format(Locale.US, "merged-%04d.dex", i));
            if (!src.renameTo(renamed)) {
                renamed = src;
            }
            files.add(renamed);
        }
        MultiFileContainer mc = new MultiFileContainer(files);
        synchronized (this) {
            this.loadedFile = tmpDir;
            this.container = mc;
            this.displayName = files.size() + " DEX files";
        }
    }

    private File copyUriToTemp(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) {
            name = "input.dex";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        String suffix = dot >= 0 ? name.substring(dot) : ".dex";
        String base = dot >= 0 ? name.substring(0, dot) : name;
        if (base.isEmpty()) {
            base = "input";
        }
        // 清理 base 中的特殊字符，避免 createTempFile 抛异常
        base = base.replaceAll("[^A-Za-z0-9_-]", "_");
        File tmp = File.createTempFile(base + "-", suffix, context.getCacheDir());
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(tmp)) {
            if (is == null) {
                throw new IllegalStateException("Cannot open input stream for " + uri);
            }
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
            }
            Log.i(TAG, "Copy " + uri + " -> " + tmp + " (" + total + " bytes)");
        }
        return tmp;
    }

    public static class SingleDexContainer implements MultiDexContainer<DexFile> {
        private final DexFile dexFile;
        private final String entryName;

        public SingleDexContainer(DexFile dexFile, String entryName) {
            this.dexFile = dexFile;
            this.entryName = entryName;
        }

        @Override
        public List<String> getDexEntryNames() {
            List<String> list = new ArrayList<>();
            list.add(entryName);
            return list;
        }

        @Override
        public DexEntry<DexFile> getEntry(String entryName) {
            if (!this.entryName.equals(entryName)) {
                return null;
            }
            return new DexEntry<DexFile>() {
                @Override
                public String getEntryName() {
                    return SingleDexContainer.this.entryName;
                }

                @Override
                public DexFile getDexFile() {
                    return dexFile;
                }

                @Override
                public MultiDexContainer<DexFile> getContainer() {
                    return SingleDexContainer.this;
                }
            };
        }
    }

    public static class MultiFileContainer implements MultiDexContainer<DexFile> {
        private final List<String> entryNames = new ArrayList<>();
        private final List<DexFile> dexFiles = new ArrayList<>();

        public MultiFileContainer(List<File> files) throws Exception {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.US);
                try {
                    MultiDexContainer<? extends DexFile> c =
                            DexFileFactory.loadDexContainer(f, (Opcodes) null);
                    List<String> names = c.getDexEntryNames();
                    if (names.isEmpty() && n.endsWith(".dex")) {
                        DexFile df = DexFileFactory.loadDexFile(f, (Opcodes) null);
                        entryNames.add(f.getName());
                        dexFiles.add(df);
                    } else {
                        for (String nm : names) {
                            DexEntry<? extends DexFile> e = c.getEntry(nm);
                            if (e != null) {
                                entryNames.add(nm);
                                dexFiles.add(e.getDexFile());
                            }
                        }
                    }
                } catch (Exception ex) {
                    Log.w("MultiFileContainer", "load " + f + " failed", ex);
                    if (n.endsWith(".dex")) {
                        DexFile df = DexFileFactory.loadDexFile(f, (Opcodes) null);
                        entryNames.add(f.getName());
                        dexFiles.add(df);
                    } else {
                        throw ex;
                    }
                }
            }
        }

        @Override
        public List<String> getDexEntryNames() {
            return new ArrayList<>(entryNames);
        }

        @Override
        public DexEntry<DexFile> getEntry(String entryName) {
            int idx = entryNames.indexOf(entryName);
            if (idx < 0) {
                return null;
            }
            final DexFile dexFile = dexFiles.get(idx);
            final String name = entryNames.get(idx);
            return new DexEntry<DexFile>() {
                @Override
                public String getEntryName() {
                    return name;
                }

                @Override
                public DexFile getDexFile() {
                    return dexFile;
                }

                @Override
                public MultiDexContainer<DexFile> getContainer() {
                    return MultiFileContainer.this;
                }
            };
        }
    }
}
