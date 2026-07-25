package com.rudder.server.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 统一 API 错误响应，避免把堆栈直接暴露给客户端。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex) {
        String message = ex.getMessage();
        if (ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            message = manv.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", message == null ? "invalid request" : message
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", ex.getMessage() == null ? "unexpected error" : ex.getMessage()
        ));
    }
}
