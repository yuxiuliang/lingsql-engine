package com.lingsql.datasource;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 租户数据库连接地址校验器测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class TenantJdbcUrlValidatorTest {

    @Test
    void validateShouldAcceptSupportedMysqlUrls() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://db.example.com:3306/sales"));
        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://mysql/order_db"));
        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://10.0.0.5:3307/order_db"));
        assertDoesNotThrow(() -> validator.validate(
                "jdbc:mysql://db.example.com:3306/sales?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://[::1]:3306/sales"));
        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://[fd00:0:0:0:0:0:0:1]/sales"));
    }

    @Test
    void validateShouldRejectNonMysqlUrl() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertEquals(
                ErrorCode.CONFIG_INVALID,
                assertThrows(BizException.class, () -> validator.validate("jdbc:postgresql://db:5432/sales")).getErrorCode()
        );
        assertEquals(
                ErrorCode.CONFIG_INVALID,
                assertThrows(BizException.class, () -> validator.validate("http://169.254.169.254/latest")).getErrorCode()
        );
        assertEquals(
                ErrorCode.CONFIG_INVALID,
                assertThrows(BizException.class, () -> validator.validate(" ")).getErrorCode()
        );
    }

    @Test
    void validateShouldRejectCredentialInUrl() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://root:pwd@db.example.com:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://db.example.com:3306/sales?password=123"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://db.example.com:3306/sales;user=root"));
    }

    @Test
    void validateShouldRejectDangerousJdbcProperties() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertThrows(BizException.class,
                () -> validator.validate("jdbc:mysql://db:3306/sales?allowLoadLocalInfile=true"));
        assertThrows(BizException.class,
                () -> validator.validate("jdbc:mysql://db:3306/sales?autoDeserialize=true"));
        assertThrows(BizException.class,
                () -> validator.validate("jdbc:mysql://db:3306/sales?socketFactory=com.example.Evil"));
        assertThrows(BizException.class,
                () -> validator.validate("jdbc:mysql://db:3306/sales?allowMultiQueries=true"));
        assertThrows(BizException.class,
                () -> validator.validate("jdbc:mysql://db:3306/sales?statementInterceptors=com.example.Evil"));
        assertThrows(BizException.class,
                () -> validator.validate("jdbc:mysql://db:3306/sales?serverRSAPublicKeyFile=/etc/passwd"));
    }

    @Test
    void validateShouldRejectLinkLocalAndUnspecifiedAddress() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://169.254.169.254:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://0.0.0.0:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://[fe80::1]:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://[::]:3306/sales"));
    }

    @Test
    void validateShouldRejectMalformedHostOrPort() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://db example.com:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://db.example.com:0/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://db.example.com:70000/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://db.example.com:abc/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://a:b:c/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://[::1:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://999.1.1.1:3306/sales"));
    }

    @Test
    void validateShouldEnforceHostAllowlistWhenConfigured() {
        TenantJdbcUrlValidator validator = createValidator(List.of("db.example.com", "*.corp.internal"));

        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://db.example.com:3306/sales"));
        assertDoesNotThrow(() -> validator.validate("jdbc:mysql://mysql01.corp.internal:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://other.example.com:3306/sales"));
        assertThrows(BizException.class, () -> validator.validate("jdbc:mysql://corp.internal:3306/sales"));
    }

    @Test
    void resolveDriverClassShouldFailClosedOnUnknownClass() {
        TenantJdbcUrlValidator validator = createValidator(List.of());

        assertEquals(TenantJdbcUrlValidator.DEFAULT_DRIVER_CLASS, validator.resolveDriverClass(null));
        assertEquals(TenantJdbcUrlValidator.DEFAULT_DRIVER_CLASS, validator.resolveDriverClass(" "));
        assertEquals(
                TenantJdbcUrlValidator.DEFAULT_DRIVER_CLASS,
                validator.resolveDriverClass("com.mysql.cj.jdbc.Driver")
        );
        assertThrows(BizException.class, () -> validator.resolveDriverClass("com.mysql.jdbc.Driver"));
        assertThrows(BizException.class, () -> validator.resolveDriverClass("javax.naming.InitialContext"));
    }

    private TenantJdbcUrlValidator createValidator(List<String> hostAllowlist) {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getDatasource().setHostAllowlist(new ArrayList<>(hostAllowlist));
        return new TenantJdbcUrlValidator(properties);
    }
}
