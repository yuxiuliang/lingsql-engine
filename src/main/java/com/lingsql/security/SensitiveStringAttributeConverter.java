package com.lingsql.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 敏感字段字符串加密转换器。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class SensitiveStringAttributeConverter implements AttributeConverter<String, String> {

    private final SensitiveStringEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
