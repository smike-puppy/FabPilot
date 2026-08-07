package com.fabpilot.mescore.diagnostic.exception;

import com.fabpilot.mescore.common.error.BusinessException;

/** 表示按业务编码查询不到 Lot，由全局异常处理器转换为统一的 HTTP 404 响应。 */
public class LotNotFoundException extends BusinessException {
    public LotNotFoundException(String lotCode) {
        super(DiagnosticErrorCode.LOT_NOT_FOUND, "Lot not found: " + lotCode);
    }
}
