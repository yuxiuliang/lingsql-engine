package com.lingsql.ops;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.model.AlertDispatchResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    /**
     * 客户群可见的推送正文不得携带任何内部字段。
     *
     * <p>钉钉、企微与通用 Webhook 都会把 details 整块渲染进消息正文，而详情块直接下发到
     * 客户群聊。此处以 DigitalEmployeeService#buildPushDetails 修复后的实际产出为输入，
     * 断言三条通道的正文都只包含客户可见事实，防止后续往推送里重新加回租户标识、
     * 内部任务标识、触发来源、通道配置或生成 SQL。</p>
     */
    @Test
    void shouldNotLeakInternalFieldsIntoCustomerFacingPush() throws Exception {
        try (MockWebhookServer webhook = new MockWebhookServer(200);
             MockWebhookServer dingTalk = new MockWebhookServer(200);
             MockWebhookServer weCom = new MockWebhookServer(200)) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAlert().setEnabled(true);
            properties.getAlert().setWebhookUrl(webhook.url());
            properties.getAlert().setDingTalkWebhookUrl(dingTalk.url());
            properties.getAlert().setWeComWebhookUrl(weCom.url());
            OpsAlertService service = new OpsAlertService(properties, new ObjectMapper());

            Map<String, Object> customerDetails = new LinkedHashMap<>();
            customerDetails.put("executeTime", LocalDateTime.of(2026, 10, 24, 8, 0));
            customerDetails.put("resultRows", 1);
            customerDetails.put("previewRows", List.of("销售额=3037.11", "订单数=10"));

            AlertDispatchResult result = service.sendAlertToChannelsWithResult(
                    "【昨日经营快报】10-24",
                    "销售额 3,037 元，订单 10 单",
                    customerDetails,
                    List.of("webhook", "dingtalk", "wecom"),
                    "INFO");

            assertTrue(result.success());
            List<String> forbiddenFields = List.of("tenantId", "employeeId", "triggerSource", "pushChannels", "SELECT");
            for (MockWebhookServer target : List.of(webhook, dingTalk, weCom)) {
                assertEquals(1, target.requestCount());
                String body = target.firstBody();
                assertTrue(body.contains("销售额=3037.11"), "结果预览必须送达客户");
                for (String forbidden : forbiddenFields) {
                    assertFalse(body.contains(forbidden), "客户推送不得包含内部字段: " + forbidden);
                }
            }
            // 客户可见级别（INFO）下，markdown 通道只渲染业务预览，工程字段不得出现在群消息正文里
            for (MockWebhookServer target : List.of(dingTalk, weCom)) {
                String body = target.firstBody();
                assertFalse(body.contains("executeTime"), "群消息正文不得出现 executeTime");
                assertFalse(body.contains("resultRows"), "群消息正文不得出现 resultRows");
            }
            // 通用 webhook 是给机器读的 JSON，必须保留完整 details
            assertTrue(webhook.firstBody().contains("executeTime"), "webhook 需保留完整 details");
        }
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
