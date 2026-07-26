package com.jadx.dexeditor.axml;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 二进制 AXML ↔ 文本 XML 互转，并支持资源 ID 还原为名称（@7F010000 → @string/app_name）。
 */
public final class AxmlConverter {

    private AxmlConverter() {
    }

    /** 把二进制 AXML 转为带缩进的文本 XML，并尝试把 @7Fxxxxxx 形式还原为资源名 */
    public static String toTextXml(byte[] binary, Map<Integer, String> idToName) throws Exception {
        BinaryXml.Document doc = BinaryXml.decode(binary);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        for (BinaryXml.Element root : doc.roots) {
            writeElement(sb, root, 0, idToName);
        }
        return sb.toString();
    }

    /**
     * 把文本 XML 编码为二进制 AXML。
     * <p>
     * 简易手写解析器，支持 Manifest 编辑常用的子集：
     * 声明 / 处理指令 / 注释 / CDATA / 标签 / 属性 / 文本节点 / 实体引用。
     * <p>
     * 命名空间自动从 xmlns:xxx 属性推断。
     */
    public static byte[] toBinary(String textXml) throws Exception {
        BinaryXml.Document doc = parseTextXml(textXml);
        return BinaryXml.encode(doc);
    }

    private static BinaryXml.Document parseTextXml(String text) {
        Parser p = new Parser(text);
        BinaryXml.Document doc = new BinaryXml.Document();
        // 跳过 XML 声明 / 处理指令 / DOCTYPE / 注释
        while (p.pos < p.s.length()) {
            if (p.peek("<?")) {
                int end = p.s.indexOf("?>", p.pos);
                if (end < 0) break;
                p.pos = end + 2;
                p.skipWs();
            } else if (p.peek("<!--")) {
                int end = p.s.indexOf("-->", p.pos);
                if (end < 0) break;
                p.pos = end + 3;
                p.skipWs();
            } else if (p.peek("<!")) {
                int end = p.s.indexOf('>', p.pos);
                if (end < 0) break;
                p.pos = end + 1;
                p.skipWs();
            } else if (p.peek("<")) {
                BinaryXml.Element e = p.parseElement(new HashMap<>());
                if (e != null) doc.roots.add(e);
            } else {
                p.pos++;
            }
        }
        return doc;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        BinaryXml.Element parseElement(Map<String, String> nsMap) {
            if (!peek("<")) return null;
            pos++; // skip <
            boolean isEnd = pos < s.length() && s.charAt(pos) == '/';
            if (isEnd) {
                pos++;
                skipWs();
                String name = readName();
                skipWs();
                if (pos < s.length() && s.charAt(pos) == '>') pos++;
                return null;
            }
            String name = readName();
            if (name.isEmpty()) return null;
            BinaryXml.Element e = new BinaryXml.Element();
            e.lineNumber = 1;
            // 处理 xmlns:foo="..."
            List<BinaryXml.Attribute> attrs = new ArrayList<>();
            Map<String, String> localNs = new HashMap<>(nsMap);
            while (true) {
                skipWs();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (c == '>' || c == '/') break;
                String an = readName();
                if (an.isEmpty()) {
                    pos++;
                    continue;
                }
                skipWs();
                String av = "";
                if (pos < s.length() && s.charAt(pos) == '=') {
                    pos++;
                    skipWs();
                    av = readQuoted();
                }
                // 解析命名空间声明
                if (an.startsWith("xmlns:")) {
                    String prefix = an.substring(6);
                    localNs.put(prefix, av);
                } else if (an.equals("xmlns")) {
                    localNs.put("", av);
                } else {
                    BinaryXml.Attribute attr = new BinaryXml.Attribute();
                    int colon = an.indexOf(':');
                    if (colon > 0) {
                        String prefix = an.substring(0, colon);
                        attr.name = an.substring(colon + 1);
                        attr.ns = localNs.get(prefix);
                    } else {
                        attr.name = an;
                        attr.ns = null;
                    }
                    attr.rawValue = av;
                    attr.typedValue = 0;
                    attr.typedType = inferType(av);
                    if (attr.typedType == BinaryXml.TYPE_REFERENCE && av.startsWith("@")) {
                        attr.typedValue = parseResId(av);
                    } else if (attr.typedType == BinaryXml.TYPE_INT_BOOLEAN) {
                        attr.typedValue = "true".equalsIgnoreCase(av) ? -1 : 0;
                    } else if (attr.typedType == BinaryXml.TYPE_INT_HEX) {
                        try {
                            attr.typedValue = (int) Long.parseLong(av.substring(2), 16);
                        } catch (Throwable t) {
                            attr.typedValue = 0;
                        }
                    } else if (attr.typedType == BinaryXml.TYPE_INT_DEC) {
                        try {
                            attr.typedValue = (int) Long.parseLong(av);
                        } catch (Throwable t) {
                            attr.typedValue = 0;
                        }
                    } else if (attr.typedType == BinaryXml.TYPE_FIRST_COLOR_INT) {
                        try {
                            attr.typedValue = (int) Long.parseLong(av.substring(1), 16);
                        } catch (Throwable t) {
                            attr.typedValue = 0;
                        }
                    }
                    attrs.add(attr);
                }
            }
            e.attributes.addAll(attrs);
            // 处理 ns
            // 元素自身的 ns：如果 name 有 prefix，从 localNs 取
            int colon = name.indexOf(':');
            if (colon > 0) {
                String prefix = name.substring(0, colon);
                e.name = name.substring(colon + 1);
                e.ns = localNs.get(prefix);
            } else {
                e.name = name;
                e.ns = null;
            }
            boolean selfClose = false;
            if (pos < s.length() && s.charAt(pos) == '/') {
                selfClose = true;
                pos++;
            }
            if (pos < s.length() && s.charAt(pos) == '>') pos++;
            if (selfClose) return e;
            // 子节点 / 文本
            while (pos < s.length()) {
                if (peek("</")) {
                    int end = s.indexOf('>', pos);
                    if (end < 0) break;
                    pos = end + 1;
                    return e;
                } else if (peek("<!--")) {
                    int end = s.indexOf("-->", pos);
                    if (end < 0) break;
                    pos = end + 3;
                } else if (peek("<![CDATA[")) {
                    int end = s.indexOf("]]>", pos);
                    if (end < 0) break;
                    String cd = s.substring(pos + 9, end);
                    e.children.add(cd);
                    pos = end + 3;
                } else if (peek("<")) {
                    BinaryXml.Element child = parseElement(localNs);
                    if (child != null) e.children.add(child);
                } else {
                    int lt = s.indexOf('<', pos);
                    if (lt < 0) lt = s.length();
                    String text = s.substring(pos, lt).trim();
                    pos = lt;
                    if (!text.isEmpty()) {
                        e.children.add(unescape(text));
                    }
                }
            }
            return e;
        }

        private int inferType(String v) {
            if (v == null || v.isEmpty()) return BinaryXml.TYPE_STRING;
            if (v.startsWith("@") || v.startsWith("@+")) return BinaryXml.TYPE_REFERENCE;
            if (v.startsWith("#")) return BinaryXml.TYPE_FIRST_COLOR_INT;
            if (v.startsWith("0x") || v.startsWith("0X")) return BinaryXml.TYPE_INT_HEX;
            if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) return BinaryXml.TYPE_INT_BOOLEAN;
            // 数字
            try {
                Long.parseLong(v);
                return BinaryXml.TYPE_INT_DEC;
            } catch (NumberFormatException nfe) {
                return BinaryXml.TYPE_STRING;
            }
        }

