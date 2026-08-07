package com.fabpilot.mescore.diagnostic.service;

import com.fabpilot.mescore.diagnostic.dto.LotDiagnosticContextTO;

/**
 * Lot 异常诊断的只读应用服务。
 *
 * <p>Service 返回业务 DTO，不感知 ApiResponse 或 HTTP 状态，保持应用层可复用。</p>
 */
public interface LotDiagnosticService {
    /**
     * 聚合 Lot、工单、当前 Step/Operation、设备和最近历史流水。
     *
     * @throws com.fabpilot.mescore.diagnostic.exception.LotNotFoundException 当 Lot 不存在时
     */
    LotDiagnosticContextTO getDiagnosticContext(String lotCode);
}
