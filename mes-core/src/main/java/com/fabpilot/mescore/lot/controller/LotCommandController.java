package com.fabpilot.mescore.lot.controller;

import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;
import com.fabpilot.mescore.lot.service.LotCommandService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供 Lot 状态变更接口，业务规则统一交由写侧 Service 执行。 */
@RestController
@RequestMapping("/api/lots")
public class LotCommandController {

    @Autowired
    private LotCommandService lotCommandService;

    @Autowired
    private TraceIdProvider traceIdProvider;

    /**
     * Release 只负责接收并校验 HTTP 输入，事务和状态机逻辑不放在 Controller。
     */
    @PostMapping("/{lotCode}/release")
    public ApiResponse<LotCommandResultTO> release(
            @PathVariable String lotCode,
            @Valid @RequestBody ReleaseLotRequestTO request) {
        LotCommandResultTO result = lotCommandService.release(lotCode, request);
        return ApiResponse.success(result, traceIdProvider.currentTraceId());
    }

    /** Track In 接收目标设备，实际状态机与双快照更新由事务 Service 完成。 */
    @PostMapping("/{lotCode}/track-in")
    public ApiResponse<LotCommandResultTO> trackIn(
            @PathVariable String lotCode,
            @Valid @RequestBody TrackInLotRequestTO request) {
        LotCommandResultTO result = lotCommandService.trackIn(lotCode, request);
        return ApiResponse.success(result, traceIdProvider.currentTraceId());
    }

    /** Track Out 由 Lot 当前绑定关系确定设备，并在事务中推进工艺路线。 */
    @PostMapping("/{lotCode}/track-out")
    public ApiResponse<LotCommandResultTO> trackOut(
            @PathVariable String lotCode,
            @Valid @RequestBody TrackOutLotRequestTO request) {
        LotCommandResultTO result = lotCommandService.trackOut(lotCode, request);
        return ApiResponse.success(result, traceIdProvider.currentTraceId());
    }
}