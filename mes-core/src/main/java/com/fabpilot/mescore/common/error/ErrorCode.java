package com.fabpilot.mescore.common.error;

import org.springframework.http.HttpStatus;

/**
 * 所有业务错误码遵守的最小契约。
 *
 * <p>各模块可以定义自己的枚举实现，避免一个巨大的全局枚举最终难以维护。</p>
 */
public interface ErrorCode {
    String code();
    String defaultMessage();
    HttpStatus httpStatus();
}
