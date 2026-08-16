package com.jadx.dexeditor.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

/**
 * MCP (Model Context Protocol) 服务端。
 * <p>
 * 基于 NanoHTTPD 实现，同时支持两种传输方式（双协议兼容）：
 * <ul>
 *   <li><b>Streamable HTTP</b>（MCP 2025-03-26 规范）：客户端 POST JSON-RPC 到 {@code /mcp}，
 *       服务端直接返回 JSON 响应。无状态实现，无需会话管理。</li>
 *   <li><b>HTTP + SSE</b>（MCP 2024-11-05 规范，已废弃但广泛兼容）：
 *       客户端 GET {@code /sse} 建立事件流，服务端下发 {@code endpoint} 事件告知消息端点，
 *       客户端 POST JSON-RPC 到 {@code /message?sessionId=xxx}（返回 202），
 *       响应通过 SSE 流以 {@code message} 事件推送。</li>
 * </ul>
 * 协议参考：https://modelcontextprotocol.io/specification
 */
public class McpServer extends NanoHTTPD {

    public static final String SERVER_NAME = "dex-editor-mcp";
    public static final String SERVER_VERSION = "1.3.1";
    public static final int DEFAULT_PORT = 33333;

    /** SSE 会话表（传统 HTTP+SSE 传输） */
    private final Map<String, SseSession> sseSessions = new ConcurrentHashMap<>();

    /** UI 日志回调（弱引用式：由 Fragment 注册/注销） */
    private volatile LogListener logListener;

    /**
     * 全局日志环形缓冲（静态）。
     * <p>
     * 无论 UI 是否在前台，日志始终写入缓冲：用户切到 AI 软件期间（本应用后台、
     * 监听器已注销）产生的请求日志不会丢失，返回服务页时可完整回看。
     */
    private static final StringBuilder LOG_BUFFER = new StringBuilder();
    private static final int LOG_BUFFER_MAX = 16000;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public interface LogListener {
        void onLog(String line);
    }

    public McpServer(int port) {
        super(port);
    }

    public void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    public int getPort() {
        return getListeningPort() > 0 ? getListeningPort() : DEFAULT_PORT;
    }

    /** 记录一条服务日志：写入静态缓冲（持久），并回调 UI 监听器（若在前台） */
    void log(String line) {
        String stamped = stamp(line);
        appendBuffer(stamped);
        LogListener l = logListener;
        if (l != null) {
            l.onLog(stamped);
        }
    }

    /** 静态日志：无需服务器实例即可记录（服务启动失败等场景） */
    public static void logStatic(String line) {
        appendBuffer(stamp(line));
    }

    /** 获取日志快照（含后台期间产生的日志），供 UI 回看 */
    public static String getLogSnapshot() {
        synchronized (LOG_BUFFER) {
            return LOG_BUFFER.toString();
        }
    }

    private static String stamp(String line) {
        synchronized (TIME_FMT) {
            return TIME_FMT.format(new Date()) + " " + line;
        }
    }

    private static void appendBuffer(String stamped) {
        synchronized (LOG_BUFFER) {
            LOG_BUFFER.append(stamped).append('\n');
            if (LOG_BUFFER.length() > LOG_BUFFER_MAX) {
                LOG_BUFFER.delete(0, LOG_BUFFER.length() - 12000);
            }
        }
    }

    // ==== HTTP 路由 ====

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        log("▸ " + method + " " + uri);

