package com.lingsql.security;

import com.lingsql.config.LingSqlProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加解密器。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Component
@RequiredArgsConstructor
public class SensitiveStringEncryptor {

    private static final String PREFIX = "ENC:";
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int MIN_KEY_BYTES = 32;

    private final LingSqlProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;
    private boolean enabled;

    @PostConstruct
    public void init() {
        LingSqlProperties.Security security = properties.getSecurity();
        enabled = security != null && security.isDataEncryptionEnabled();
        if (!enabled) {
            return;
        }
        String rawKey = security == null ? null : security.getDataEncryptionKey();
        if (!StringUtils.hasText(rawKey)) {
            throw new IllegalStateException("敏感字段加密密钥未配置");
        }
        byte[] keyBytes = resolveKeyBytes(rawKey.trim());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("敏感字段加密密钥长度不足 32 字节");
        }
        secretKey = new SecretKeySpec(trimKey(keyBytes), "AES");
    }

    public String encrypt(String raw) {
        if (!StringUtils.hasText(raw) || isEncrypted(raw)) {
            return raw;
        }
        if (!enabled) {
            return raw;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherBytes, 0, payload, iv.length, cipherBytes.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("敏感字段加密失败", ex);
        }
    }

    public String decrypt(String raw) {
        if (!StringUtils.hasText(raw) || !isEncrypted(raw)) {
            return raw;
        }
        if (!enabled) {
            throw new IllegalStateException("敏感字段解密失败：未启用加密配置");
        }
        String payload = raw.substring(PREFIX.length());
        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            if (decoded.length <= IV_LENGTH) {
                throw new IllegalStateException("敏感字段解密失败：密文长度不足");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherBytes = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decoded, IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("敏感字段解密失败", ex);
        }
    }

    private boolean isEncrypted(String raw) {
        return raw.startsWith(PREFIX);
    }

    private byte[] resolveKeyBytes(String rawKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(rawKey);
            if (decoded.length > 0) {
                return decoded;
            }
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        return rawKey.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] trimKey(byte[] raw) {
        if (raw.length == MIN_KEY_BYTES) {
            return raw;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw);
        } catch (Exception ex) {
            byte[] trimmed = new byte[MIN_KEY_BYTES];
            System.arraycopy(raw, 0, trimmed, 0, Math.min(raw.length, MIN_KEY_BYTES));
            return trimmed;
        }
    }
}
