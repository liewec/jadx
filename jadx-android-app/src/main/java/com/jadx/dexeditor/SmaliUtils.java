package com.jadx.dexeditor;

import android.util.Log;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

public final class SmaliUtils {
    private static final String TAG = "SmaliUtils";

    private SmaliUtils() {
    }

    public static String disassemble(ClassDef classDef) throws IOException {
        BaksmaliOptions options = new BaksmaliOptions();
        ClassDefinition classDefinition = new ClassDefinition(options, classDef);
        StringWriter sw = new StringWriter();
        BaksmaliWriter writer = new BaksmaliWriter(sw);
        classDefinition.writeTo(writer);
        writer.flush();
        return sw.toString();
    }

    public static String decompileToJava(File dexFile, String classType) {
        if (dexFile == null || !dexFile.exists()) {
            return "// 无法反编译：未加载文件";
        }
        String fullName = classType;
        if (fullName.startsWith("L") && fullName.endsWith(";")) {
            fullName = fullName.substring(1, fullName.length() - 1).replace('/', '.');
        }
        final String target = fullName;
        final String targetNormalized = fullName.replace('$', '.');
        JadxArgs args = new JadxArgs();
        args.setSkipResources(true);
        args.setShowInconsistentCode(true);
        args.setFallbackMode(true);
        args.addInputFile(dexFile);
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            // 先在顶层类中查找
            for (JavaClass cls : jadx.getClasses()) {
                String name = cls.getFullName();
                if (name != null) {
                    if (name.equals(target) || name.replace('$', '.').equals(targetNormalized)) {
                        cls.decompile();
                        String code = cls.getCode();
                        if (code != null && !code.isEmpty()) {
                            return code;
                        }
                    }
                }
            }
            // 再在内部类中查找
            for (JavaClass cls : jadx.getClasses()) {
                for (JavaClass inner : cls.getInnerClasses()) {
                    String name = inner.getFullName();
                    if (name != null) {
                        if (name.equals(target) || name.replace('$', '.').equals(targetNormalized)) {
                            inner.decompile();
                            String code = inner.getCode();
                            if (code != null && !code.isEmpty()) {
                                return code;
                            }
                        }
                    }
                }
            }
            return "// 无法反编译此类\n// 未在 DEX 中找到类：" + target;
        } catch (Throwable e) {
            Log.w(TAG, "jadx decompilation failed for " + classType, e);
            return "// 反编译失败：" + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public static boolean compile(String smaliCode, String outputDexFile) throws IOException {
        File outFile = new File(outputDexFile);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Could not create output directory: " + parent);
        }
        File tmpSmali = File.createTempFile("dexeditor_", ".smali");
        try {
            try (FileWriter fw = new FileWriter(tmpSmali)) {
                fw.write(smaliCode);
            }
            SmaliOptions options = new SmaliOptions();
            options.outputDexFile = outputDexFile;
            options.apiLevel = 28;
            return Smali.assemble(options, tmpSmali.getAbsolutePath());
        } finally {
            if (!tmpSmali.delete()) {
                tmpSmali.deleteOnExit();
            }
        }
    }
}
