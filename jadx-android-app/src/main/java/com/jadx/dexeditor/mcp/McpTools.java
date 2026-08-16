package com.jadx.dexeditor.mcp;

import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jadx.dexeditor.DexLoader;
import com.jadx.dexeditor.PathConfig;
import com.jadx.dexeditor.SmaliUtils;
import com.jadx.dexeditor.apk.ApkBuilder;
import com.jadx.dexeditor.apk.Unpacker;
import com.jadx.dexeditor.axml.ArscParser;
import com.jadx.dexeditor.axml.AxmlConverter;
import com.jadx.dexeditor.model.SearchResult;
import com.android.tools.smali.dexlib2.iface.ClassDef;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MCP 工具集：把应用的 DEX/APK 分析与编辑能力暴露为 MCP Tools。
 * <p>
 * 复用现有业务层：DexLoader / SmaliUtils / ApkBuilder / AxmlConverter / ArscParser / Unpacker / PathConfig。
 * 与 UI 共享 DexLoader 单例状态（AI 可直接分析用户已加载的文件）。
 */
public final class McpTools {

    /** 工具执行异常（错误信息返回给 AI 客户端，isError=true） */
    public static class ToolException extends Exception {
        public ToolException(String message) {
            super(message);
        }
    }

    private McpTools() {
    }

    // ==== tools/list ====

