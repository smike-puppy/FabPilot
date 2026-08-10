package com.fabpilot.mescore.equipment.exception;

import com.fabpilot.mescore.common.error.BusinessException;

/** 设备事件状态机、幂等或并发校验失败时抛出的业务异常。 */
public class EquipmentCommandException extends BusinessException {
    public EquipmentCommandException(EquipmentCommandErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}