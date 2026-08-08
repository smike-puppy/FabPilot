package com.fabpilot.mescore.lot.controller;

import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseHoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.ReleaseLotRequestTO;
import com.fabpilot.mescore.lot.dto.ScrapLotRequestTO;
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

/**
 * Lot 写命令的 HTTP 入口。
 * Controller 只负责路径参数、JSON 反序列化、Bean Validation 和统一响应封装；
 * 状态机、事务、幂等和乐观锁全部交给 LotCommandService，避免业务规则散落在接口层。
 */
@RestController
@RequestMapping("/api/lots")
public class LotCommandController {
    @Autowired private LotCommandService lotCommandService;
    @Autowired private TraceIdProvider traceIdProvider;

    /** 将 CREATED + RELEASED 的 Lot 释放到首工序，成功后为 READY。 */
    @PostMapping("/{lotCode}/release")
    public ApiResponse<LotCommandResultTO> release(@PathVariable String lotCode,
            @Valid @RequestBody ReleaseLotRequestTO request) {
        return ApiResponse.success(lotCommandService.release(lotCode, request),
                traceIdProvider.currentTraceId());
    }

    /** 将 READY + RELEASED 的 Lot 绑定到指定可用设备并进入 RUNNING。 */
    @PostMapping("/{lotCode}/track-in")
    public ApiResponse<LotCommandResultTO> trackIn(@PathVariable String lotCode,
            @Valid @RequestBody TrackInLotRequestTO request) {
        return ApiResponse.success(lotCommandService.trackIn(lotCode, request),
                traceIdProvider.currentTraceId());
    }

    /** 将 RUNNING Lot 从当前设备下机，并推进下一工序或进入正常完工终态。 */
    @PostMapping("/{lotCode}/track-out")
    public ApiResponse<LotCommandResultTO> trackOut(@PathVariable String lotCode,
            @Valid @RequestBody TrackOutLotRequestTO request) {
        return ApiResponse.success(lotCommandService.trackOut(lotCode, request),
                traceIdProvider.currentTraceId());
    }

    /** 暂停 READY/RUNNING Lot；只改变独立 Hold 状态，不改变执行阶段和设备绑定。 */
    @PostMapping("/{lotCode}/hold")
    public ApiResponse<LotCommandResultTO> hold(@PathVariable String lotCode,
            @Valid @RequestBody HoldLotRequestTO request) {
        return ApiResponse.success(lotCommandService.hold(lotCode, request),
                traceIdProvider.currentTraceId());
    }

    /** 解除 READY/RUNNING Lot 的暂停；只执行 HELD→RELEASED。 */
    @PostMapping("/{lotCode}/release-hold")
    public ApiResponse<LotCommandResultTO> releaseHold(@PathVariable String lotCode,
            @Valid @RequestBody ReleaseHoldLotRequestTO request) {
        return ApiResponse.success(lotCommandService.releaseHold(lotCode, request),
                traceIdProvider.currentTraceId());
    }

    /** 将任意非终态 Lot 报废为 SCRAPPED，并记录不可变报废原因。 */
    @PostMapping("/{lotCode}/scrap")
    public ApiResponse<LotCommandResultTO> scrap(@PathVariable String lotCode,
            @Valid @RequestBody ScrapLotRequestTO request) {
        return ApiResponse.success(lotCommandService.scrap(lotCode, request),
                traceIdProvider.currentTraceId());
    }
}