    public static JsonArray toolList() {
        JsonArray tools = new JsonArray();
        tools.add(tool("get_status", "获取当前状态：已加载文件、类/方法/字段统计、输出目录、服务器信息",
                obj()));
        tools.add(tool("load_file", "加载手机上的 DEX/APK 文件到分析引擎（后续工具操作的目标）。"
                        + "路径示例：/storage/emulated/0/Download/app.apk。也可用 list_dir 浏览文件。",
                obj(kv("path", "string", "文件绝对路径（.dex 或 .apk）", true))));
        tools.add(tool("list_classes", "分页列出已加载 DEX/APK 中的类（smali 类型描述符格式，如 Lcom/a/B;）",
                obj(kv("filter", "string", "包名/类名包含过滤（可选）", false),
                        kv("offset", "number", "起始偏移，默认 0（可选）", false),
                        kv("limit", "number", "返回数量，默认 50，最大 500（可选）", false))));
        tools.add(tool("search_code", "在已加载的 DEX 中按类名/方法名/字符串搜索",
                obj(kv("kind", "string", "搜索类型：class | method | string", true),
                        kv("keyword", "string", "关键字（大小写不敏感）", true),
                        kv("limit", "number", "返回数量，默认 30，最大 200（可选）", false))));
        tools.add(tool("get_smali", "获取指定类的 Smali 反汇编代码（baksmali 引擎）",
                obj(kv("classType", "string", "类类型描述符，如 Lcom/a/B; 或 com.a.B", true),
                        kv("maxLen", "number", "返回最大字符数，默认 100000（可选）", false))));
        tools.add(tool("decompile_class", "将指定类反编译为 Java 源码（jadx 引擎）",
                obj(kv("classType", "string", "类类型描述符，如 Lcom/a/B; 或 com.a.B", true),
                        kv("maxLen", "number", "返回最大字符数，默认 200000（可选）", false))));
        tools.add(tool("compile_smali", "把 Smali 代码编译为 DEX 文件，输出到成品目录，返回输出路径与字节数",
                obj(kv("smaliCode", "string", "完整 Smali 源码", true),
                        kv("outputName", "string", "输出 dex 文件名（可选，默认自动生成）", false))));
        tools.add(tool("list_dir", "列出手机目录内容（用于查找 APK/DEX 文件）",
                obj(kv("path", "string", "目录绝对路径", true))));
        tools.add(tool("list_apk_entries", "列出 APK 内所有条目（zip 条目路径）",
                obj(kv("path", "string", "APK 路径（可选，默认当前已加载文件）", false),
                        kv("filter", "string", "条目名包含过滤（可选）", false),
                        kv("limit", "number", "返回数量，默认 100，最大 1000（可选）", false))));
        tools.add(tool("read_apk_entry", "读取 APK 内条目内容：文本直接返回；二进制（图片/so等）返回 base64；"
                        + "二进制 AXML（如 res/xml、layout）自动解码为文本 XML",
                obj(kv("name", "string", "条目名（zip 内路径）", true),
                        kv("path", "string", "APK 路径（可选，默认当前已加载文件）", false),
                        kv("format", "string", "强制格式：text | base64（可选，默认自动判断）", false),
                        kv("maxLen", "number", "文本模式最大字符数，默认 100000（可选）", false))));
        tools.add(tool("read_manifest", "读取 APK 的 AndroidManifest.xml，自动解码二进制 AXML 为文本 XML，"
                        + "并加载 resources.arsc 将资源 ID 还原为名称",
                obj(kv("path", "string", "APK 路径（可选，默认当前已加载文件）", false))));
        tools.add(tool("write_manifest", "把修改后的文本 XML 编码为二进制 AXML 并回写 APK（AndroidManifest.xml），"
                        + "输出未签名的新 APK 到成品目录",
                obj(kv("xml", "string", "完整文本 XML 内容", true),
                        kv("path", "string", "源 APK 路径（可选，默认当前已加载文件）", false),
                        kv("outputName", "string", "输出 APK 文件名（可选，默认自动生成）", false))));
        tools.add(tool("replace_apk_entry", "替换 APK 内指定条目并输出新 APK 到成品目录。"
                        + "文本方式替换 .xml 且原条目为二进制 AXML 时自动重新编码",
                obj(kv("name", "string", "条目名（zip 内路径，不存在则新增）", true),
                        kv("text", "string", "文本内容（与 contentBase64 二选一）", false),
                        kv("contentBase64", "string", "二进制内容 base64（与 text 二选一）", false),
                        kv("path", "string", "源 APK 路径（可选，默认当前已加载文件）", false),
                        kv("outputName", "string", "输出 APK 文件名（可选，默认自动生成）", false))));
        tools.add(tool("detect_packer", "检测 APK 加壳情况（360加固/腾讯乐固/爱加密/梆梆/百度/阿里等）",
                obj(kv("path", "string", "APK 路径（可选，默认当前已加载文件）", false))));
        tools.add(tool("sign_apk", "使用内置密钥对 APK 签名（v2+v3），输出到成品目录",
                obj(kv("inputPath", "string", "待签名 APK 路径", true),
                        kv("outputName", "string", "输出 APK 文件名（可选，默认自动生成）", false))));
        return tools;
    }

    // ==== tools/call ====

    public static JsonObject call(JsonObject params) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        String name = params.has("name") && !params.get("name").isJsonNull()
                ? params.get("name").getAsString() : null;
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();

