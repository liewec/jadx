package com.jadx.dexeditor.axml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自实现的 Android 二进制 AXML 解析器/编码器。
 * <p>
 * 二进制 AXML 格式参考 AOSP frameworks/base/include/androidfw/ResourceTypes.h
 * <p>
 * 仅实现 Manifest 编辑所需的最小子集：
 * - RES_XML_TYPE / RES_STRING_POOL_TYPE / RES_XML_RESOURCE_MAP_TYPE / RES_XML_START_ELEMENT_TYPE / RES_XML_END_ELEMENT_TYPE / RES_XML_CDATA_TYPE
 * - 字符串池 / 资源 ID 映射
 */
public final class BinaryXml {

    // ==== Chunk types ====
    public static final int RES_NULL_TYPE = 0x0000;
    public static final int RES_STRING_POOL_TYPE = 0x0001;
    public static final int RES_TABLE_TYPE = 0x0201;
    public static final int RES_XML_TYPE = 0x0003;

    public static final int RES_XML_FIRST_CHUNK_TYPE = 0x0100;
    public static final int RES_XML_START_NAMESPACE_TYPE = 0x0100;
    public static final int RES_XML_END_NAMESPACE_TYPE = 0x0101;
    public static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    public static final int RES_XML_END_ELEMENT_TYPE = 0x0103;
    public static final int RES_XML_CDATA_TYPE = 0x0104;
    public static final int RES_XML_RESOURCE_MAP_TYPE = 0x0180;

    // ==== String pool flags ====
    public static final int UTF8_FLAG = 1 << 8;

    // ==== Value types ====
    public static final int TYPE_NULL = 0x00;
    public static final int TYPE_REFERENCE = 0x01;
    public static final int TYPE_ATTRIBUTE = 0x02;
    public static final int TYPE_STRING = 0x03;
    public static final int TYPE_FLOAT = 0x04;
    public static final int TYPE_DIMENSION = 0x05;
    public static final int TYPE_FRACTION = 0x06;
    public static final int TYPE_INT_DEC = 0x10;
    public static final int TYPE_INT_HEX = 0x11;
    public static final int TYPE_INT_BOOLEAN = 0x12;
    public static final int TYPE_FIRST_COLOR_INT = 0x1c;
    public static final int TYPE_INT_COLOR_ARGB8 = 0x1c;
    public static final int TYPE_INT_COLOR_RGB8 = 0x1d;
    public static final int TYPE_INT_COLOR_ARGB4 = 0x1e;
    public static final int TYPE_INT_COLOR_RGB4 = 0x1f;

    public static final int ATTR_TYPE_REFERENCE = 0x01;
    public static final int ATTR_TYPE_STRING = 0x03;
    public static final int ATTR_TYPE_INT_DEC = 0x10;
    public static final int ATTR_TYPE_INT_BOOL = 0x12;

    private BinaryXml() {
    }

    // ==== Data classes ====
    public static final class Attribute {
        public String ns;
        public String name;
        public String rawValue;
        public int typedValue;
        public int typedType;

        public Attribute() {
        }

        public Attribute(String ns, String name, String rawValue, int typedValue, int typedType) {
            this.ns = ns;
            this.name = name;
            this.rawValue = rawValue;
            this.typedValue = typedValue;
            this.typedType = typedType;
        }
    }

    public static final class Element {
        public int lineNumber;
        public String comment;
        public String ns;
        public String name;
        public final List<Attribute> attributes = new ArrayList<>();
        public final List<Object> children = new ArrayList<>(); // Element or String (cdata)
    }

    public static final class Document {
        public final List<Element> roots = new ArrayList<>();
    }

