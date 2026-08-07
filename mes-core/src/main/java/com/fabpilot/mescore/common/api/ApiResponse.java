package com.fabpilot.mescore.common.api;

import com.fabpilot.mescore.common.error.CommonErrorCode;
import com.fabpilot.mescore.common.error.ErrorCode;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 所有 HTTP API 共用的外层响应协议。
 *
 * <p>业务模块只负责提供强类型 {@code data}；success、code、message、traceId 和
 * timestamp 由公共层统一维护，避免每个模块重复定义响应结构。</p>
 *
 * @param <T> 具体接口返回的业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String traceId;
    private Instant timestamp;

    /** 创建统一的成功响应。 */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(
                true,
                CommonErrorCode.SUCCESS.code(),
                CommonErrorCode.SUCCESS.defaultMessage(),
                data,
                traceId,
                Instant.now());
    }

    /** 创建统一的失败响应；HTTP 状态由全局异常处理器单独设置。 */
    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(
                false,
                errorCode.code(),
                message,
                null,
                traceId,
                Instant.now());
    }
}