        String text;
        boolean isError = false;
        try {
            if (name == null) {
                throw new ToolException("Missing tool name");
            }
            text = execute(name, args);
        } catch (ToolException e) {
            isError = true;
            text = "工具执行失败：" + e.getMessage();
        } catch (Throwable t) {
            isError = true;
            text = "工具执行异常：" + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "");
            // EACCES：分区存储权限不足，附上明确的解决指引
            String msg = t.getMessage();
            if (msg != null && (msg.contains("EACCES") || msg.contains("Permission denied"))) {
                text = text + "\n\n解决方法：这是文件访问权限不足。请在手机上打开 Dex 编辑器 "
                        + "→ 菜单 → MCP 服务页，点击\"授权\"按钮，在系统设置中允许\"所有文件访问\""
                        + "（Android 11 及以上）后重试。";
            }
        }
        JsonObject c = new JsonObject();
        c.addProperty("type", "text");
        c.addProperty("text", text != null ? text : "");
        content.add(c);
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    // ==== 工具实现 ====

    private static String execute(String name, JsonObject a) throws Exception {
        switch (name) {
            case "get_status":
                return getStatus();
            case "load_file":
                return loadFile(str(a, "path"));
            case "list_classes":
                return listClasses(optStr(a, "filter"), optInt(a, "offset", 0), clamp(optInt(a, "limit", 50), 1, 500));
            case "search_code":
                return searchCode(str(a, "kind"), str(a, "keyword"), clamp(optInt(a, "limit", 30), 1, 200));
            case "get_smali":
                return getSmali(str(a, "classType"), clamp(optInt(a, "maxLen", 100_000), 1_000, 2_000_000));
            case "decompile_class":
                return decompile(str(a, "classType"), clamp(optInt(a, "maxLen", 200_000), 1_000, 4_000_000));
            case "compile_smali":
                return compileSmali(str(a, "smaliCode"), optStr(a, "outputName"));
            case "list_dir":
                return listDir(str(a, "path"));
            case "list_apk_entries":
                return listEntries(optStr(a, "path"), optStr(a, "filter"), clamp(optInt(a, "limit", 100), 1, 1000));
            case "read_apk_entry":
                return readEntry(str(a, "name"), optStr(a, "path"), optStr(a, "format"), clamp(optInt(a, "maxLen", 100_000), 1_000, 4_000_000));
            case "read_manifest":
                return readManifest(optStr(a, "path"));
            case "write_manifest":
                return writeManifest(str(a, "xml"), optStr(a, "path"), optStr(a, "outputName"));
            case "replace_apk_entry":
                return replaceEntry(str(a, "name"), optStr(a, "text"), optStr(a, "contentBase64"),
                        optStr(a, "path"), optStr(a, "outputName"));
            case "detect_packer":
                return detectPacker(optStr(a, "path"));
            case "sign_apk":
                return signApk(str(a, "inputPath"), optStr(a, "outputName"));
            default:
                throw new ToolException("Unknown tool: " + name);
        }
    }

    private static String getStatus() {
        DexLoader dl = DexLoader.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"server\": \"").append(McpServer.SERVER_NAME).append(" v").append(McpServer.SERVER_VERSION).append("\",\n");
        sb.append("  \"loaded\": ").append(dl.isLoaded()).append(",\n");
        if (dl.isLoaded()) {
            sb.append("  \"fileName\": \"").append(esc(dl.getFileName())).append("\",\n");
            sb.append("  \"filePath\": \"").append(dl.getLoadedFile() != null ? esc(dl.getLoadedFile().getAbsolutePath()) : "").append("\",\n");
            sb.append("  \"fileSize\": ").append(dl.getFileSize()).append(",\n");
            sb.append("  \"dexCount\": ").append(dl.getDexCount()).append(",\n");
            sb.append("  \"classCount\": ").append(dl.getClassCount()).append(",\n");
            sb.append("  \"methodCount\": ").append(dl.getMethodCount()).append(",\n");
            sb.append("  \"fieldCount\": ").append(dl.getFieldCount()).append(",\n");
            sb.append("  \"stringCount\": ").append(dl.getStringCount()).append(",\n");
            sb.append("  \"packageCount\": ").append(dl.getPackageCount()).append(",\n");
        }
        sb.append("  \"outputDir\": \"").append(esc(PathConfig.get().getOutputDir().getAbsolutePath())).append("\"\n");
        sb.append("}");
        return sb.toString();
    }

    private static String loadFile(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            // 权限不足时 exists() 同样返回 false（如未授予"所有文件访问"），
            // 通过父目录不可读区分两种情况，避免误导
            File parent = f.getParentFile();
            boolean parentReadable = parent != null && parent.canRead();
            if (!parentReadable && path.startsWith("/storage/emulated/0")
                    && !f.canRead()) {
                throw new ToolException("无权访问该路径（EACCES）：请先在 Dex 编辑器的 MCP 服务页"
                        + "点击\"授权\"，允许\"所有文件访问\"后重试。路径: " + path);
            }
            throw new ToolException("文件不存在: " + path);
        }
        if (!f.canRead()) {
            throw new ToolException("无权读取该文件（EACCES）：请先在 Dex 编辑器的 MCP 服务页"
                    + "点击\"授权\"，允许\"所有文件访问\"后重试。路径: " + path);
        }
        DexLoader dl = DexLoader.getInstance();
        int count = dl.load(f);
        return "已加载: " + f.getName() + "\n路径: " + f.getAbsolutePath()
                + "\n类数量: " + count
                + "\n方法数: " + dl.getMethodCount()
                + "\n提示: 可用 list_classes / search_code 继续分析";
    }

    private static String listClasses(String filter, int offset, int limit) throws Exception {
        DexLoader dl = DexLoader.getInstance();
        if (!dl.isLoaded()) {
            throw new ToolException("未加载文件，请先调用 load_file");
        }
        String needle = filter != null ? filter.toLowerCase() : null;
        StringBuilder sb = new StringBuilder();
        int total = dl.getClassCount();
        List<ClassDef> classes = dl.getClasses();
        int matched = 0, emitted = 0, index = 0;
        sb.append("共 ").append(total).append(" 个类");
        if (needle != null) sb.append("，过滤 \"").append(filter).append("\"");
        sb.append("：\n");
        for (ClassDef cls : classes) {
            String type = cls.getType();
            if (needle != null && !type.toLowerCase().contains(needle)) continue;
            if (index++ < offset) continue;
            if (emitted >= limit) break;
            sb.append("  ").append(type).append("\n");
            emitted++;
            matched++;
        }
        sb.append("\n已返回 ").append(emitted).append(" 条（offset=").append(offset)
                .append("）。更多请增大 offset。");
        return sb.toString();
    }

    private static String searchCode(String kind, String keyword, int limit) throws Exception {
        DexLoader dl = DexLoader.getInstance();
        if (!dl.isLoaded()) {
            throw new ToolException("未加载文件，请先调用 load_file");
        }
        int k;
        if ("class".equals(kind)) k = SearchResult.KIND_CLASS;
        else if ("method".equals(kind)) k = SearchResult.KIND_METHOD;
        else if ("string".equals(kind)) k = SearchResult.KIND_STRING;
        else throw new ToolException("kind 必须是 class | method | string");
        List<SearchResult> results = dl.search(k, keyword);
        StringBuilder sb = new StringBuilder();
        sb.append("搜索 ").append(kind).append(" \"").append(keyword).append("\" 找到 ")
                .append(results.size()).append(" 个结果：\n");
        int n = 0;
        for (SearchResult r : results) {
            if (n++ >= limit) {
                sb.append("…（已截断至 ").append(limit).append(" 条）\n");
                break;
            }
            sb.append('[').append(n).append("] ").append(r.getTitle()).append('\n');
            String sub = r.getSubtitle();
            if (sub != null && !sub.isEmpty()) {
                sb.append("    ").append(sub.replace("\n", " | ")).append('\n');
            }
            sb.append("    类: ").append(r.getClassType()).append('\n');
        }
        return sb.toString();
    }

    private static String getSmali(String classType, int maxLen) throws Exception {
        DexLoader dl = DexLoader.getInstance();
        ClassDef cls = findClassOrThrow(dl, classType);
        String smali = SmaliUtils.disassemble(cls);
        return truncate(smali, maxLen);
    }

    private static String decompile(String classType, int maxLen) throws Exception {
        DexLoader dl = DexLoader.getInstance();
        if (!dl.isLoaded()) {
            throw new ToolException("未加载文件，请先调用 load_file");
        }
        ClassDef cls = findClassOrThrow(dl, classType);
        String java = SmaliUtils.decompileToJava(dl.getLoadedFile(), cls.getType());
        return truncate(java, maxLen);
    }

    private static String compileSmali(String smaliCode, String outputName) throws Exception {
        File out = outputFile(outputName, "classes_mcp_", ".dex");
        boolean ok = SmaliUtils.compile(smaliCode, out.getAbsolutePath());
        if (!ok) {
            throw new ToolException("smali 编译失败（请检查语法）");
        }
        return "编译成功\n输出: " + out.getAbsolutePath() + "\n大小: " + out.length() + " 字节";
    }

    private static String listDir(String path) throws Exception {
        File dir = new File(path);
        if (!dir.exists()) throw new ToolException("路径不存在: " + path);
        if (!dir.isDirectory()) throw new ToolException("不是目录: " + path);
        File[] children = dir.listFiles();
        if (children == null) throw new ToolException("无法读取目录（EACCES 权限不足）：请先在 Dex 编辑器"
                + "的 MCP 服务页点击\"授权\"，允许\"所有文件访问\"后重试。路径: " + path);
        StringBuilder sb = new StringBuilder();
        sb.append("目录 ").append(path).append("（").append(children.length).append(" 项）：\n");
        int n = 0;
        // 目录在前，文件在后
        for (File f : children) if (f.isDirectory()) {
            if (n++ >= 500) break;
            sb.append("[目录] ").append(f.getName()).append('/').append('\n');
        }
        for (File f : children) if (f.isFile()) {
            if (n++ >= 500) { sb.append("…（超过 500 项已截断）\n"); break; }
            sb.append(String.format("[%d B] %s%n", f.length(), f.getName()));
        }
        return sb.toString();
    }

    private static String listEntries(String path, String filter, int limit) throws Exception {
        File apk = resolveApk(path);
        List<String> entries = ApkBuilder.listEntries(apk);
        String needle = filter != null ? filter.toLowerCase() : null;
        StringBuilder sb = new StringBuilder();
        sb.append("APK: ").append(apk.getName()).append("（共 ").append(entries.size()).append(" 个条目）\n");
        int n = 0, total = 0;
        for (String e : entries) {
            if (needle != null && !e.toLowerCase().contains(needle)) continue;
            total++;
            if (n++ >= limit) continue;
            sb.append("  ").append(e).append('\n');
        }
        if (n > limit) sb.append("…（匹配 ").append(total).append(" 条，已显示 ").append(limit).append(" 条）\n");
        return sb.toString();
    }

    private static String readEntry(String name, String path, String format, int maxLen) throws Exception {
        File apk = resolveApk(path);
        byte[] data = ApkBuilder.readEntry(apk, name);
        if (data == null) {
            throw new ToolException("条目不存在: " + name + "（可用 list_apk_entries 查看全部条目）");
        }
        boolean isAxml = data.length >= 4
                && (data[0] == 0x03 && data[1] == 0x00 && data[2] == 0x08 && data[3] == 0x00);
        if ("base64".equals(format)) {
            return "[base64, " + data.length + " 字节]\n" + Base64.encodeToString(data, Base64.NO_WRAP);
        }
        if (isAxml && !"text".equals(format)) {
            // 二进制 AXML → 文本 XML（自动加载 resources.arsc 还原资源 ID）
            try {
                byte[] arsc = ApkBuilder.readEntry(apk, "resources.arsc");
                java.util.Map<Integer, String> map = arsc != null ? ArscParser.parse(arsc) : java.util.Collections.emptyMap();
                String xml = AxmlConverter.toTextXml(data, map);
                return "[二进制 AXML 已解码为文本 XML, " + data.length + " 字节]\n" + truncate(xml, maxLen);
            } catch (Throwable t) {
                // 解码失败回退 base64
                return "[AXML 解码失败: " + t.getMessage() + "，返回 base64]\n"
                        + Base64.encodeToString(data, Base64.NO_WRAP);
            }
        }
        if ("text".equals(format) || isProbablyText(data)) {
            return "[文本, " + data.length + " 字节]\n"
                    + truncate(new String(data, StandardCharsets.UTF_8), maxLen);
        }
        return "[二进制 " + data.length + " 字节，返回 base64]\n"
                + Base64.encodeToString(data, Base64.NO_WRAP);
    }

    private static String readManifest(String path) throws Exception {
        File apk = resolveApk(path);
        byte[] bin = ApkBuilder.readEntry(apk, "AndroidManifest.xml");
        if (bin == null) {
            throw new ToolException("APK 中没有 AndroidManifest.xml: " + apk.getAbsolutePath());
        }
        if (bin.length >= 2 && bin[0] == 0x03 && bin[1] == 0x00) {
            byte[] arsc = ApkBuilder.readEntry(apk, "resources.arsc");
            java.util.Map<Integer, String> map = arsc != null ? ArscParser.parse(arsc) : java.util.Collections.emptyMap();
            String xml = AxmlConverter.toTextXml(bin, map);
            return "[二进制 AXML 已解码]\n" + xml;
        }
        return "[文本 XML]\n" + new String(bin, StandardCharsets.UTF_8);
    }

    private static String writeManifest(String xml, String path, String outputName) throws Exception {
        File apk = resolveApk(path);
        byte[] bin;
        try {
            bin = AxmlConverter.toBinary(xml);
        } catch (Throwable t) {
            throw new ToolException("XML → 二进制 AXML 编码失败: " + t.getMessage());
        }
        File out = outputFile(outputName, baseName(apk) + "_manifest_", ".apk");
        ApkBuilder.replaceEntry(apk, "AndroidManifest.xml", bin, out);
        return "已回写 AndroidManifest.xml\n输出 APK: " + out.getAbsolutePath()
                + "\n大小: " + out.length() + " 字节\n提示: 可继续调用 sign_apk 签名";
    }

    private static String replaceEntry(String name, String text, String contentBase64,
                                       String path, String outputName) throws Exception {
        if (text == null && contentBase64 == null) {
            throw new ToolException("必须提供 text 或 contentBase64 之一");
        }
        File apk = resolveApk(path);
        byte[] data;
        if (contentBase64 != null) {
            try {
                data = Base64.decode(contentBase64, Base64.NO_WRAP);
            } catch (Throwable t) {
                throw new ToolException("contentBase64 不是合法 base64");
            }
        } else {
            // 文本方式替换 .xml：若原条目是二进制 AXML，则自动重新编码
            byte[] orig = ApkBuilder.readEntry(apk, name);
            boolean origIsAxml = orig != null && orig.length >= 4
                    && orig[0] == 0x03 && orig[1] == 0x00 && orig[2] == 0x08 && orig[3] == 0x00;
            boolean isXml = name != null && name.toLowerCase().endsWith(".xml");
            if (isXml && origIsAxml) {
                try {
                    data = AxmlConverter.toBinary(text);
                } catch (Throwable t) {
                    throw new ToolException("文本 XML → AXML 编码失败: " + t.getMessage());
                }
            } else {
                data = text.getBytes(StandardCharsets.UTF_8);
            }
        }
        File out = outputFile(outputName, baseName(apk) + "_edited_", ".apk");
        ApkBuilder.replaceEntry(apk, name, data, out);
        return "已替换条目 " + name + "（" + data.length + " 字节）\n输出 APK: " + out.getAbsolutePath()
                + "\n提示: 可继续调用 sign_apk 签名";
    }

    private static String detectPacker(String path) throws Exception {
        File apk = resolveApk(path);
        Unpacker.DetectResult r = Unpacker.detect(apk);
        return r.toString();
    }

    private static String signApk(String inputPath, String outputName) throws Exception {
        File input = new File(inputPath);
        if (!input.exists()) {
            throw new ToolException("待签名 APK 不存在: " + inputPath);
        }
        File out = outputFile(outputName, baseName(input) + "_signed_", ".apk");
        try {
            ApkBuilder.signWithBuiltinKey(input, out);
        } catch (Throwable t) {
            throw new ToolException("签名失败: " + t.getMessage() + "（常见原因：APK 损坏或 Manifest 无效）");
        }
        return "签名成功（v2+v3，内置密钥）\n输出: " + out.getAbsolutePath() + "\n大小: " + out.length() + " 字节";
    }

    // ==== 辅助方法 ====

    /** 解析 APK 目标：显式 path 优先，否则取已加载文件 */
    private static File resolveApk(String path) throws ToolException {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (!f.exists()) throw new ToolException("文件不存在: " + path);
            return f;
        }
        DexLoader dl = DexLoader.getInstance();
        if (dl.isLoaded() && dl.getLoadedFile() != null && dl.getLoadedFile().exists()) {
            return dl.getLoadedFile();
        }
        throw new ToolException("未指定 path 且当前没有已加载文件，请先 load_file 或传入 path 参数");
    }

    private static ClassDef findClassOrThrow(DexLoader dl, String classType) throws ToolException {
        if (!dl.isLoaded()) {
            throw new ToolException("未加载文件，请先调用 load_file");
        }
        ClassDef cls = dl.findClass(normalizeType(classType));
        if (cls == null) {
            throw new ToolException("类不存在: " + classType + "（可用 list_classes 或 search_code 查找）");
        }
        return cls;
    }

    /** com.a.B → Lcom/a/B; */
    private static String normalizeType(String type) {
        if (type == null) return null;
        String t = type.trim();
        if (t.startsWith("L") && t.endsWith(";")) return t;
        return "L" + t.replace('.', '/') + ";";
    }

    private static File outputFile(String outputName, String prefix, String suffix) {
        File dir = PathConfig.get().getOutputDir();
        if (outputName != null && !outputName.isEmpty()) {
            if (!outputName.endsWith(suffix)) outputName = outputName + suffix;
            return new File(dir, outputName);
        }
        return new File(dir, prefix + System.currentTimeMillis() + suffix);
    }

    private static String baseName(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n…（已截断，共 " + s.length() + " 字符，可用 maxLen 参数调整）";
    }

    private static boolean isProbablyText(byte[] data) {
        int n = Math.min(data.length, 4096);
        int suspicious = 0;
        for (int i = 0; i < n; i++) {
            int b = data[i] & 0xff;
            if (b == 0) return false; // NUL → 二进制
            if (b < 0x09 || (b > 0x0d && b < 0x20)) suspicious++;
        }
        return suspicious * 20 < n;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String str(JsonObject o, String key) throws ToolException {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            return o.get(key).getAsString();
        }
        throw new ToolException("缺少必填参数: " + key);
    }

    private static String optStr(JsonObject o, String key) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            try {
                return o.get(key).getAsString();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static int optInt(JsonObject o, String key, int def) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            try {
                return o.get(key).getAsInt();
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ==== 工具描述构造 ====

    private static JsonObject tool(String name, String description, JsonObject inputSchema) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("description", description);
        t.add("inputSchema", inputSchema);
        return t;
    }

    private static JsonObject obj(JsonObject... kvs) {
        JsonObject o = new JsonObject();
        JsonArray required = new JsonArray();
        JsonObject props = new JsonObject();
        for (JsonObject kv : kvs) {
            String n = kv.get("name").getAsString();
            // 属性 schema 仅保留合法 JSON Schema 字段
            JsonObject prop = new JsonObject();
            prop.addProperty("type", kv.get("type").getAsString());
            prop.addProperty("description", kv.get("description").getAsString());
            props.add(n, prop);
            if (kv.get("required").getAsBoolean()) required.add(n);
        }
        o.addProperty("type", "object");
        o.add("properties", props);
        if (required.size() > 0) o.add("required", required);
        return o;
    }

    /** 单个参数描述（同时用于属性） */
    private static JsonObject kv(String name, String type, String desc, boolean required) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type);
        o.addProperty("description", desc);
        o.addProperty("required", required);
        return o;
    }
}
