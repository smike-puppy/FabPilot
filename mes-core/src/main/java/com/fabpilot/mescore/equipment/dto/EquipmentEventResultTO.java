package com.fabpilot.mescore.equipment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 设备事件执行结果，返回事件后的设备状态、版本以及是否为幂等重放。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentEventResultTO {
    private String equipmentCode;
    private String eventCode;
    private String upDownStatus;
    private String primaryStatus;
    private Long version;
    private boolean idempotent;
}