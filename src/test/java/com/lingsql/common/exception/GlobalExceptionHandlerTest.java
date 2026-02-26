package com.lingsql.common.exception;

import com.lingsql.common.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void noResourceFoundShouldReturnNotFoundResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiResponse<Void> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/static/admin/index.html")
        );

        assertEquals(404, response.getCode());
        assertEquals("资源不存在", response.getMessage());
    }
}