        private int parseResId(String v) {
            // @string/app_name 或 @7F010000 或 @android:string/...
            try {
                if (v.matches("@[0-9a-fA-F]{6,8}")) {
                    return (int) Long.parseLong(v.substring(1), 16);
                }
                if (v.matches("@0x[0-9a-fA-F]+")) {
                    return (int) Long.parseLong(v.substring(3), 16);
                }
            } catch (Throwable t) {
                return 0;
            }
            return 0;
        }

        private String unescape(String s) {
            if (s.indexOf('&') < 0) return s;
            return s.replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&amp;", "&");
        }

        String readName() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-' || c == '.' || c == '/') {
                    pos++;
                } else {
                    break;
                }
            }
            return s.substring(start, pos);
        }

        String readQuoted() {
            if (pos >= s.length()) return "";
            char q = s.charAt(pos);
            if (q != '"' && q != '\'') {
                int start = pos;
                while (pos < s.length()) {
                    char c = s.charAt(pos);
                    if (c == '>' || Character.isWhitespace(c)) break;
                    pos++;
                }
                return s.substring(start, pos);
            }
            pos++;
            int start = pos;
            while (pos < s.length() && s.charAt(pos) != q) pos++;
            String v = s.substring(start, pos);
            if (pos < s.length()) pos++;
            return v;
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        boolean peek(String prefix) {
            return s.regionMatches(pos, prefix, 0, prefix.length());
        }
    }

    private static void writeElement(StringBuilder sb, BinaryXml.Element e, int depth,
                                     Map<Integer, String> idToName) {
        indent(sb, depth);
        sb.append('<');
        if (!TextUtils.isEmpty(e.ns)) {
            sb.append(prefixOf(e.ns)).append(':');
        }
        sb.append(e.name);
        for (BinaryXml.Attribute a : e.attributes) {
            sb.append('\n');
            indent(sb, depth + 1);
            if (!TextUtils.isEmpty(a.ns)) {
                sb.append(prefixOf(a.ns)).append(':');
            }
            sb.append(a.name).append("=\"");
            sb.append(escapeAttr(attrValue(a, idToName)));
            sb.append('"');
        }
        if (e.children.isEmpty()) {
            sb.append(" />\n");
            return;
        }
        sb.append(">\n");
        for (Object child : e.children) {
            if (child instanceof BinaryXml.Element) {
                writeElement(sb, (BinaryXml.Element) child, depth + 1, idToName);
            } else if (child instanceof String) {
                indent(sb, depth + 1);
                sb.append(escapeText((String) child)).append('\n');
            }
        }
        indent(sb, depth);
        sb.append("</");
        if (!TextUtils.isEmpty(e.ns)) {
            sb.append(prefixOf(e.ns)).append(':');
        }
        sb.append(e.name).append(">\n");
    }

    private static String attrValue(BinaryXml.Attribute a, Map<Integer, String> idToName) {
        if (a.typedType == BinaryXml.TYPE_STRING) {
            return a.rawValue != null ? a.rawValue : "";
        }
        if (a.typedType == BinaryXml.TYPE_REFERENCE && idToName != null) {
            String name = idToName.get(a.typedValue);
            if (name != null) return "@" + name;
        }
        if (a.typedType == BinaryXml.TYPE_INT_BOOLEAN) {
            return a.typedValue != 0 ? "true" : "false";
        }
        if (a.typedType >= BinaryXml.TYPE_FIRST_COLOR_INT) {
            return String.format("#%08x", a.typedValue);
        }
        if (a.typedType == BinaryXml.TYPE_INT_HEX) {
            return "0x" + Integer.toHexString(a.typedValue);
        }
        // fallback：原始值或十进制
        if (a.rawValue != null && !a.rawValue.isEmpty()) return a.rawValue;
        return Integer.toString(a.typedValue);
    }

    private static final Pattern RES_ID_PATTERN = Pattern.compile("@7f([0-9a-fA-F]{6})", Pattern.CASE_INSENSITIVE);

    /** 把文本中的 @7F010000 等还原为 @string/xxx（如果映射表里有） */
    public static String restoreResourceNames(String text, Map<Integer, String> idToName) {
        if (idToName == null || idToName.isEmpty() || text == null) return text;
        Matcher m = RES_ID_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int id = (int) (0x7f000000L | Long.parseLong(m.group(1), 16));
            String name = idToName.get(id);
            if (name != null) {
                m.appendReplacement(sb, "@" + name);
            } else {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String prefixOf(String ns) {
        if (ns == null) return "";
        if (ns.endsWith("/apk/res/android")) return "android";
        if (ns.endsWith("/apk/res-auto")) return "app";
        if (ns.endsWith("/tools")) return "tools";
        // fallback: 取最后一段
        int slash = ns.lastIndexOf('/');
        return slash >= 0 ? ns.substring(slash + 1) : ns;
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) sb.append("    ");
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("&quot;"); break;
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '\n': sb.append("&#10;"); break;
                case '\r': sb.append("&#13;"); break;
                case '\t': sb.append("&#9;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeText(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
