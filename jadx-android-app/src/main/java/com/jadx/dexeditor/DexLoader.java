package com.jadx.dexeditor;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.jadx.dexeditor.model.ClassNode;
import com.jadx.dexeditor.model.SearchResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DexLoader {
    private static final String TAG = "DexLoader";
    private static volatile DexLoader instance;

    private final Object lock = new Object();
    private final List<DexBackedDexFile> dexFiles = new ArrayList<>();
    private final List<ClassDef> classes = new ArrayList<>();
    private final Map<String, ClassDef> classMap = new LinkedHashMap<>();

    private volatile boolean loaded = false;
    private volatile String fileName;
    private volatile long fileSize;
    private volatile File loadedFile;
    private volatile int dexCount;
    private volatile int methodCount;
    private volatile int fieldCount;
    private volatile int stringCount;
    private volatile int packageCount;
    private volatile ClassNode root;

    private Context context;

    private DexLoader() {
    }

    public static DexLoader getInstance() {
        if (instance == null) {
            synchronized (DexLoader.class) {
                if (instance == null) {
                    instance = new DexLoader();
                }
            }
        }
        return instance;
    }

    /** 注入 Context 用于 URI 复制，可空 */
    public void init(Context ctx) {
        this.context = ctx != null ? ctx.getApplicationContext() : null;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getDexCount() {
        return dexCount;
    }

    public File getLoadedFile() {
        return loadedFile;
    }

    public List<ClassDef> getClasses() {
        synchronized (lock) {
            return classes;
        }
    }

    public ClassNode getRoot() {
        return root;
    }

    public int getMethodCount() {
        return methodCount;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public int getStringCount() {
        return stringCount;
    }

    public int getPackageCount() {
        return packageCount;
    }

    public int getClassCount() {
        synchronized (lock) {
            return classes.size();
        }
    }

    public ClassDef findClass(String type) {
        synchronized (lock) {
            return classMap.get(type);
        }
    }

    public void clear() {
        synchronized (lock) {
            dexFiles.clear();
            classes.clear();
            classMap.clear();
        }
        root = null;
        fileName = null;
        fileSize = 0L;
        dexCount = 0;
        loadedFile = null;
        methodCount = 0;
        fieldCount = 0;
        stringCount = 0;
        packageCount = 0;
        loaded = false;
    }

    public int load(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist");
        }
        fileName = file.getName();
        fileSize = file.length();
        loadedFile = file;
        List<DexBackedDexFile> loadedDex = new ArrayList<>();
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".dex")) {
            DexBackedDexFile dex = DexFileFactory.loadDexFile(file, Opcodes.getDefault());
            loadedDex.add(dex);
        } else {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(file, Opcodes.getDefault());
            List<String> entryNames = container.getDexEntryNames();
            for (String entryName : entryNames) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
                if (entry != null) {
                    loadedDex.add(entry.getDexFile());
                }
            }
        }
        if (loadedDex.isEmpty()) {
            throw new IOException("No DEX data found in " + file.getName());
        }
        synchronized (lock) {
            dexFiles.clear();
            classes.clear();
            classMap.clear();
            dexFiles.addAll(loadedDex);
            for (DexBackedDexFile dex : loadedDex) {
                for (ClassDef cls : dex.getClasses()) {
                    if (classMap.put(cls.getType(), cls) == null) {
                        classes.add(cls);
                    }
                }
            }
        }
        dexCount = loadedDex.size();
        computeStatistics();
        root = buildTree();
        loaded = true;
        return classes.size();
    }

    public int loadMultiple(List<File> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IOException("No files to load");
        }
        List<DexBackedDexFile> loadedDex = new ArrayList<>();
        long totalSize = 0;
        for (File file : files) {
            if (file != null && file.exists()) {
                totalSize += file.length();
                loadedDex.addAll(loadDexFromFile(file));
            }
        }
        if (loadedDex.isEmpty()) {
            throw new IOException("No dex data found in selected files");
        }
        fileName = files.size() + " files";
        fileSize = totalSize;
        dexCount = files.size();
        loadedFile = files.get(0);
        synchronized (lock) {
            dexFiles.clear();
            classes.clear();
            classMap.clear();
            dexFiles.addAll(loadedDex);
            for (DexBackedDexFile dex : loadedDex) {
                for (ClassDef cls : dex.getClasses()) {
                    if (classMap.put(cls.getType(), cls) == null) {
                        classes.add(cls);
                    }
                }
            }
        }
        computeStatistics();
        root = buildTree();
        loaded = true;
        return classes.size();
    }

    private List<DexBackedDexFile> loadDexFromFile(File file) throws IOException {
        List<DexBackedDexFile> result = new ArrayList<>();
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".dex")) {
            result.add(DexFileFactory.loadDexFile(file, Opcodes.getDefault()));
        } else {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(file, Opcodes.getDefault());
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
                if (entry != null) {
                    result.add(entry.getDexFile());
                }
            }
        }
        return result;
    }

    public File loadFromUri(Uri uri) throws IOException {
        if (context == null) {
            throw new IllegalStateException("DexLoader not initialized with Context");
        }
        String displayName = queryDisplayName(uri);
        String ext = extractExtension(displayName);
        File cacheFile = copyToCache(uri, ext);
        load(cacheFile);
        if (displayName != null) {
            fileName = displayName;
        }
        return cacheFile;
    }

    public List<File> loadMultipleFromUris(List<Uri> uris) throws IOException {
        if (uris == null || uris.isEmpty()) {
            throw new IOException("No URIs to load");
        }
        if (uris.size() == 1) {
            loadFromUri(uris.get(0));
            List<File> one = new ArrayList<>();
            one.add(loadedFile);
            return one;
        }
        List<File> files = new ArrayList<>();
        for (Uri u : uris) {
            files.add(copyToCache(u, "dex"));
        }
        loadMultiple(files);
        return files;
    }

    private File copyToCache(Uri uri, String ext) throws IOException {
        String suffix = (ext == null || ext.isEmpty()) ? ".dex" : "." + ext;
        File outFile = File.createTempFile("loaded_", suffix, context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(outFile)) {
            if (in == null) {
                throw new IOException("Cannot open input stream for " + uri);
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return outFile;
    }

    private String queryDisplayName(Uri uri) {
        int idx;
        String result = null;
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                idx = cursor.getColumnIndex("_display_name");
                if (idx >= 0) {
                    result = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        if (result == null) {
            return uri.getLastPathSegment();
        }
        return result;
    }

    private static String extractExtension(String name) {
        int dot;
        if (name == null || (dot = name.lastIndexOf('.')) < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void computeStatistics() {
        methodCount = 0;
        fieldCount = 0;
        Set<String> strings = new HashSet<>();
        for (ClassDef cls : classes) {
            for (Method m : cls.getMethods()) {
                methodCount++;
                MethodImplementation impl = m.getImplementation();
                if (impl != null) {
                    for (Instruction insn : impl.getInstructions()) {
                        if (insn instanceof ReferenceInstruction) {
                            Reference ref = ((ReferenceInstruction) insn).getReference();
                            if (ref instanceof StringReference) {
                                strings.add(((StringReference) ref).getString());
                            }
                        }
                    }
                }
            }
            for (Field field : cls.getFields()) {
                fieldCount++;
            }
        }
        for (DexBackedDexFile dex : dexFiles) {
            try {
                for (StringReference sr : dex.getStringReferences()) {
                    strings.add(sr.getString());
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not read string pool", e);
            }
        }
        stringCount = strings.size();
        packageCount = countPackages();
    }

    private int countPackages() {
        Set<String> packages = new HashSet<>();
        for (ClassDef cls : classes) {
            String type = cls.getType();
            String inner = stripType(type);
            if (inner != null) {
                int slash = inner.lastIndexOf('/');
                if (slash > 0) {
                    packages.add(inner.substring(0, slash));
                }
            }
        }
        return packages.size();
    }

    private ClassNode buildTree() {
        ClassNode rootNode = new ClassNode(ClassNode.TYPE_PACKAGE, "", null, null);
        for (ClassDef cls : classes) {
            String type = cls.getType();
            String inner = stripType(type);
            if (inner != null) {
                String[] parts = inner.split("/");
                ClassNode current = rootNode;
                for (int i = 0; i < parts.length; i++) {
                    if (i == parts.length - 1) {
                        current.getChildren().add(new ClassNode(ClassNode.TYPE_CLASS, parts[i], type, current));
                    } else {
                        current = findOrCreatePackage(current, parts[i]);
                    }
                }
            }
        }
        sortTree(rootNode);
        return rootNode;
    }

    private ClassNode findOrCreatePackage(ClassNode parent, String name) {
        for (ClassNode child : parent.getChildren()) {
            if (child.isPackage() && child.getName().equals(name)) {
                return child;
            }
        }
        ClassNode pkg = new ClassNode(ClassNode.TYPE_PACKAGE, name, null, parent);
        parent.getChildren().add(pkg);
        return pkg;
    }

    private void sortTree(ClassNode node) {
        node.getChildren().sort(new Comparator<ClassNode>() {
            @Override
            public int compare(ClassNode a, ClassNode b) {
                if (a.isPackage() != b.isPackage()) {
                    return a.isPackage() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (ClassNode child : node.getChildren()) {
            if (child.isPackage()) {
                sortTree(child);
            }
        }
    }

    private static String stripType(String type) {
        if (type == null || type.length() < 2 || type.charAt(0) != 'L') {
            return null;
        }
        int end = type.length() - 1;
        if (type.charAt(end) != ';') {
            end = type.length();
        }
        return type.substring(1, end);
    }

    public List<SearchResult> search(int kind, String keyword) {
        List<SearchResult> results = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) {
            return results;
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        synchronized (lock) {
            if (kind == SearchResult.KIND_CLASS) {
                for (ClassDef cls : classes) {
                    String readable = toReadableClassName(cls.getType());
                    if (readable.toLowerCase(Locale.ROOT).contains(needle)
                            || cls.getType().toLowerCase(Locale.ROOT).contains(needle)) {
                        results.add(new SearchResult(SearchResult.KIND_CLASS,
                                readable, cls.getType(), cls.getType()));
                    }
                }
            } else if (kind == SearchResult.KIND_METHOD) {
                Set<String> seen = new HashSet<>();
                for (ClassDef cls : classes) {
                    String owner = toReadableClassName(cls.getType());
                    for (Method m : cls.getMethods()) {
                        if (m.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                            String key = cls.getType() + m.getName() + buildMethodSig(m);
                            if (seen.add(key)) {
                                results.add(new SearchResult(SearchResult.KIND_METHOD,
                                        m.getName(), owner + "\n" + buildMethodSig(m), cls.getType()));
                            }
                        }
                    }
                }
            } else if (kind == SearchResult.KIND_STRING) {
                Set<String> seen = new HashSet<>();
                for (ClassDef cls : classes) {
                    String owner = toReadableClassName(cls.getType());
                    for (Method m : cls.getMethods()) {
                        MethodImplementation impl = m.getImplementation();
                        if (impl == null) continue;
                        for (Instruction insn : impl.getInstructions()) {
                            if (insn instanceof ReferenceInstruction) {
                                Reference ref = ((ReferenceInstruction) insn).getReference();
                                if (ref instanceof StringReference) {
                                    String s = ((StringReference) ref).getString();
                                    if (s.toLowerCase(Locale.ROOT).contains(needle)) {
                                        String key = cls.getType() + s;
                                        if (seen.add(key)) {
                                            String display = s.length() > 200 ? s.substring(0, 200) + "…" : s;
                                            results.add(new SearchResult(SearchResult.KIND_STRING,
                                                    display, owner, cls.getType()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    private static String buildMethodSig(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName()).append("(");
        List<? extends MethodParameter> params = m.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getType());
        }
        sb.append(") : ").append(m.getReturnType());
        return sb.toString();
    }

    public static String toReadableClassName(String type) {
        if (type == null) return "";
        String inner = stripType(type);
        if (inner == null) return type;
        return inner.replace('/', '.').replace('$', '.');
    }
}