    // ==== Decoder ====
    public static Document decode(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        // XML header chunk
        int type = b.getShort() & 0xffff;
        int headerSize = b.getShort() & 0xffff;
        int chunkSize = b.getInt();
        if (type != RES_XML_TYPE) {
            throw new IOException("Not a binary XML (type=0x" + Integer.toHexString(type) + ")");
        }
        String[] strings = null;
        int[] resourceIds = null;
        List<int[]> startNamespaces = new ArrayList<>();
        List<int[]> endNamespaces = new ArrayList<>();
        List<Object> chunksInOrder = new ArrayList<>(); // Element / String(cdata)

        // 第一遍：解析所有 chunk
        List<Element> elementStack = new ArrayList<>();
        Document doc = new Document();
        while (b.remaining() >= 8) {
            int pos = b.position();
            int cType = b.getShort() & 0xffff;
            int cHeader = b.getShort() & 0xffff;
            int cSize = b.getInt();
            if (cSize < 8 || pos + cSize > b.limit()) break;
            switch (cType) {
                case RES_STRING_POOL_TYPE: {
                    strings = readStringPool(b, pos, cSize);
                    break;
                }
                case RES_XML_RESOURCE_MAP_TYPE: {
                    int count = (cSize - 8) / 4;
                    resourceIds = new int[count];
                    for (int i = 0; i < count; i++) {
                        resourceIds[i] = b.getInt();
                    }
                    break;
                }
                case RES_XML_START_NAMESPACE_TYPE: {
                    int line = b.getInt();
                    int comment = b.getInt();
                    int prefix = b.getInt();
                    int uri = b.getInt();
                    startNamespaces.add(new int[]{prefix, uri});
                    break;
                }
                case RES_XML_END_NAMESPACE_TYPE: {
                    int line = b.getInt();
                    int comment = b.getInt();
                    int prefix = b.getInt();
                    int uri = b.getInt();
                    endNamespaces.add(new int[]{prefix, uri});
                    break;
                }
                case RES_XML_START_ELEMENT_TYPE: {
                    int line = b.getInt();
                    int comment = b.getInt();
                    int nsIdx = b.getInt();
                    int nameIdx = b.getInt();
                    int attrStart = b.getShort() & 0xffff;
                    int attrSize = b.getShort() & 0xffff;
                    int attrCount = b.getShort() & 0xffff;
                    int idIdx = b.getShort() & 0xffff;
                    int classIdx = b.getShort() & 0xffff;
                    int styleIdx = b.getShort() & 0xffff;
                    Element e = new Element();
                    e.lineNumber = line;
                    e.comment = comment >= 0 ? safe(strings, comment) : null;
                    e.ns = nsIdx >= 0 ? safe(strings, nsIdx) : null;
                    e.name = safe(strings, nameIdx);
                    for (int i = 0; i < attrCount; i++) {
                        Attribute a = new Attribute();
                        int aNs = b.getInt();
                        int aName = b.getInt();
                        int aRawValue = b.getInt();
                        // typed value: 8 bytes
                        int vSize = b.getShort() & 0xffff;
                        int vRes0 = b.get() & 0xff;
                        int vType = b.get() & 0xff;
                        int vData = b.getInt();
                        a.ns = aNs >= 0 ? safe(strings, aNs) : null;
                        a.name = safe(strings, aName);
                        a.rawValue = aRawValue >= 0 ? safe(strings, aRawValue) : null;
                        a.typedValue = vData;
                        a.typedType = vType;
                        e.attributes.add(a);
                    }
                    if (elementStack.isEmpty()) {
                        doc.roots.add(e);
                    } else {
                        elementStack.get(elementStack.size() - 1).children.add(e);
                    }
                    elementStack.add(e);
                    break;
                }
                case RES_XML_END_ELEMENT_TYPE: {
                    int line = b.getInt();
                    int comment = b.getInt();
                    int nsIdx = b.getInt();
                    int nameIdx = b.getInt();
                    if (!elementStack.isEmpty()) {
                        elementStack.remove(elementStack.size() - 1);
                    }
                    break;
                }
                case RES_XML_CDATA_TYPE: {
                    int line = b.getInt();
                    int comment = b.getInt();
                    int dataIdx = b.getInt();
                    // typed value
                    b.getShort();
                    b.get();
                    b.get();
                    b.getInt();
                    String text = dataIdx >= 0 ? safe(strings, dataIdx) : null;
                    if (text != null && !text.isEmpty()) {
                        if (!elementStack.isEmpty()) {
                            elementStack.get(elementStack.size() - 1).children.add(text);
                        }
                        // 根级 CDATA 在 AXML 中不会出现，忽略
                    }
                    break;
                }
                default:
                    // skip unknown
                    break;
            }
            b.position(pos + cSize);
        }
        return doc;
    }

    private static String safe(String[] arr, int idx) {
        if (arr == null || idx < 0 || idx >= arr.length) return null;
        return arr[idx];
    }

