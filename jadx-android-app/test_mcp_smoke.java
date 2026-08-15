import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jadx.dexeditor.mcp.McpServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * JVM 冒烟测试：验证 McpServer 的 HTTP 传输 + JSON-RPC + SSE 握手。
 * 仅测试不触发 Android 依赖的方法（initialize/ping/resources/错误路径）。
 */
public class test_mcp_smoke {
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        int port = 34567;
        McpServer server = new McpServer(port);
        server.start(5000, true);
        System.out.println("server started on " + port);
        try {
            runTests(server, port);
        } finally {
            server.closeAllSessions();
            server.stop();
        }

        System.out.println("\n=== RESULT: " + (failures == 0 ? "ALL PASSED" : failures + " FAILURES") + " ===");
        if (failures > 0) System.exit(1);
    }

    static void runTests(McpServer server, int port) throws Exception {

        // 1. initialize
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        Resp r = post("http://127.0.0.1:" + port + "/mcp", initBody);
        check("initialize 200", r.code == 200);
        JsonObject init = JsonParser.parseString(r.body).getAsJsonObject();
        check("jsonrpc=2.0", "2.0".equals(init.get("jsonrpc").getAsString()));
        check("id echo", init.get("id").getAsInt() == 1);
        JsonObject result = init.getAsJsonObject("result");
        check("protocolVersion", "2025-03-26".equals(result.get("protocolVersion").getAsString()));
        check("serverInfo name", "dex-editor-mcp".equals(result.getAsJsonObject("serverInfo").get("name").getAsString()));
        check("capabilities.tools", result.getAsJsonObject("capabilities").has("tools"));

        // 2. 旧协议版本回显
        String initOld = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\"}}";
        r = post("http://127.0.0.1:" + port + "/mcp", initOld);
        check("old proto echo", "2024-11-05".equals(
                JsonParser.parseString(r.body).getAsJsonObject().getAsJsonObject("result")
                        .get("protocolVersion").getAsString()));

        // 3. ping
        r = post("http://127.0.0.1:" + port + "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}");
        check("ping 200", r.code == 200);
        check("ping empty result", JsonParser.parseString(r.body).getAsJsonObject()
                .getAsJsonObject("result").entrySet().isEmpty());

        // 4. 通知 → 202
        r = post("http://127.0.0.1:" + port + "/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        check("notification 202", r.code == 202);

        // 5. 未知方法 → -32601
        r = post("http://127.0.0.1:" + port + "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"foo/bar\"}");
        check("unknown method -32601", JsonParser.parseString(r.body).getAsJsonObject()
                .getAsJsonObject("error").get("code").getAsInt() == -32601);

        // 6. 非法 JSON → -32700
        r = post("http://127.0.0.1:" + port + "/mcp", "{invalid json");
        check("parse error -32700", r.code == 200 && JsonParser.parseString(r.body).getAsJsonObject()
                .getAsJsonObject("error").get("code").getAsInt() == -32700);

        // 7. 批量请求
        r = post("http://127.0.0.1:" + port + "/mcp",
                "[{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"},{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]");
        check("batch is array", JsonParser.parseString(r.body).isJsonArray());
        check("batch size 1", JsonParser.parseString(r.body).getAsJsonArray().size() == 1);

        // 8. resources/list / prompts/list
        r = post("http://127.0.0.1:" + port + "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/list\"}");
        check("resources empty", JsonParser.parseString(r.body).getAsJsonObject()
                .getAsJsonObject("result").getAsJsonArray("resources").size() == 0);
        r = post("http://127.0.0.1:" + port + "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"prompts/list\"}");
        check("prompts empty", JsonParser.parseString(r.body).getAsJsonObject()
                .getAsJsonObject("result").getAsJsonArray("prompts").size() == 0);

        // 9. GET /mcp → 405
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/mcp").openConnection();
        conn.setRequestMethod("GET");
        check("GET /mcp 405", conn.getResponseCode() == 405);
        conn.disconnect();

        // 10. 首页
        conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/").openConnection();
        conn.setRequestMethod("GET");
        check("info page 200", conn.getResponseCode() == 200);
        conn.disconnect();

        // 11. SSE 传输：GET /sse 收 endpoint，POST /message 收 message 事件
        URL sseUrl = new URL("http://127.0.0.1:" + port + "/sse");
        HttpURLConnection sseConn = (HttpURLConnection) sseUrl.openConnection();
        sseConn.setRequestMethod("GET");
        BufferedReader sseReader = new BufferedReader(
                new InputStreamReader(sseConn.getInputStream(), StandardCharsets.UTF_8));
        String[] endpointEvent = readSseEvent(sseReader);
        check("sse event line", "endpoint".equals(endpointEvent[0]));
        check("sse endpoint data", endpointEvent[1] != null && endpointEvent[1].startsWith("/message?sessionId="));
        String sessionId = endpointEvent[1].substring("/message?sessionId=".length());
        System.out.println("sse session: " + sessionId);

        // 通过 /message 发 initialize，期望 202 + SSE 流收到 message 事件
        r = post("http://127.0.0.1:" + port + "/message?sessionId=" + sessionId,
                "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{}}");
        check("sse message post 202", r.code == 202);
        String[] msgEvent = readSseEvent(sseReader);
        check("sse message event", "message".equals(msgEvent[0]));
        JsonObject sseResp = JsonParser.parseString(msgEvent[1]).getAsJsonObject();
        check("sse response id", sseResp.get("id").getAsInt() == 10);
        check("sse serverInfo", sseResp.getAsJsonObject("result").has("serverInfo"));

        // 12. 无效会话 → 404
        r = post("http://127.0.0.1:" + port + "/message?sessionId=deadbeef",
                "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"ping\"}");
        check("bad session 404", r.code == 404);

        sseConn.disconnect();
    }

    /** 读取一个 SSE 事件（跳过事件间的空行分隔符），返回 {eventName, data} */
    static String[] readSseEvent(BufferedReader reader) throws Exception {
        String event = null;
        String data = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (event != null || data != null) break; // 事件结束
                continue; // 事件前的空行
            }
            if (line.startsWith("event: ")) event = line.substring(7);
            else if (line.startsWith("data: ")) data = line.substring(6);
        }
        return new String[]{event, data};
    }

    static class Resp {
        int code;
        String body;
    }

    static Resp post(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        Resp r = new Resp();
        r.code = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            r.body = sb.toString();
        } catch (Exception e) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                r.body = sb.toString();
            }
        }
        conn.disconnect();
        return r;
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  ✓ " : "  ✗ ") + name);
        if (!ok) failures++;
    }
}
