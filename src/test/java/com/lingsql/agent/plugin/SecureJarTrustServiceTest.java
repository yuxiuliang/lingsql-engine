package com.lingsql.agent.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可信 JAR 准入校验单元测试。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class SecureJarTrustServiceTest {

    private Path tempJar;

    private byte[] buildJar(String pluginId, String version, String type, byte[] payload) throws Exception {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (pluginId != null) {
            main.putValue("LingSql-Plugin-Id", pluginId);
        }
        if (version != null) {
            main.putValue("LingSql-Plugin-Version", version);
        }
        if (type != null) {
            main.putValue("LingSql-Plugin-Type", type);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out, manifest)) {
            jar.putNextEntry(new JarEntry("plugin/payload.bin"));
            jar.write(payload);
            jar.closeEntry();
        }
        return out.toByteArray();
    }

    private void writeTempJar(byte[] bytes) throws Exception {
        tempJar = Files.createTempFile("lingsql-plugin", ".jar");
        Files.write(tempJar, bytes);
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder builder = new StringBuilder();
        for (byte b : digest.digest(bytes)) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private SecureJarTrustService service(String allowlist) {
        MockEnvironment environment = new MockEnvironment();
        if (allowlist != null) {
            environment.setProperty("lingsql.agent.trusted-plugin-jar-sha256", allowlist);
        }
        return new SecureJarTrustService(environment);
    }

    @Test
    void rejectsEverythingWhenAllowlistEmpty() throws Exception {
        byte[] jar = buildJar("p1", "1.0.0", "agent-plugin", "x".getBytes());
        writeTempJar(jar);
        var result = service(null).verify(tempJar.toUri().toURL());
        assertFalse(result.trusted());
        assertTrue(result.reason().contains("未配置"));
    }

    @Test
    void acceptsJarWhoseDigestIsAllowlisted() throws Exception {
        byte[] jar = buildJar("p1", "1.0.0", "agent-plugin", "x".getBytes());
        writeTempJar(jar);
        String allowlist = sha256(jar);
        var result = service(allowlist).verify(tempJar.toUri().toURL());
        assertTrue(result.trusted(), () -> "拒绝原因: " + result.reason());
        assertEquals("p1", result.pluginId());
        assertEquals("1.0.0", result.version());
        assertEquals(allowlist, result.digest());
    }

    @Test
    void rejectsJarWhoseDigestIsNotAllowlisted() throws Exception {
        byte[] jar = buildJar("p1", "1.0.0", "agent-plugin", "x".getBytes());
        writeTempJar(jar);
        var result = service("0000000000000000000000000000000000000000000000000000000000000000")
                .verify(tempJar.toUri().toURL());
        assertFalse(result.trusted());
        assertTrue(result.reason().contains("不在白名单"));
    }

    @Test
    void rejectsJarMissingPluginId() throws Exception {
        byte[] jar = buildJar(null, "1.0.0", "agent-plugin", "x".getBytes());
        writeTempJar(jar);
        var result = service(sha256(jar)).verify(tempJar.toUri().toURL());
        assertFalse(result.trusted());
        assertTrue(result.reason().contains("Plugin-Id"));
    }

    @Test
    void rejectsJarMissingVersion() throws Exception {
        byte[] jar = buildJar("p1", null, "agent-plugin", "x".getBytes());
        writeTempJar(jar);
        var result = service(sha256(jar)).verify(tempJar.toUri().toURL());
        assertFalse(result.trusted());
        assertTrue(result.reason().contains("Plugin-Version"));
    }

    @Test
    void rejectsUnsupportedPluginType() throws Exception {
        byte[] jar = buildJar("p1", "1.0.0", "sql-plugin", "x".getBytes());
        writeTempJar(jar);
        var result = service(sha256(jar)).verify(tempJar.toUri().toURL());
        assertFalse(result.trusted());
        assertTrue(result.reason().contains("不支持的插件类型"));
    }

    @Test
    void rejectsNullUrl() {
        assertFalse(service(null).verify(null).trusted());
    }
}