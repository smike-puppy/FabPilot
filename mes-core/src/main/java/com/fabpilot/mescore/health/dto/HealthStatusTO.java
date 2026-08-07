package com.fabpilot.mescore.health.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 健康检查自己的业务数据，外层响应格式由 ApiResponse 负责。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthStatusTO {
    private String status;
    private String service;
}