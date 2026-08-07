package com.fabpilot.mescore.health;

import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.health.dto.HealthStatusTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供 MES Core 的基础存活状态。 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    @Autowired
    private TraceIdProvider traceIdProvider;

    @GetMapping
    public ApiResponse<HealthStatusTO> health() {
        HealthStatusTO status = new HealthStatusTO("UP", "mes-core");
        return ApiResponse.success(status, traceIdProvider.currentTraceId());
    }
}
