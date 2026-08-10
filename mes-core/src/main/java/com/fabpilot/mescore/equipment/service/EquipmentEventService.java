package com.fabpilot.mescore.equipment.service;

import com.fabpilot.mescore.equipment.dto.EquipmentEventResultTO;
import com.fabpilot.mescore.equipment.dto.ExecuteEquipmentEventRequestTO;

/** 设备事件写服务；所有状态变化都经过状态机、版本、幂等和历史保护。 */
public interface EquipmentEventService {
    EquipmentEventResultTO executeEvent(ExecuteEquipmentEventRequestTO request);
}