package com.lingsql.datasource;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 租户数据库连接地址校验器，收敛 SSRF、驱动注入与危险连接参数风险。
 *
 * <p>校验范围：仅允许 MySQL JDBC 地址；主机名、端口必须符合格式要求；
 * 地址中不得携带账号密码；禁止本地文件读取、反序列化、多语句与自定义类等危险连接参数；
 * 禁止链路本地地址（含云元数据地址）与未指定地址；支持可选的数据库主机白名单。</p>
 *
 * <p>主机白名单由 {@code lingsql.datasource.host-allowlist} 配置，为空时不额外限制，
 * 配置后必须命中精确主机名/IP 或 {@code *.example.com} 形式的通配项。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class TenantJdbcUrlValidator {

    /**
     * 默认且唯一允许的 MySQL 官方驱动类。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    public static final String DEFAULT_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static final Set<String> ALLOWED_DRIVER_CLASSES = Set.of(DEFAULT_DRIVER_CLASS);

    private static final String MYSQL_URL_PREFIX = "jdbc:mysql://";

    private static final int MIN_PORT = 1;

    private static final int MAX_PORT = 65535;

    private static final int MIN_HOST_TEXT_LENGTH = 1;

    private static final int MAX_HOST_TEXT_LENGTH = 253;

    private static final int MAX_HOST_LABEL_LENGTH = 63;

    private static final int MAX_IPV6_TEXT_LENGTH = 45;

    private static final Pattern IPV4_PATTERN = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");

    private static final Pattern PORT_PATTERN = Pattern.compile("\\d{1,5}");

    /**
     * 凭据类连接参数，禁止出现在地址中。
     */
    private static final Pattern SENSITIVE_JDBC_PROPERTY = Pattern.compile(
            "(?:^|[?&;(])\\s*(?:password|passwd|pwd|user|username|credential|token|secret)\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 危险连接参数：本地文件读取、反序列化、多语句、自定义类加载与本地文件读取。
     */
    private static final Pattern DENIED_JDBC_PROPERTY = Pattern.compile(
            "(?:^|[?&;(])\\s*(?:autoDeserialize|allowLoadLocalInfile|allowLoadLocalInfileInPath"
                    + "|allowUrlInLocalInfile|useLocalInfile|allowMultiQueries|multiQueries"
                    + "|socketFactory|statementInterceptors|connectionLifecycleInterceptors"
                    + "|propertiesTransform|trustCertificateKeyStoreUrl|clientCertificateKeyStoreUrl"
                    + "|serverRSAPublicKeyFile)\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    private final LingSqlProperties properties;

    /**
     * 校验 JDBC 地址，不合法时抛出配置错误。
     *
     * @param jdbcUrl JDBC 连接地址
     * @author 余炘洋
     * @date 2026-10-17
     */
    public void validate(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库连接地址不能为空");
        }
        String trimmed = jdbcUrl.trim();
        if (!trimmed.regionMatches(true, 0, MYSQL_URL_PREFIX, 0, MYSQL_URL_PREFIX.length())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "当前仅支持 MySQL JDBC 地址");
        }
        String remainder = trimmed.substring(MYSQL_URL_PREFIX.length());
        int queryIndex = remainder.indexOf('?');
        String location = queryIndex < 0 ? remainder : remainder.substring(0, queryIndex);
        if (location.indexOf('#') >= 0 || location.indexOf('?') >= 0) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库连接地址格式不合法");
        }
        int slashIndex = location.indexOf('/');
        String authority = slashIndex < 0 ? location : location.substring(0, slashIndex);
        if (!StringUtils.hasText(authority)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库连接地址缺少主机名");
        }
        if (authority.contains("@")) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "JDBC 地址不得携带数据库账号或密码");
        }
        if (SENSITIVE_JDBC_PROPERTY.matcher(trimmed).find()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "JDBC 地址不得携带数据库账号或密码");
        }
        if (DENIED_JDBC_PROPERTY.matcher(trimmed).find()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "JDBC 地址包含禁止使用的危险连接参数");
        }
        String host = resolveHost(authority);
        verifyHostAllowlist(host);
    }

    /**
     * 解析并校验驱动类，仅允许 MySQL 官方驱动。
     *
     * @param requestedDriverClass 请求指定的驱动类，可为空
     * @return 生效的驱动类
     * @author 余炘洋
     * @date 2026-10-17
     */
    public String resolveDriverClass(String requestedDriverClass) {
        if (!StringUtils.hasText(requestedDriverClass)) {
            return DEFAULT_DRIVER_CLASS;
        }
        String normalized = requestedDriverClass.trim();
        if (!ALLOWED_DRIVER_CLASSES.contains(normalized)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "当前仅支持 MySQL 官方驱动 " + DEFAULT_DRIVER_CLASS);
        }
        return normalized;
    }

    private String resolveHost(String authority) {
        if (authority.startsWith("[")) {
            int endIndex = authority.indexOf(']');
            if (endIndex < 0) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法");
            }
            String ipv6Text = authority.substring(1, endIndex);
            verifyPort(authority.substring(endIndex + 1));
            return normalizeIpv6(ipv6Text);
        }
        int colonIndex = authority.indexOf(':');
        if (colonIndex < 0) {
            verifyHostText(authority);
            return authority.toLowerCase(Locale.ROOT);
        }
        if (authority.indexOf(':', colonIndex + 1) >= 0) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "IPv6 主机地址必须使用方括号包裹");
        }
        verifyPort(authority.substring(colonIndex));
        String host = authority.substring(0, colonIndex);
        verifyHostText(host);
        return host.toLowerCase(Locale.ROOT);
    }

    private void verifyHostText(String host) {
        if (!StringUtils.hasText(host)
                || host.length() < MIN_HOST_TEXT_LENGTH
                || host.length() > MAX_HOST_TEXT_LENGTH) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法");
        }
        if (IPV4_PATTERN.matcher(host).matches()) {
            verifyIpv4(host);
            return;
        }
        // 逐标签校验，避免正则嵌套量词带来的多项式回溯风险。
        for (String label : host.split("\\.", -1)) {
            if (!isHostLabel(label)) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法");
            }
        }
    }

    private boolean isHostLabel(String label) {
        if (label.isEmpty() || label.length() > MAX_HOST_LABEL_LENGTH) {
            return false;
        }
        for (int index = 0; index < label.length(); index++) {
            char current = label.charAt(index);
            if (isHostLabelCharacter(current)) {
                continue;
            }
            boolean hyphenInside = current == '-' && index > 0 && index < label.length() - 1;
            if (!hyphenInside) {
                return false;
            }
        }
        return true;
    }

    private boolean isHostLabelCharacter(char value) {
        return isLowerCaseLetter(value) || isUpperCaseLetter(value) || isDigit(value) || value == '_';
    }

    private void verifyIpv4(String host) {
        String[] segments = host.split("\\.");
        for (String segment : segments) {
            if (Integer.parseInt(segment) > 255) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法");
            }
        }
        verifyLiteralAddress(host);
    }

    private String normalizeIpv6(String ipv6Text) {
        if (!isIpv6Literal(ipv6Text)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法");
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(ipv6Text);
        } catch (UnknownHostException ex) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法", ex);
        }
        verifyAddressScope(address);
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断文本是否为 IPv6 字面量：仅由十六进制字符与冒号组成且至少包含一个冒号。
     * 逐字符校验可避免正则回溯，并确保 {@link InetAddress#getByName} 不会触发域名解析。
     *
     * @param text 主机文本
     * @return 是否为 IPv6 字面量
     * @author 余炘洋
     * @date 2026-10-17
     */
    private boolean isIpv6Literal(String text) {
        if (!StringUtils.hasText(text) || text.length() > MAX_IPV6_TEXT_LENGTH || text.indexOf(':') < 0) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == ':' || isHexDigit(current)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isHexDigit(char value) {
        return isDigit(value) || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F');
    }

    private boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private boolean isLowerCaseLetter(char value) {
        return value >= 'a' && value <= 'z';
    }

    private boolean isUpperCaseLetter(char value) {
        return value >= 'A' && value <= 'Z';
    }

    private void verifyLiteralAddress(String literal) {
        try {
            verifyAddressScope(InetAddress.getByName(literal));
        } catch (UnknownHostException ex) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机地址格式不合法", ex);
        }
    }

    private void verifyAddressScope(InetAddress address) {
        if (address.isAnyLocalAddress()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "不允许连接未指定地址");
        }
        if (address.isLinkLocalAddress()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "不允许连接链路本地地址");
        }
    }

    private void verifyPort(String portSuffix) {
        if (!StringUtils.hasText(portSuffix)) {
            return;
        }
        if (portSuffix.charAt(0) != ':' || portSuffix.length() == 1) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库端口格式不合法");
        }
        String portText = portSuffix.substring(1);
        if (!PORT_PATTERN.matcher(portText).matches()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库端口格式不合法");
        }
        int port = Integer.parseInt(portText);
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库端口必须在 1 到 65535 之间");
        }
    }

    private void verifyHostAllowlist(String host) {
        List<String> allowlist = properties == null || properties.getDatasource() == null
                ? null
                : properties.getDatasource().getHostAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return;
        }
        for (String entry : allowlist) {
            if (matchesAllowlistEntry(host, entry)) {
                return;
            }
        }
        throw new BizException(ErrorCode.CONFIG_INVALID, "数据库主机不在允许列表中：" + host);
    }

    private boolean matchesAllowlistEntry(String host, String entry) {
        if (!StringUtils.hasText(entry)) {
            return false;
        }
        String normalized = entry.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("*.")) {
            String suffix = normalized.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(normalized);
    }
}
