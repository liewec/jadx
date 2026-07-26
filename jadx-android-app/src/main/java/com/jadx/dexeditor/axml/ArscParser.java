package com.jadx.dexeditor.axml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 resources.arsc 解析器：仅提取 (resourceId → package:type/name) 映射，
 * 用于把 @7F010000 还原为 @string/app_name。
 * <p>
 * 参考 AOSP frameworks/base/libs/androidfw/ResourceTypes.cpp。
 */
public final class ArscParser {

    public static final int RES_TABLE_TYPE = 0x0002;
    public static final int RES_STRING_POOL_TYPE = 0x0001;
    public static final int RES_TABLE_PACKAGE_TYPE = 0x0200;
    public static final int RES_TABLE_TYPE_TYPE = 0x0201;
    public static final int RES_TABLE_TYPE_SPEC_TYPE = 0x0202;

    public static final int SPEC_PUBLIC = 0x40000000;

    private ArscParser() {
    }

    /** 解析 arsc，返回 (id → "string/app_name") */
    public static Map<Integer, String> parse(byte[] data) throws IOException {
        Map<Integer, String> result = new HashMap<>();
        if (data == null || data.length < 8) return result;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int type = b.getShort() & 0xffff;
        int headerSize = b.getShort() & 0xffff;
        int size = b.getInt();
        if (type != RES_TABLE_TYPE) {
            // 不是 arsc
            return result;
        }
        int packageCount = b.getInt();
        // 顺序：global string pool, 然后 N 个 package chunk
        // 跳过 global string pool
        if (b.remaining() < 8) return result;
        int pos = b.position();
        int spType = b.getShort() & 0xffff;
        int spHeader = b.getShort() & 0xffff;
        int spSize = b.getInt();
        String[] globalStrings = readStringPool(b, pos, spSize);
        b.position(pos + spSize);

        for (int pkg = 0; pkg < packageCount; pkg++) {
            if (b.remaining() < 8) break;
            int ppos = b.position();
            int pType = b.getShort() & 0xffff;
            int pHeader = b.getShort() & 0xffff;
            int pSize = b.getInt();
            if (pType != RES_TABLE_PACKAGE_TYPE) {
                b.position(ppos + pSize);
                continue;
            }
            int pkgId = b.getInt();
            // name: 256 chars (utf16)
            char[] nameArr = new char[128];
            for (int i = 0; i < 128; i++) nameArr[i] = b.getChar();
            String pkgName = new String(nameArr).trim();
            int typeStringsOffset = b.getInt();
            int lastPublicType = b.getInt();
            int keyStringsOffset = b.getInt();
            int lastPublicKey = b.getInt();
            int typeIdOffset = b.getInt(); // 0x0202+ 才有

            // type strings
            String[] typeStrings = null;
            if (typeStringsOffset > 0) {
                int ts = ppos + typeStringsOffset;
                b.position(ts);
                int tsType = b.getShort() & 0xffff;
                int tsHeader = b.getShort() & 0xffff;
                int tsSize = b.getInt();
                typeStrings = readStringPool(b, ts, tsSize);
            }
            // key strings
            String[] keyStrings = null;
            if (keyStringsOffset > 0) {
                int ks = ppos + keyStringsOffset;
                b.position(ks);
                int ksType = b.getShort() & 0xffff;
                int ksHeader = b.getShort() & 0xffff;
                int ksSize = b.getInt();
                keyStrings = readStringPool(b, ks, ksSize);
            }

            // 遍历 package 内的所有 type chunk
            b.position(ppos + pHeader);
            int end = ppos + pSize;
            while (b.position() < end && b.remaining() >= 8) {
                int cpos = b.position();
                int cType = b.getShort() & 0xffff;
                int cHeader = b.getShort() & 0xffff;
                int cSize = b.getInt();
                if (cSize < 8) break;
                if (cType == RES_TABLE_TYPE_TYPE) {
                    int typeId = b.get() & 0xff;
                    int res0 = b.get() & 0xff;
                    int res1 = b.getShort() & 0xffff;
                    int entryCount = b.getInt();
                    int entriesStart = b.getInt();
                    int configSize = b.getInt();
                    // 跳过 config
                    b.position(cpos + cHeader);
                    int entriesBase = cpos + entriesStart;
                    for (int i = 0; i < entryCount; i++) {
                        int entryOffset = b.getInt();
                        if (entryOffset == -1) continue;
                        int savePos = b.position();
                        int ep = entriesBase + entryOffset;
                        b.position(ep);
                        int entrySize = b.getShort() & 0xffff;
                        int entryFlags = b.getShort() & 0xffff;
                        int keyIdx = b.getInt();
                        String typeName = safe(typeStrings, typeId - 1);
                        String keyName = safe(keyStrings, keyIdx);
                        if (typeName != null && keyName != null) {
                            int resId = (pkgId << 24) | (typeId << 16) | i;
                            result.put(resId, typeName + "/" + keyName);
                        }
                        b.position(savePos);
                    }
                }
                b.position(cpos + cSize);
            }
            b.position(ppos + pSize);
        }
        return result;
    }

    private static String safe(String[] arr, int idx) {
        if (arr == null || idx < 0 || idx >= arr.length) return null;
        String s = arr[idx];
        return s == null ? null : s;
    }

    private static String[] readStringPool(ByteBuffer b, int start, int size) {
        int savePos = b.position();
        b.position(start);
        int type = b.getShort() & 0xffff;
        int headerSize = b.getShort() & 0xffff;
        int chunkSize = b.getInt();
        int stringCount = b.getInt();
        int styleCount = b.getInt();
        int flags = b.getInt();
        int stringsStart = b.getInt();
        int stylesStart = b.getInt();
        boolean utf8 = (flags & BinaryXml.UTF8_FLAG) != 0;
        int[] offsets = new int[stringCount];
        for (int i = 0; i < stringCount; i++) offsets[i] = b.getInt();
        String[] result = new String[stringCount];
        for (int i = 0; i < stringCount; i++) {
            int off = start + stringsStart + offsets[i];
            b.position(off);
            if (utf8) {
                int len1 = b.get() & 0xff;
                int len;
                if ((len1 & 0x80) != 0) {
                    len = ((len1 & 0x7f) << 8) | (b.get() & 0xff);
                } else {
                    len = len1;
                }
                int len2 = b.get() & 0xff;
                if ((len2 & 0x80) != 0) b.get();
                byte[] arr = new byte[len];
                b.get(arr);
                result[i] = new String(arr, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                int len1 = b.getShort() & 0xffff;
                int len;
                if ((len1 & 0x8000) != 0) {
                    len = ((len1 & 0x7fff) << 16) | (b.getShort() & 0xffff);
                } else {
                    len = len1;
                }
                char[] arr = new char[len];
                for (int j = 0; j < len; j++) arr[j] = b.getChar();
                result[i] = new String(arr);
            }
        }
        b.position(savePos);
        return result;
    }
}
