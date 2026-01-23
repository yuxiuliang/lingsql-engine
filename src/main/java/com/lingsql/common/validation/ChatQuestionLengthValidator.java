package com.lingsql.common.validation;

import com.lingsql.config.LingSqlProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户问题长度校验器。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Component
@RequiredArgsConstructor
public class ChatQuestionLengthValidator implements ConstraintValidator<ChatQuestionLength, String> {

    private final LingSqlProperties properties;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int maxLength = properties.getChat().getMaxQuestionLength();
        if (value.length() <= maxLength) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("问题长度不能超过" + maxLength + "字")
                .addConstraintViolation();
        return false;
    }
}
