package com.fabpilot.mescore.lot.exception;

import com.fabpilot.mescore.common.error.BusinessException;

/** Lot 状态机校验或并发控制失败时抛出的业务异常。 */
public class LotCommandException extends BusinessException {

    public LotCommandException(LotCommandErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}