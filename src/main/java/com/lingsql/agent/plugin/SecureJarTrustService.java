package com.lingsql.agent.plugin;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;

/**
 * 可信 JAR 插件信任校验。
 *
 * <p>校验内容：SHA-256 摘要白名单 + JAR 体积上限 + 插件声明的稳定 ID 与版本。</p>
 *
 * <p><b>安全边界</b>：本服务只做准入判断，<b>不提供 ClassLoader 沙箱</b>。
 * 白名单内的 JAR 仍与宿主进程同权限运行，因此白名单只能由人工审核后配置。
 * 不可信来源必须走独立进程 MCP/HTTP 通道。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class SecureJarTrustService {

    /** JAR 体积上限：64 MB，超出直接拒绝，避免解压炸弹。 */
    private static final long MAX_JAR_BYTES = 64L * 1024 * 1024;

    private static final String MANIFEST_PLUGIN_ID = "LingSql-Plugin-Id";
    private static final String MANIFEST_PLUGIN_VERSION = "LingSql-Plugin-Version";
    private static final String MANIFEST_PLUGIN_TYPE = "LingSql-Plugin-Type";

    private final List<String> allowedSha256;

    public SecureJarTrustService(Environment environment) {
        String value = environment.getProperty("lingssql.agent.trusted-plugin-jar-sha256",
                environment.getProperty("lingsql.agent.trusted-plugin-jar-sha256", ""));
        this.allowedSha256 = value == null || value.isBlank()
                ? List.of()
                : List.of(value.split(","));
    }

    /**
     * 校验 JAR 是否可信：摘要命中白名单、体积合规、清单声明完整。
     *
     * @param jarUrl JAR 地址
     * @return 校验结果
     */
    public TrustResult verify(URL jarUrl) {
        if (jarUrl == null) {
            return TrustResult.rejected("插件 JAR 地址为空");
        }
        try (InputStream in = jarUrl.openStream()) {
            byte[] bytes = readAllBounded(in);
            if (bytes == null) {
                return TrustResult.rejected("插件 JAR 超过体积上限: " + MAX_JAR_BYTES + " bytes");
            }
            String digest = sha256(bytes);
            if (allowedSha256.isEmpty()) {
                return TrustResult.rejected("未配置可信插件 JAR 白名单，默认拒绝所有 JAR 插件");
            }
            if (allowedSha256.stream().noneMatch(h -> h.trim().equalsIgnoreCase(digest))) {
                return TrustResult.rejected("插件 JAR 摘要不在白名单: " + digest);
            }
            Map<String, String> manifest = readManifest(bytes);
            String pluginId = manifest.get(MANIFEST_PLUGIN_ID);
            String pluginVersion = manifest.get(MANIFEST_PLUGIN_VERSION);
            String pluginType = manifest.get(MANIFEST_PLUGIN_TYPE);
            if (pluginId == null || pluginId.isBlank()) {
                return TrustResult.rejected("插件清单缺少 " + MANIFEST_PLUGIN_ID);
            }
            if (pluginVersion == null || pluginVersion.isBlank()) {
                return TrustResult.rejected("插件清单缺少 " + MANIFEST_PLUGIN_VERSION);
            }
            if (!"agent-plugin".equals(pluginType)) {
                return TrustResult.rejected("不支持的插件类型: " + pluginType);
            }
            return TrustResult.accepted(digest, pluginId, pluginVersion);
        } catch (Exception exception) {
            return TrustResult.rejected("插件 JAR 读取或校验失败: " + exception.getMessage());
        }
    }

    /**
     * 兼容旧调用：仅判断摘要是否在白名单。
     */
    public boolean trusted(URL jarUrl) {
        return verify(jarUrl).trusted();
    }

    private byte[] readAllBounded(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_JAR_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private Map<String, String> readManifest(byte[] jarBytes) {
        Map<String, String> attributes = new LinkedHashMap<>();
        try (JarInputStream jar = new JarInputStream(new java.io.ByteArrayInputStream(jarBytes))) {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return Map.of();
            }
            java.util.Map<Object, Object> mainAttributes = manifest.getMainAttributes();
            for (java.util.Map.Entry<Object, Object> attribute : mainAttributes.entrySet()) {
                attributes.put(String.valueOf(attribute.getKey()), String.valueOf(attribute.getValue()));
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return attributes;
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    /**
     * 信任校验结果。
     *
     * @param trusted  是否通过
     * @param digest   JAR SHA-256 摘要
     * @param pluginId 插件稳定 ID
     * @param version  插件版本
     * @param reason   拒绝原因
     */
    public record TrustResult(boolean trusted, String digest, String pluginId, String version, String reason) {

        static TrustResult rejected(String reason) {
            return new TrustResult(false, null, null, null, reason);
        }

        static TrustResult accepted(String digest, String pluginId, String version) {
            return new TrustResult(true, digest, pluginId, version, null);
        }
    }
}