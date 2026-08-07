package com.fabpilot.mescore.common.error;

/**
 * 可安全返回给接口调用方的业务异常基类。
 *
 * <p>异常只携带稳定错误码和可读消息，不把 SQL、堆栈或内部配置暴露到响应中。</p>
 */
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
