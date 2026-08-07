package com.fabpilot.mescore.diagnostic.controller;

import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.diagnostic.dto.LotDiagnosticContextTO;
import com.fabpilot.mescore.diagnostic.service.LotDiagnosticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lots")
public class LotDiagnosticController {
    @Autowired
    private LotDiagnosticService lotDiagnosticService;

    @Autowired
    private TraceIdProvider traceIdProvider;

    /**
     * 返回一次异常分析需要的完整 Lot 上下文。
     *
     * <p>Controller 只负责 HTTP 协议和统一响应包装，诊断聚合逻辑仍由 Service 完成。</p>
     */
    @GetMapping("/{lotCode}/diagnostic-context")
    public ApiResponse<LotDiagnosticContextTO> getDiagnosticContext(
            @PathVariable String lotCode) {
        LotDiagnosticContextTO context =
                lotDiagnosticService.getDiagnosticContext(lotCode);
        return ApiResponse.success(context, traceIdProvider.currentTraceId());
    }
}
