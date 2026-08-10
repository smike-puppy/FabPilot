package com.fabpilot.mescore.equipment.controller;

import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.equipment.dto.EquipmentEventResultTO;
import com.fabpilot.mescore.equipment.dto.ExecuteEquipmentEventRequestTO;
import com.fabpilot.mescore.equipment.service.EquipmentEventService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 设备事件 HTTP 接口；设备编号和事件编号统一由请求体传递。 */
@RestController
@RequestMapping("/api/equipment-events")
public class EquipmentEventController {
    @Autowired private EquipmentEventService equipmentEventService;
    @Autowired private TraceIdProvider traceIdProvider;

    /**
     * 执行一个启用的设备事件。
     * 允许的来源状态和成功后的目标状态由数据库事件定义决定，所以该接口可处理故障、维护和生产事件。
     */
    @PostMapping
    public ApiResponse<EquipmentEventResultTO> executeEvent(
            @Valid @RequestBody ExecuteEquipmentEventRequestTO request) {
        return ApiResponse.success(equipmentEventService.executeEvent(request),
                traceIdProvider.currentTraceId());
    }
}