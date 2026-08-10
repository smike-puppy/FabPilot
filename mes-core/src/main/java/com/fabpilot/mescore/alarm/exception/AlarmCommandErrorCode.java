package com.fabpilot.mescore.alarm.exception;

import com.fabpilot.mescore.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 告警命令对外暴露的稳定错误码，便于 Postman、前端和 Agent 按 code 判断失败原因。 */
public enum AlarmCommandErrorCode implements ErrorCode {
    ALARM_NOT_FOUND("ALARM_NOT_FOUND", "告警不存在", HttpStatus.NOT_FOUND),
    STATE_INVALID("ALARM_STATE_INVALID", "告警当前状态不允许执行该动作", HttpStatus.CONFLICT),
    VERSION_CONFLICT("ALARM_VERSION_CONFLICT", "告警已被其他请求修改", HttpStatus.CONFLICT),
    EQUIPMENT_NOT_RECOVERED("EQUIPMENT_NOT_RECOVERED", "设备尚未恢复，不能关闭告警", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "幂等键已被其他告警动作使用", HttpStatus.CONFLICT);
    private final String code; private final String message; private final HttpStatus status;
    AlarmCommandErrorCode(String code,String message,HttpStatus status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;} public String defaultMessage(){return message;} public HttpStatus httpStatus(){return status;}
}