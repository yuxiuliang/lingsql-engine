package com.lingsql.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户问题长度校验注解。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Documented
@Constraint(validatedBy = ChatQuestionLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ChatQuestionLength {

    /**
     * 默认错误提示。
     *
     * @return 错误提示
     * @author 余炘洋
     * @date 2026-01-23
     */
    String message() default "问题长度超出限制";

    /**
     * 分组。
     *
     * @return 分组
     * @author 余炘洋
     * @date 2026-01-23
     */
    Class<?>[] groups() default {};

    /**
     * 负载信息。
     *
     * @return 负载
     * @author 余炘洋
     * @date 2026-01-23
     */
    Class<? extends Payload>[] payload() default {};
}
