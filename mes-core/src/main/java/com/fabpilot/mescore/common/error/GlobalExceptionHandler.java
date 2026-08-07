package com.fabpilot.mescore.common.error;

import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将所有模块的异常统一转换为 ApiResponse，同时保留正确的 HTTP 状态。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private TraceIdProvider traceIdProvider;

    /** 业务异常记录为 WARN，并向调用方返回稳定的业务错误码。 */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        String traceId = traceIdProvider.currentTraceId();
        log.warn(
                "Business request rejected: method={}, path={}, code={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.code(),
                exception.getMessage());
        return ResponseEntity.status(errorCode.httpStatus()).body(
                ApiResponse.failure(errorCode, exception.getMessage(), traceId));
    }

    /** 请求体字段校验失败时返回 400，不把客户端输入错误误报为服务端异常。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        String traceId = traceIdProvider.currentTraceId();
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(CommonErrorCode.VALIDATION_ERROR.defaultMessage());
        log.warn(
                "Request validation rejected: method={}, path={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                message);
        return ResponseEntity.badRequest().body(
                ApiResponse.failure(CommonErrorCode.VALIDATION_ERROR, message, traceId));
    }

    /** 未预期异常记录完整堆栈，客户端只收到不含内部细节的通用文案。 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        String traceId = traceIdProvider.currentTraceId();
        log.error(
                "Unhandled request error: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus()).body(
                ApiResponse.failure(
                        CommonErrorCode.INTERNAL_ERROR,
                        CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                        traceId));
    }
}