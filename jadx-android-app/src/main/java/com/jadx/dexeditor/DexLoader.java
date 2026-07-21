package com.jadx.dexeditor;

import android.content.Context;
import android.net.Uri;

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
        MultiDexContainer<? extends DexFile> c = DexFileFactory.loadDexContainer(file, (Opcodes) null);
        synchronized (this) {
            this.loadedFile = file;
            this.container = c;
            this.displayName = file.getName();
        }
    }

    public void loadFromUri(Uri uri) throws Exception {
        File tmp = copyUriToTemp(uri);
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
        try {
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
        } catch (Exception e) {
            throw e;
        }
    }

    private File copyUriToTemp(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) {
            name = "input.dex";
        }
        int dot = name.lastIndexOf('.');
        String suffix = dot >= 0 ? name.substring(dot) : ".dex";
        String base = dot >= 0 ? name.substring(0, dot) : name;
        if (base.isEmpty()) {
            base = "input";
        }
        File tmp = File.createTempFile(base + "-", suffix, context.getCacheDir());
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(tmp)) {
            if (is == null) {
                throw new IllegalStateException("Cannot open input stream for " + uri);
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
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
                MultiDexContainer<? extends DexFile> c =
                        DexFileFactory.loadDexContainer(f, (Opcodes) null);
                List<String> names = c.getDexEntryNames();
                if (names.isEmpty()) {
                    String n = f.getName();
                    entryNames.add(n);
                    dexFiles.add(null);
                } else {
                    for (String nm : names) {
                        DexEntry<? extends DexFile> e = c.getEntry(nm);
                        if (e != null) {
                            entryNames.add(nm);
                            dexFiles.add(e.getDexFile());
                        }
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
