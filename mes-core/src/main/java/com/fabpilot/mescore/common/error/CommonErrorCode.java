package com.fabpilot.mescore.common.error;

import org.springframework.http.HttpStatus;

/** 只存放真正跨业务模块的通用结果码。 */
public enum CommonErrorCode implements ErrorCode {
    SUCCESS("SUCCESS", "操作成功", HttpStatus.OK),
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数校验失败", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() { return code; }

    @Override
    public String defaultMessage() { return defaultMessage; }

    @Override
    public HttpStatus httpStatus() { return httpStatus; }
}