    private static String[] readStringPool(ByteBuffer b, int start, int size) {
        int p = b.position();
        // 已经读了 header(type, headerSize, size)
        // 现在是 stringCount, styleCount, flags, stringsStart, stylesStart
        int stringCount = b.getInt();
        int styleCount = b.getInt();
        int flags = b.getInt();
        int stringsStart = b.getInt();
        int stylesStart = b.getInt();
        boolean utf8 = (flags & UTF8_FLAG) != 0;
        int[] offsets = new int[stringCount];
        for (int i = 0; i < stringCount; i++) {
            offsets[i] = b.getInt();
        }
        String[] result = new String[stringCount];
        for (int i = 0; i < stringCount; i++) {
            int off = start + stringsStart + offsets[i];
            b.position(off);
            if (utf8) {
                // u8 len (1 or 2 bytes), then u8 utf16 len (1 or 2 bytes), then bytes
                int len1 = b.get() & 0xff;
                int len;
                if ((len1 & 0x80) != 0) {
                    len = ((len1 & 0x7f) << 8) | (b.get() & 0xff);
                } else {
                    len = len1;
                }
                // utf16 length (ignored)
                int len2 = b.get() & 0xff;
                if ((len2 & 0x80) != 0) {
                    b.get();
                }
                byte[] arr = new byte[len];
                b.get(arr);
                result[i] = new String(arr, StandardCharsets.UTF_8);
            } else {
                int len1 = b.getShort() & 0xffff;
                int len;
                if ((len1 & 0x8000) != 0) {
                    len = ((len1 & 0x7fff) << 16) | (b.getShort() & 0xffff);
                } else {
                    len = len1;
                }
                char[] arr = new char[len];
                for (int j = 0; j < len; j++) {
                    arr[j] = b.getChar();
                }
                // null terminator
                if (b.remaining() >= 2) b.getShort();
                result[i] = new String(arr);
            }
        }
        return result;
    }

    // ==== Encoder ====
    public static byte[] encode(Document doc) throws IOException {
        // 收集所有字符串
        List<String> strings = new ArrayList<>();
        Map<String, Integer> stringIdx = new HashMap<>();
        // 占位 0 号字符串（"")
        strings.add("");
        stringIdx.put("", 0);

        // 收集 resource IDs（按 attribute name 出现顺序）
        List<Integer> resIds = new ArrayList<>();

        // 序列化元素
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Element root : doc.roots) {
            encodeElement(body, root, strings, stringIdx, resIds);
        }

        // 构建 string pool chunk
        ByteArrayOutputStream stringsBuf = new ByteArrayOutputStream();
        List<Integer> stringOffsets = new ArrayList<>();
        for (String s : strings) {
            stringOffsets.add(stringsBuf.size());
            writeUtf8String(stringsBuf, s);
        }
        // null terminator for last string is already added by writeUtf8String

        int stringsStart = 8 + 4 * 5 + 4 * strings.size(); // header + 5 fields + offsets
        int stringsChunkSize = stringsStart + stringsBuf.size();
        // align
        while (stringsChunkSize % 4 != 0) stringsChunkSize++;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // XML header
        writeShort(out, RES_XML_TYPE);
        writeShort(out, 8); // header size
        writeInt(out, 0); // total size, will fix
        int xmlHeaderEnd = out.size();

        // String pool chunk
        writeShort(out, RES_STRING_POOL_TYPE);
        writeShort(out, 8 + 4 * 5); // header size
        writeInt(out, stringsChunkSize);
        writeInt(out, strings.size());
        writeInt(out, 0); // style count
        writeInt(out, UTF8_FLAG);
        writeInt(out, stringsStart);
        writeInt(out, 0); // styles start
        for (int off : stringOffsets) writeInt(out, off);
        out.write(stringsBuf.toByteArray());
        // align to 4
        while (out.size() % 4 != 0) out.write(0);

        // Resource map chunk
        if (!resIds.isEmpty()) {
            writeShort(out, RES_XML_RESOURCE_MAP_TYPE);
            writeShort(out, 8);
            writeInt(out, 8 + 4 * resIds.size());
            for (int id : resIds) writeInt(out, id);
        }

        // body
        out.write(body.toByteArray());

