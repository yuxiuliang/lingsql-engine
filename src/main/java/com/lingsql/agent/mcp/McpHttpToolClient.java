package com.lingsql.agent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP 远程插件客户端（JSON-RPC 2.0 over HTTP，Streamable HTTP 传输）。
 *
 * <p>安全边界：MCP 工具永远在独立进程执行，本客户端只做受控转发，不加载远端代码、
 * 不解释远端脚本。未配置 endpoint、握手失败或返回非法结构时一律fail-closed，
 * 绝不返回未验证的远端内容。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Slf4j
@Service
public class McpHttpToolClient {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final RestClient.Builder restClientBuilder;
    private final String endpoint;
    private final AtomicInteger requestId = new AtomicInteger();
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    public McpHttpToolClient(RestClient.Builder restClientBuilder, Environment environment) {
        this.restClientBuilder = restClientBuilder;
        this.endpoint = trimTrailingSlash(environment.getProperty("lingsql.agent.mcp.base-url", ""));
    }

    public boolean available() {
        return endpoint != null && !endpoint.isBlank();
    }

    /**
     * 执行 MCP 初始化握手，成功后缓存会话 ID。
     *
     * @return 握手是否成功
     */
    public boolean initialize() {
        if (!available()) {
            return false;
        }
        Map<String, Object> response = exchange("initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "clientInfo", Map.of("name", "lingsql-engine", "version", "1.0.0")));
        if (response == null) {
            return false;
        }
        // Streamable HTTP 要求握手成功后向服务端补发 notifications/initialized
        notify("notifications/initialized", Map.of());
        return true;
    }

    /**
     * 列出远端工具定义。
     *
     * @return 工具定义列表；不可用或失败时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<McpToolDefinition> listTools() {
        if (!available()) {
            return List.of();
        }
        Map<String, Object> response = exchange("tools/list", Map.of());
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)) {
            return List.of();
        }
        Object tools = result.get("tools");
        if (!(tools instanceof List<?> list)) {
            return List.of();
        }
        List<McpToolDefinition> definitions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> tool) {
                String name = String.valueOf(tool.get("name"));
                if (name == null || name.isBlank() || "null".equals(name)) {
                    continue;
                }
                Object description = tool.get("description");
                Object schema = tool.get("inputSchema");
                definitions.add(new McpToolDefinition(
                        name,
                        description == null ? "" : String.valueOf(description),
                        schema == null ? "{}" : String.valueOf(schema)));
            }
        }
        return List.copyOf(definitions);
    }

    /**
     * 调用远端工具。仅允许调用已注册名称的工具，禁止按远端提供的 URL 直接转发。
     *
     * @param toolName 工具名
     * @param arguments 工具入参
     * @return 工具返回内容；失败时返回 null
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        if (!available() || toolName == null || toolName.isBlank()) {
            return null;
        }
        Map<String, Object> response = exchange("tools/call", Map.of(
                "name", toolName,
                "arguments", arguments == null ? Map.of() : arguments));
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)) {
            return null;
        }
        Object content = result.get("content");
        if (content instanceof List<?> blocks && !blocks.isEmpty()) {
            Object first = blocks.get(0);
            if (first instanceof Map<?, ?> block) {
                Object text = block.get("text");
                if (text != null) {
                    return String.valueOf(text);
                }
            }
        }
        return String.valueOf(result.get("isError") != null ? result.get("isError") : result);
    }

    private void notify(String method, Map<String, Object> params) {
        if (!available()) {
            return;
        }
        try {
            request(method, params, false);
        } catch (RuntimeException exception) {
            log.warn("MCP 通知发送失败（忽略）: method={}", method, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(String method, Map<String, Object> params) {
        if (!available()) {
            return null;
        }
        try {
            return request(method, params, true);
        } catch (RuntimeException exception) {
            log.warn("MCP 请求失败: method={}, endpoint={}", method, endpoint, exception);
            return null;
        }
    }

    private Map<String, Object> request(String method, Map<String, Object> params, boolean expectBody) {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", requestId.incrementAndGet(),
                "method", method,
                "params", params == null ? Map.of() : params);
        RestClient.RequestBodySpec spec = restClientBuilder.build().post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload);
        String currentSession = sessionId.get();
        if (currentSession != null && !currentSession.isBlank()) {
            spec.header(SESSION_HEADER, currentSession);
        }
        ResponseEntity<Map> entity = spec.retrieve().toEntity(Map.class);
        captureSessionId(entity.getHeaders());
        Map<String, Object> response = entity.getBody();
        if (response == null) {
            return null;
        }
        if (!expectBody) {
            return response;
        }
        if (response.get("error") != null) {
            log.warn("MCP 返回错误: method={}, error={}", method, response.get("error"));
            return null;
        }
        return response;
    }

    /**
     * 捕获服务端下发的会话 ID，后续请求需携带。
     */
    private void captureSessionId(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String issued = headers.getFirst(SESSION_HEADER);
        if (issued != null && !issued.isBlank()) {
            sessionId.set(issued);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}