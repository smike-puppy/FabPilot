package com.fabpilot.mescore.lot.exception;

import com.fabpilot.mescore.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Lot 写侧命令对外暴露的稳定业务错误码。 */
public enum LotCommandErrorCode implements ErrorCode {
    LOT_NOT_FOUND("LOT_NOT_FOUND", "Lot 不存在", HttpStatus.NOT_FOUND),
    LOT_STATE_INVALID("LOT_STATE_INVALID", "Lot 当前状态不允许执行该操作", HttpStatus.CONFLICT),
    LOT_VERSION_CONFLICT("LOT_VERSION_CONFLICT", "Lot 已被其他请求修改", HttpStatus.CONFLICT),
    EQUIPMENT_NOT_FOUND("EQUIPMENT_NOT_FOUND", "设备不存在", HttpStatus.NOT_FOUND),
    EQUIPMENT_STATE_INVALID("EQUIPMENT_STATE_INVALID", "设备当前状态不允许上机", HttpStatus.CONFLICT),
    EQUIPMENT_CAPABILITY_MISMATCH(
            "EQUIPMENT_CAPABILITY_MISMATCH", "设备能力与当前工序不匹配", HttpStatus.CONFLICT),
    EQUIPMENT_OCCUPIED("EQUIPMENT_OCCUPIED", "设备已被其他 Lot 占用", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "幂等键已被其他命令使用", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    LotCommandErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}