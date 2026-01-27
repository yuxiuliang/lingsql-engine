package com.lingsql.common.trace;

import com.lingsql.common.result.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一注入 requestId 到 ApiResponse。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@RestControllerAdvice
public class ApiResponseRequestIdAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse) {
            String requestId = apiResponse.getRequestId();
            if (!StringUtils.hasText(requestId)) {
                apiResponse.setRequestId(RequestTraceContext.resolveRequestId());
            }
            return apiResponse;
        }
        return body;
    }
}
