package com.jadx.dexeditor.axml;

/**
 * 简易 XML 文本格式化器：解析文本 XML 并按统一缩进输出。
 * <p>
 * 不依赖 Android XmlPullParser（避免与 AXML 二进制混淆），用最小手写解析器，
 * 仅支持 Manifest 编辑常用的子集（声明、注释、CDATA、处理指令、标签、属性、文本）。
 */
public final class XmlFormatter {

    private XmlFormatter() {
    }

    public static String format(String input) {
        if (input == null) return "";
        Parser p = new Parser(input);
        StringBuilder sb = new StringBuilder(input.length() + 64);
        try {
            p.format(sb);
        } catch (Throwable t) {
            // 格式化失败，返回原文本
            return input;
        }
        return sb.toString();
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        void format(StringBuilder out) {
            while (pos < s.length()) {
                if (peek("<?xml")) {
                    // XML 声明
                    int end = s.indexOf("?>", pos);
                    if (end < 0) { out.append(s, pos, s.length()); break; }
                    out.append(s, pos, end + 2).append('\n');
                    pos = end + 2;
                    skipWs();
                } else if (peek("<!--")) {
                    int end = s.indexOf("-->", pos);
                    if (end < 0) { out.append(s, pos, s.length()); break; }
                    out.append(s, pos, end + 3).append('\n');
                    pos = end + 3;
                } else if (peek("<!")) {
                    int end = s.indexOf('>', pos);
                    if (end < 0) { out.append(s, pos, s.length()); break; }
                    out.append(s, pos, end + 1).append('\n');
                    pos = end + 1;
                } else if (peek("<?")) {
                    int end = s.indexOf("?>", pos);
                    if (end < 0) { out.append(s, pos, s.length()); break; }
                    out.append(s, pos, end + 2).append('\n');
                    pos = end + 2;
                } else if (peek("<")) {
                    parseElement(out, 0);
                } else {
                    // 顶层文本：跳过
                    pos++;
                }
            }
        }

        void parseElement(StringBuilder out, int depth) {
            // 已经在 '<'
            int start = pos;
            pos++; // skip <
            boolean isEnd = s.charAt(pos) == '/';
            if (isEnd) pos++;
            String name = readName();
            if (isEnd) {
                skipWs();
                if (pos < s.length() && s.charAt(pos) == '>') pos++;
                indent(out, depth);
                out.append("</").append(name).append(">\n");
                return;
            }
            // 读取属性
            java.util.List<String[]> attrs = new java.util.ArrayList<>();
            while (true) {
                skipWs();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (c == '>' || c == '/') break;
                String an = readName();
                if (an.isEmpty()) { pos++; continue; }
                skipWs();
                String av = "";
                if (pos < s.length() && s.charAt(pos) == '=') {
                    pos++;
                    skipWs();
                    av = readQuoted();
                }
                attrs.add(new String[]{an, av});
            }
            boolean selfClose = false;
            if (pos < s.length() && s.charAt(pos) == '/') {
                selfClose = true;
                pos++;
            }
            if (pos < s.length() && s.charAt(pos) == '>') pos++;

            // 输出开始标签
            indent(out, depth);
            out.append('<').append(name);
            // 对齐：所有属性换行 + 缩进 depth+1
            for (String[] a : attrs) {
                out.append('\n');
                indent(out, depth + 1);
                out.append(a[0]).append("=\"").append(a[1]).append('"');
            }
            if (selfClose) {
                out.append('\n');
                indent(out, depth);
                out.append("/>\n");
                return;
            }
            out.append('\n');
            indent(out, depth);
            out.append(">\n");

            // 子节点 / 文本
            while (pos < s.length()) {
                if (peek("</")) {
                    int end = s.indexOf('>', pos);
                    if (end < 0) { out.append(s, pos, s.length()); return; }
                    String closeName = readCloseName();
                    if (pos < s.length() && s.charAt(pos) == '>') pos++;
                    indent(out, depth);
                    out.append("</").append(closeName).append(">\n");
                    return;
                } else if (peek("<!--")) {
                    int end = s.indexOf("-->", pos);
                    if (end < 0) { out.append(s, pos, s.length()); return; }
                    indent(out, depth + 1);
                    out.append(s, pos, end + 3).append('\n');
                    pos = end + 3;
                } else if (peek("<")) {
                    parseElement(out, depth + 1);
                } else {
                    // 文本节点
                    int lt = s.indexOf('<', pos);
                    if (lt < 0) lt = s.length();
                    String text = s.substring(pos, lt).trim();
                    pos = lt;
                    if (!text.isEmpty()) {
                        indent(out, depth + 1);
                        out.append(text).append('\n');
                    }
                }
            }
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

        String readCloseName() {
            // 假设在 </ 之后
            int save = pos;
            if (pos < s.length() && s.charAt(pos) == '/') pos++;
            skipWs();
            return readName();
        }

        String readQuoted() {
            if (pos >= s.length()) return "";
            char q = s.charAt(pos);
            if (q != '"' && q != '\'') {
                // 未引用，读到空白或 >
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

        void indent(StringBuilder out, int depth) {
            for (int i = 0; i < depth; i++) out.append("    ");
        }
    }
}
