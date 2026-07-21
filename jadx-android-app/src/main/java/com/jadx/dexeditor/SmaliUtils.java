package com.jadx.dexeditor;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SmaliUtils {
    private static final ConcurrentHashMap<String, String> DISASM_CACHE = new ConcurrentHashMap<>();

    private SmaliUtils() {
    }

    public static String disassembleClass(ClassDef classDef) {
        if (classDef == null) {
            return "";
        }
        String cacheKey = classDef.getType();
        if (cacheKey != null) {
            String cached = DISASM_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        BaksmaliOptions options = new BaksmaliOptions();
        StringWriter sw = new StringWriter();
        try (BaksmaliWriter writer = new BaksmaliWriter(sw)) {
            ClassDefinition classDefinition = new ClassDefinition(options, classDef);
            classDefinition.writeTo(writer);
        } catch (Exception e) {
            return "/* disassemble failed: " + e.getMessage() + " */";
        }
        String result = sw.toString();
        if (cacheKey != null) {
            DISASM_CACHE.put(cacheKey, result);
        }
        return result;
    }

    public static boolean assembleSmali(File srcFile, File outFile) {
        SmaliOptions options = new SmaliOptions();
        options.apiLevel = 28;
        options.outputDexFile = outFile.getAbsolutePath();
        try {
            return Smali.assemble(options, srcFile.getAbsolutePath());
        } catch (Throwable e) {
            return false;
        }
    }

    public static String descriptorToJavaName(String descriptor) {
        if (descriptor == null) {
            return "";
        }
        String s = descriptor;
        if (s.startsWith("L") && s.endsWith(";") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace('/', '.');
    }

    public static String simpleName(String typeDescriptor) {
        if (typeDescriptor == null) {
            return "";
        }
        String s = typeDescriptor;
        if (s.startsWith("L") && s.endsWith(";") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        int idx = s.lastIndexOf('/');
        if (idx >= 0) {
            s = s.substring(idx + 1);
        }
        int dollar = s.lastIndexOf('$');
        if (dollar >= 0) {
            s = s.substring(dollar + 1);
        }
        return s;
    }

    public static int countMethods(ClassDef cd) {
        if (cd == null) {
            return 0;
        }
        int count = 0;
        for (Method m : cd.getMethods()) {
            count++;
        }
        return count;
    }

    public static int countFields(ClassDef cd) {
        if (cd == null) {
            return 0;
        }
        int count = 0;
        for (Field f : cd.getFields()) {
            count++;
        }
        return count;
    }

    public static void clearCache() {
        DISASM_CACHE.clear();
    }

    public static List<String> collectStrings(ClassDef cd) {
        List<String> result = new ArrayList<>();
        if (cd == null) {
            return result;
        }
        for (Method m : cd.getMethods()) {
            MethodImplementation impl = m.getImplementation();
            if (impl == null) {
                continue;
            }
            for (Instruction inst : impl.getInstructions()) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        String s = ((StringReference) ref).getString();
                        if (s != null) {
                            result.add(s);
                        }
                    }
                }
            }
        }
        return result;
    }
}
