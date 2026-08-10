package com.fabpilot.mescore.alarm.controller;

import com.fabpilot.mescore.alarm.dto.AlarmActionRequestTO;
import com.fabpilot.mescore.alarm.dto.AlarmActionResultTO;
import com.fabpilot.mescore.alarm.service.EquipmentAlarmService;
import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对外提供告警确认和关闭命令。 */
@RestController
@RequestMapping("/api/equipment-alarms")
public class EquipmentAlarmController {

    @Autowired
    private EquipmentAlarmService equipmentAlarmService;

    @Autowired
    private TraceIdProvider traceIdProvider;

    /**
     * 执行一次告警动作。
     *
     * <p>控制器只负责校验请求格式和包装统一响应；状态机、恢复条件、并发和幂等规则由服务层处理。
     */
    @PostMapping("/actions")
    public ApiResponse<AlarmActionResultTO> executeAction(
            @Valid @RequestBody AlarmActionRequestTO request) {
        AlarmActionResultTO result = equipmentAlarmService.executeAction(request);
        return ApiResponse.success(result, traceIdProvider.currentTraceId());
    }
}