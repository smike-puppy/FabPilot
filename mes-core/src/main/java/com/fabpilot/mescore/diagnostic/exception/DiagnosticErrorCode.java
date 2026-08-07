package com.fabpilot.mescore.diagnostic.exception;

import com.fabpilot.mescore.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 诊断模块自己的错误码，避免业务错误不断挤入公共枚举。 */
public enum DiagnosticErrorCode implements ErrorCode {
    LOT_NOT_FOUND("LOT_NOT_FOUND", "Lot 不存在", HttpStatus.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    DiagnosticErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
