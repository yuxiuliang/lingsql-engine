package com.lingsql.agent.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 远程插件客户端单元测试：握手、会话透传、工具解析与 fail-closed。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class McpHttpToolClientTest {

    private final MockEnvironment environment = new MockEnvironment();

    @Test
    void unavailableWhenEndpointBlank() {
        McpHttpToolClient client = new McpHttpToolClient(RestClient.builder(), environment);
        assertFalse(client.available());
        assertTrue(client.listTools().isEmpty());
        assertNull(client.callTool("anything", Map.of()));
        assertFalse(client.initialize());
    }

    @Test
    void listToolsParsesDefinitions() {
        environment.setProperty("lingsql.agent.mcp.base-url", "http://localhost:9000/mcp/");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = bindAndGet(builder);
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9000/mcp"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"jsonrpc":"2.0","id":1,"result":{"tools":[
                          {"name":"fetch_report","description":"拉取报表","inputSchema":{"type":"object"}},
                          {"name":"","description":"非法工具"},
                          {"nope":1}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        McpHttpToolClient client = new McpHttpToolClient(builder, environment);
        List<McpToolDefinition> tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("fetch_report", tools.get(0).name());
        assertEquals("拉取报表", tools.get(0).description());
        assertTrue(tools.get(0).inputSchema().contains("object"));
        server.verify();
    }

    @Test
    void listToolsReturnsEmptyOnRpcError() {
        environment.setProperty("lingsql.agent.mcp.base-url", "http://localhost:9000/mcp");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = bindAndGet(builder);
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9000/mcp"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"method not found\"}}",
                        MediaType.APPLICATION_JSON));
        McpHttpToolClient client = new McpHttpToolClient(builder, environment);
        assertTrue(client.listTools().isEmpty());
        server.verify();
    }

    @Test
    void initializeCapturesSessionIdAndSendsInitializedNotification() {
        environment.setProperty("lingsql.agent.mcp.base-url", "http://localhost:9000/mcp");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = bindAndGet(builder);
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9000/mcp"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.OK)
                        .header("Mcp-Session-Id", "sess-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}"));
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9000/mcp"))
                .andExpect(MockRestRequestMatchers.header("Mcp-Session-Id", "sess-42"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.ACCEPTED));
        McpHttpToolClient client = new McpHttpToolClient(builder, environment);
        assertTrue(client.initialize());
        server.verify();
    }

    @Test
    void callToolExtractsTextContent() {
        environment.setProperty("lingsql.agent.mcp.base-url", "http://localhost:9000/mcp");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = bindAndGet(builder);
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9000/mcp"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"报表已生成\"}]}}",
                        MediaType.APPLICATION_JSON));
        McpHttpToolClient client = new McpHttpToolClient(builder, environment);
        assertEquals("报表已生成", client.callTool("fetch_report", Map.of("id", 1)));
        server.verify();
    }

    @Test
    void callToolRejectsBlankToolName() {
        environment.setProperty("lingsql.agent.mcp.base-url", "http://localhost:9000/mcp");
        McpHttpToolClient client = new McpHttpToolClient(RestClient.builder(), environment);
        assertNull(client.callTool("  ", Map.of()));
        assertNull(client.callTool(null, Map.of()));
    }

    private MockRestServiceServer bindAndGet(RestClient.Builder builder) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return server;
    }
}