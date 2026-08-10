package com.fabpilot.mescore.alarm.exception;
import com.fabpilot.mescore.common.error.BusinessException;
/** 告警状态、版本、恢复条件或幂等校验失败时抛出的业务异常。 */
public class AlarmCommandException extends BusinessException {
    public AlarmCommandException(AlarmCommandErrorCode code,String message){super(code,message);}
}