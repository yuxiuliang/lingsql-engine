package com.lingsql.schema;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.schema.model.TableInfo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Schema 白名单收据工厂，集中定义规范化、完整命中与摘要规则。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Component
class SchemaWhitelistReceiptFactory {

    Snapshot verify(String configuredWhitelist, List<TableInfo> tables) {
        Set<String> whitelist = normalizeWhitelist(configuredWhitelist);
        if (whitelist.isEmpty()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "Schema 同步要求至少配置一个白名单表");
        }
        Set<String> actualTables = new TreeSet<>();
        if (tables != null) {
            tables.stream()
                    .map(TableInfo::getTableName)
                    .filter(StringUtils::hasText)
                    .map(name -> name.trim().toLowerCase())
                    .forEach(actualTables::add);
        }
        Set<String> missingTables = new TreeSet<>(whitelist);
        missingTables.removeAll(actualTables);
        if (actualTables.isEmpty() || !missingTables.isEmpty()) {
            String suffix = missingTables.isEmpty() ? "" : "，未命中: " + String.join(", ", missingTables);
            throw new BizException(ErrorCode.CONFIG_INVALID, "Schema 白名单未完整命中" + suffix);
        }
        String canonical = String.join("\n", whitelist);
        return new Snapshot(actualTables.size(), whitelist.size(), sha256(canonical));
    }

    private Set<String> normalizeWhitelist(String configuredWhitelist) {
        Set<String> values = new TreeSet<>();
        if (!StringUtils.hasText(configuredWhitelist)) {
            return values;
        }
        for (String item : configuredWhitelist.split(",")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim().toLowerCase());
            }
        }
        return values;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    record Snapshot(int tableCount, int whitelistTableCount, String whitelistDigest) {
    }
}