        // fix total size
        int total = out.size();
        byte[] result = out.toByteArray();
        result[4] = (byte) (total & 0xff);
        result[5] = (byte) ((total >> 8) & 0xff);
        result[6] = (byte) ((total >> 16) & 0xff);
        result[7] = (byte) ((total >> 24) & 0xff);
        return result;
    }

    private static void encodeElement(ByteArrayOutputStream out, Element e,
                                      List<String> strings, Map<String, Integer> stringIdx,
                                      List<Integer> resIds) throws IOException {
        int nameIdx = internString(e.name, strings, stringIdx, resIds);
        int nsIdx = e.ns != null ? internString(e.ns, strings, stringIdx, null) : -1;

        // START_ELEMENT chunk
        int attrCount = e.attributes.size();
        int attrStart = 0x14; // 20
        int attrSize = 0x14; // 20
        int chunkSize = 8 + 4 * 4 + attrStart + attrCount * attrSize;
        // header: type(2) + headerSize(2) + size(4)
        writeShort(out, RES_XML_START_ELEMENT_TYPE);
        writeShort(out, 8 + 4 * 4); // 24
        writeInt(out, chunkSize);
        writeInt(out, e.lineNumber > 0 ? e.lineNumber : 1);
        writeInt(out, -1); // comment
        writeInt(out, nsIdx);
        writeInt(out, nameIdx);
        writeShort(out, attrStart);
        writeShort(out, attrSize);
        writeShort(out, attrCount);
        writeShort(out, 0); // id index
        writeShort(out, 0); // class index
        writeShort(out, 0); // style index

        // 计算每个属性的 ns/name/raw 索引（需要先注册到 string pool）
        int[] nsIdxArr = new int[attrCount];
        int[] nameIdxArr = new int[attrCount];
        int[] rawIdxArr = new int[attrCount];
        for (int i = 0; i < attrCount; i++) {
            Attribute a = e.attributes.get(i);
            nsIdxArr[i] = a.ns != null ? internString(a.ns, strings, stringIdx, null) : -1;
            nameIdxArr[i] = internString(a.name, strings, stringIdx, resIds);
            rawIdxArr[i] = a.rawValue != null ? internString(a.rawValue, strings, stringIdx, null) : -1;
        }
        for (int i = 0; i < attrCount; i++) {
            Attribute a = e.attributes.get(i);
            writeInt(out, nsIdxArr[i]);
            writeInt(out, nameIdxArr[i]);
            writeInt(out, rawIdxArr[i]);
            writeShort(out, 8); // typed value size
            writeByte(out, 0); // res0
            writeByte(out, a.typedType != 0 ? a.typedType : TYPE_STRING);
            writeInt(out, a.typedValue);
        }

        // children
        for (Object child : e.children) {
            if (child instanceof Element) {
                encodeElement(out, (Element) child, strings, stringIdx, resIds);
            } else if (child instanceof String) {
                String s = (String) child;
                int sIdx = internString(s, strings, stringIdx, null);
                writeShort(out, RES_XML_CDATA_TYPE);
                writeShort(out, 8 + 4 * 2 + 8);
                writeInt(out, 8 + 4 * 2 + 8);
                writeInt(out, 1); // line
                writeInt(out, -1); // comment
                writeInt(out, sIdx);
                writeShort(out, 8);
                writeByte(out, 0);
                writeByte(out, TYPE_STRING);
                writeInt(out, sIdx);
            }
        }

        // END_ELEMENT chunk
        writeShort(out, RES_XML_END_ELEMENT_TYPE);
        writeShort(out, 8 + 4 * 2);
        writeInt(out, 8 + 4 * 2);
        writeInt(out, e.lineNumber > 0 ? e.lineNumber : 1);
        writeInt(out, -1);
        writeInt(out, nsIdx);
        writeInt(out, nameIdx);
    }

    private static int internString(String s, List<String> strings,
                                    Map<String, Integer> stringIdx, List<Integer> resIds) {
        if (s == null) return -1;
        Integer idx = stringIdx.get(s);
        if (idx != null) return idx;
        int newIdx = strings.size();
        strings.add(s);
        stringIdx.put(s, newIdx);
        if (resIds != null) {
            // 占位 resource id
            resIds.add(0);
        }
        return newIdx;
    }

    private static void writeUtf8String(ByteArrayOutputStream out, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        // u8 length (utf8 byte count)
        if (len > 0x7f) {
            out.write(0x80 | ((len >> 8) & 0x7f));
            out.write(len & 0xff);
        } else {
            out.write(len);
        }
        // u8 utf16 length (we use same as len for simplicity)
        int utf16 = s.length();
        if (utf16 > 0x7f) {
            out.write(0x80 | ((utf16 >> 8) & 0x7f));
            out.write(utf16 & 0xff);
        } else {
            out.write(utf16);
        }
        out.write(bytes, 0, bytes.length);
        out.write(0);
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 24) & 0xff);
    }

    private static void writeByte(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
    }
}
