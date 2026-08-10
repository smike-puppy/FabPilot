package com.fabpilot.mescore.equipment.exception;

import com.fabpilot.mescore.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 设备写侧命令对外暴露的稳定业务错误码。 */
public enum EquipmentCommandErrorCode implements ErrorCode {
    EQUIPMENT_NOT_FOUND("EQUIPMENT_NOT_FOUND", "设备不存在", HttpStatus.NOT_FOUND),
    EVENT_NOT_FOUND("EQUIPMENT_EVENT_NOT_FOUND", "设备事件不存在或未启用", HttpStatus.NOT_FOUND),
    REASON_REQUIRED("EQUIPMENT_EVENT_REASON_REQUIRED", "该设备事件必须填写原因", HttpStatus.BAD_REQUEST),
    STATE_INVALID("EQUIPMENT_STATE_INVALID", "设备当前状态不允许发生该事件", HttpStatus.CONFLICT),
    VERSION_CONFLICT("EQUIPMENT_VERSION_CONFLICT", "设备已被其他请求修改", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "幂等键已被其他设备事件使用", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    EquipmentCommandErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public HttpStatus httpStatus() { return httpStatus; }
}