        try {
            if (Method.OPTIONS == method) {
                return cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""));
            }
            if (Method.POST == method) {
                if ("/mcp".equals(uri)) {
                    return cors(handleStreamablePost(session));
                }
                if ("/message".equals(uri)) {
                    return cors(handleSseMessagePost(session));
                }
                return cors(notFound());
            }
            if (Method.GET == method) {
                if ("/sse".equals(uri)) {
                    return handleSseConnect(session);
                }
                if ("/mcp".equals(uri)) {
                    // Streamable HTTP 的 GET（SSE 升级）不支持：按规范返回 405
                    Response resp = newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,
                            MIME_PLAINTEXT, "Use POST /mcp for JSON-RPC, or GET /sse for legacy SSE transport.");
                    resp.addHeader("Allow", "POST");
                    return cors(resp);
                }
                if ("/".equals(uri) || uri.isEmpty()) {
                    return cors(infoPage());
                }
            }
            if (Method.DELETE == method && "/mcp".equals(uri)) {
                // 无状态实现：会话终止请求直接确认
                return cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""));
            }
            return cors(notFound());
        } catch (Throwable t) {
            log("✗ 处理异常: " + t);
            return cors(jsonRpcErrorResponse(null, -32603, "Internal error: " + t.getMessage()));
        }
    }

    private Response notFound() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private Response cors(Response resp) {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Mcp-Session-Id, Last-Event-ID");
        return resp;
    }

    // ==== Streamable HTTP 传输 ====

    private Response handleStreamablePost(IHTTPSession session) throws IOException {
        String body = readBody(session);
        JsonElement el;
        try {
            el = JsonParser.parseString(body);
        } catch (Throwable t) {
            return jsonRpcErrorResponse(null, -32700, "Parse error: invalid JSON");
        }

        // 批量请求（JSON-RPC batch）
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    JsonObject resp = handleSingleRequest(item.getAsJsonObject());
                    if (resp != null) {
                        out.add(resp);
                    }
                }
            }
            if (out.size() == 0) {
                // 全部为通知
                return cors(newFixedLengthResponse(Response.Status.ACCEPTED, null, ""));
            }
            return cors(jsonResponse(out.toString()));
        }

        if (!el.isJsonObject()) {
            return jsonRpcErrorResponse(null, -32600, "Invalid Request");
        }
        JsonObject resp = handleSingleRequest(el.getAsJsonObject());
        if (resp == null) {
            // 通知：202 Accepted + 空体
            return cors(newFixedLengthResponse(Response.Status.ACCEPTED, null, ""));
        }
        return cors(jsonResponse(resp.toString()));
    }

    /** 处理单个 JSON-RPC 请求/通知；通知返回 null，请求返回响应对象 */
    private JsonObject handleSingleRequest(JsonObject req) {
        JsonElement idEl = req.get("id");
        boolean isNotification = idEl == null || idEl.isJsonNull();
        String method = req.has("method") && !req.get("method").isJsonNull()
                ? req.get("method").getAsString() : null;
        JsonObject params = req.has("params") && req.get("params").isJsonObject()
                ? req.getAsJsonObject("params") : new JsonObject();

        log("  ↳ JSON-RPC " + (isNotification ? "notification" : "id=" + idEl) + " " + method);

        if (isNotification) {
            // 通知无需响应（notifications/initialized 等）
            return null;
        }

        JsonObject result = dispatch(method, params);
        if (result.has("$$error$$")) {
            JsonObject err = result.getAsJsonObject("$$error$$");
            return errorResponse(idEl, err.get("code").getAsInt(), err.get("message").getAsString());
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", idEl);
        resp.add("result", result);
        return resp;
    }

    // ==== JSON-RPC 方法分发 ====

    private JsonObject dispatch(String method, JsonObject params) {
        JsonObject result = new JsonObject();
        if (method == null) {
            return errorObj(-32600, "Invalid Request: missing method");
        }
        switch (method) {
            case "initialize": {
                String clientProto = params.has("protocolVersion")
                        && !params.get("protocolVersion").isJsonNull()
                        ? params.get("protocolVersion").getAsString() : "2025-03-26";
                // 客户端请求的版本受支持则原样回显，否则回退到最新支持版本
                if (!"2024-11-05".equals(clientProto) && !"2025-03-26".equals(clientProto)) {
                    clientProto = "2025-03-26";
                }
                result.addProperty("protocolVersion", clientProto);
                JsonObject caps = new JsonObject();
                caps.add("tools", new JsonObject());
                result.add("capabilities", caps);
                JsonObject info = new JsonObject();
                info.addProperty("name", SERVER_NAME);
                info.addProperty("version", SERVER_VERSION);
                result.add("serverInfo", info);
                result.addProperty("instructions",
                        "Dex 编辑器 MCP 服务端：可加载并分析手机上的 DEX/APK 文件，"
                                + "浏览类结构、查看 Smali/反编译 Java、搜索代码、读取/编辑 AndroidManifest、"
                                + "替换 APK 资源、检测壳与签名 APK。先调用 load_file 或 get_status 开始。");
                break;
            }
            case "ping": {
                // 空结果即存活应答
                break;
            }
            case "tools/list": {
                result.add("tools", McpTools.toolList());
                break;
            }
            case "tools/call": {
                result = McpTools.call(params);
                break;
            }
            case "resources/list":
            case "resources/templates/list": {
                result.add("resources", new JsonArray());
                break;
            }
            case "prompts/list": {
                result.add("prompts", new JsonArray());
                break;
            }
            case "completion/complete": {
                result.add("completion", new JsonObject());
                break;
            }
            default: {
                if (method.startsWith("notifications/")) {
                    // 通知走到这里说明误带 id，静默返回空结果
                    break;
                }
                return errorObj(-32601, "Method not found: " + method);
            }
        }
        return result;
    }

    // ==== 传统 HTTP+SSE 传输 ====

    private Response handleSseConnect(IHTTPSession session) {
        final String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        final SseSession sse = new SseSession(sessionId);
        sseSessions.put(sessionId, sse);
        log("  + SSE 会话建立: " + sessionId);

        // 首个事件：endpoint，告知客户端消息回传地址
        sse.send("endpoint", "/message?sessionId=" + sessionId);

        Response resp = newChunkedResponse(Response.Status.OK, "text/event-stream", sse.getInputStream());
        resp.addHeader("Cache-Control", "no-cache");
        resp.addHeader("X-Accel-Buffering", "no");
        return cors(resp);
    }

    private Response handleSseMessagePost(IHTTPSession session) throws IOException {
        Map<String, String> params = session.getParameters() != null
                ? flatten(session.getParameters()) : new HashMap<>();
        String sessionId = params.get("sessionId");
        final SseSession sse = sessionId != null ? sseSessions.get(sessionId) : null;
        String body = readBody(session);

        if (sse == null || !sse.isOpen()) {
            return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                    "Invalid or expired session"));
        }

        JsonElement el;
        try {
            el = JsonParser.parseString(body);
        } catch (Throwable t) {
            sse.sendJson(errorResponse(null, -32700, "Parse error: invalid JSON"));
            return cors(newFixedLengthResponse(Response.Status.ACCEPTED, MIME_PLAINTEXT, ""));
        }
        if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    JsonObject resp = handleSingleRequest(item.getAsJsonObject());
                    if (resp != null) sse.sendJson(resp);
                }
            }
        } else if (el.isJsonObject()) {
            JsonObject resp = handleSingleRequest(el.getAsJsonObject());
            if (resp != null) sse.sendJson(resp);
        }
        // 规范要求返回 202 Accepted
        return cors(newFixedLengthResponse(Response.Status.ACCEPTED, MIME_PLAINTEXT, ""));
    }

    private static Map<String, String> flatten(Map<String, java.util.List<String>> in) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, java.util.List<String>> e : in.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                out.put(e.getKey(), e.getValue().get(0));
            }
        }
        return out;
    }

    // ==== SSE 会话 ====

    /** 一个 SSE 会话：队列 + 写线程 + 管道（供 NanoHTTPD chunked 流读取） */
    private final class SseSession {
        final String id;
        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(64);
        private final PipedInputStream pipeIn = new PipedInputStream(64 * 1024);
        private final PipedOutputStream pipeOut;
        private volatile boolean open = true;
        private final Thread writer;

        SseSession(String id) {
            this.id = id;
            try {
                this.pipeOut = new PipedOutputStream(pipeIn);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            writer = new Thread(this::run, "mcp-sse-" + id);
            writer.setDaemon(true);
            writer.start();
        }

        private void run() {
            try {
                while (open) {
                    String msg = queue.poll(1, TimeUnit.SECONDS);
                    if (msg != null) {
                        pipeOut.write(msg.getBytes(StandardCharsets.UTF_8));
                        pipeOut.flush();
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                close();
            }
        }

        void send(String event, String data) {
            // SSE data 不允许裸换行；单行 JSON 无此问题
            offer("event: " + event + "\ndata: " + data + "\n\n");
        }

        void sendJson(JsonObject obj) {
            send("message", obj.toString());
        }

        private void offer(String s) {
            if (open) {
                queue.offer(s);
            }
        }

        boolean isOpen() {
            return open;
        }

        java.io.InputStream getInputStream() {
            return pipeIn;
        }

        void close() {
            open = false;
            sseSessions.remove(id);
            try {
                pipeOut.close();
            } catch (IOException ignored) {
            }
            writer.interrupt();
        }
    }

    /** 客户端断开或服务停止时清理全部 SSE 会话 */
    public void closeAllSessions() {
        for (SseSession s : sseSessions.values()) {
            s.close();
        }
        sseSessions.clear();
    }

    // ==== 响应构造 ====

    private Response jsonResponse(String json) {
        Response resp = newFixedLengthResponse(Response.Status.OK, "application/json", json);
        return resp;
    }

    private Response jsonRpcErrorResponse(JsonElement id, int code, String message) {
        return jsonResponse(errorResponse(id, code, message).toString());
    }

    private static JsonObject errorResponse(JsonElement id, int code, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id);
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        resp.add("error", err);
        return resp;
    }

    private static JsonObject errorObj(int code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        JsonObject wrapper = new JsonObject();
        wrapper.add("$$error$$", err);
        return wrapper;
    }

    // ==== 首页信息 ====

    private Response infoPage() {
        int port = getPort();
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<title>Dex Editor MCP Server</title>"
                + "<style>body{font-family:sans-serif;max-width:640px;margin:40px auto;padding:0 16px;"
                + "color:#333}h1{font-size:1.4em}code{background:#f4f4f4;padding:2px 6px;border-radius:4px}"
                + ".ok{color:#0a7d32;font-weight:bold}</style></head><body>"
                + "<h1>Dex 编辑器 MCP 服务端</h1>"
                + "<p class=\"ok\">● 运行中 (端口 " + port + ")</p>"
                + "<ul>"
                + "<li>Streamable HTTP 端点：<code>POST http://&lt;本机IP&gt;:" + port + "/mcp</code></li>"
                + "<li>传统 SSE 端点：<code>GET http://&lt;本机IP&gt;:" + port + "/sse</code></li>"
                + "</ul>"
                + "<p>服务器名称：<code>" + SERVER_NAME + "</code> v" + SERVER_VERSION + "</p>"
                + "</body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    // ==== 工具 ====

    private String readBody(IHTTPSession session) throws IOException {
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (ResponseException e) {
            throw new IOException("Bad request: " + e.getMessage());
        }
        String body = files.get("postData");
        return body != null ? body : "";
    }

    /** 获取本机局域网 IPv4 地址（无则返回 127.0.0.1） */
    public static String getLanIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nis = java.net.NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                java.net.NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "127.0.0.1";
    }
}
