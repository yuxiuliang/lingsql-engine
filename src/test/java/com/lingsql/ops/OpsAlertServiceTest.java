package com.lingsql.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.model.AlertDispatchResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAlertServiceTest {

    @Test
    void shouldSendGenericWebhookPayload() throws Exception {
        try (MockWebhookServer webhook = new MockWebhookServer(200)) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAlert().setEnabled(true);
            properties.getAlert().setWebhookUrl(webhook.url());
            OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

            boolean success = service.sendAlert("连接池告警", "连接池使用率过高", Map.of("utilization", 0.95));

            assertTrue(success);
            assertEquals(1, webhook.requestCount());
            assertTrue(webhook.firstBody().contains("\"app\":\"lingsql-engine\""));
            assertTrue(webhook.firstBody().contains("\"title\":\"连接池告警\""));
        }
    }

    @Test
    void shouldReturnTrueWhenAnyChannelSucceeds() throws Exception {
        try (MockWebhookServer failedWebhook = new MockWebhookServer(500);
             MockWebhookServer dingTalkWebhook = new MockWebhookServer(200)) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAlert().setEnabled(true);
            properties.getAlert().setWebhookUrl(failedWebhook.url());
            properties.getAlert().setDingTalkWebhookUrl(dingTalkWebhook.url());
            properties.getAlert().setRequireAllChannels(false);
            OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

            boolean success = service.sendAlert("告警", "测试消息", Map.of("tenantId", "t-001"));

            assertTrue(success);
            assertEquals(1, failedWebhook.requestCount());
            assertEquals(1, dingTalkWebhook.requestCount());
            assertTrue(dingTalkWebhook.firstBody().contains("\"msgtype\":\"markdown\""));
        }
    }

    @Test
    void shouldReturnFalseWhenRequireAllChannelsAndAnyFailed() throws Exception {
        try (MockWebhookServer failedWebhook = new MockWebhookServer(500);
             MockWebhookServer weComWebhook = new MockWebhookServer(200)) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAlert().setEnabled(true);
            properties.getAlert().setWebhookUrl(failedWebhook.url());
            properties.getAlert().setWeComWebhookUrl(weComWebhook.url());
            properties.getAlert().setRequireAllChannels(true);
            OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

            boolean success = service.sendAlert("告警", "测试消息", Map.of("tenantId", "t-002"));

            assertFalse(success);
            assertEquals(1, failedWebhook.requestCount());
            assertEquals(1, weComWebhook.requestCount());
            assertTrue(weComWebhook.firstBody().contains("\"msgtype\":\"markdown\""));
        }
    }

    @Test
    void shouldSendOnlySpecifiedChannels() throws Exception {
        try (MockWebhookServer webhook = new MockWebhookServer(200);
             MockWebhookServer dingTalkWebhook = new MockWebhookServer(200);
             MockWebhookServer weComWebhook = new MockWebhookServer(200)) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAlert().setEnabled(true);
            properties.getAlert().setWebhookUrl(webhook.url());
            properties.getAlert().setDingTalkWebhookUrl(dingTalkWebhook.url());
            properties.getAlert().setWeComWebhookUrl(weComWebhook.url());
            OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

            boolean success = service.sendAlertToChannels(
                    "订阅结果",
                    "测试消息",
                    Map.of("tenantId", "t-003"),
                    List.of("dingtalk", "wecom")
            );

            assertTrue(success);
            assertEquals(0, webhook.requestCount());
            assertEquals(1, dingTalkWebhook.requestCount());
            assertEquals(1, weComWebhook.requestCount());
        }
    }

    @Test
    void shouldReturnStructuredDeliveryDetailWhenChannelFailed() throws Exception {
        try (MockWebhookServer failedWebhook = new MockWebhookServer(500);
             MockWebhookServer weComWebhook = new MockWebhookServer(200)) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAlert().setEnabled(true);
            properties.getAlert().setWebhookUrl(failedWebhook.url());
            properties.getAlert().setWeComWebhookUrl(weComWebhook.url());
            properties.getAlert().setRequireAllChannels(false);
            OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

            AlertDispatchResult result = service.sendAlertWithResult("告警", "测试消息", Map.of("tenantId", "t-004"));

            assertTrue(result.success());
            assertEquals(2, result.channelResults().size());
            assertTrue(result.summarize().contains("Webhook=失败(HTTP 500)"));
            assertTrue(result.summarize().contains("企微=成功(HTTP 200)"));
        }
    }

    @Test
    void shouldExposeConfiguredAndUnconfiguredChannels() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getAlert().setEnabled(true);
        properties.getAlert().setWebhookUrl("http://127.0.0.1/test");
        properties.getAlert().setWeComWebhookUrl("http://127.0.0.1/wecom");
        OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

        assertTrue(service.isAlertEnabled());
        assertEquals(List.of("webhook", "wecom"), service.getConfiguredChannels());
        assertEquals(List.of("dingtalk"), service.getUnconfiguredChannels());
    }

    private static final class MockWebhookServer implements AutoCloseable {

        private final HttpServer server;
        private final int statusCode;
        private final AtomicInteger requestCount = new AtomicInteger();
        private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());

        private MockWebhookServer(int statusCode) throws IOException {
            this.statusCode = statusCode;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/alert", this::handle);
            this.server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requestBodies.add(new String(body, StandardCharsets.UTF_8));
            requestCount.incrementAndGet();

            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }

        private String url() {
            int port = server.getAddress().getPort();
            return "http://127.0.0.1:" + port + "/alert";
        }

        private int requestCount() {
            return requestCount.get();
        }

        private String firstBody() {
            if (requestBodies.isEmpty()) {
                return "";
            }
            return requestBodies.get(0);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
