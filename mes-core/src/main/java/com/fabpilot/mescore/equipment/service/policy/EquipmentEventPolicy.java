package com.fabpilot.mescore.equipment.service.policy;

import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentEventDefinition;
import org.springframework.util.StringUtils;

/** 设备事件定义与当前设备快照之间的确定性规则。 */
public final class EquipmentEventPolicy {
    private EquipmentEventPolicy() {
    }

    /** requiresReason=true 时，原因码和原因说明必须成对提供。 */
    public static boolean isReasonSatisfied(
            EquipmentEventDefinition definition,
            String reasonCode,
            String reasonText) {
        return !Boolean.TRUE.equals(definition.getRequiresReason())
                || StringUtils.hasText(reasonCode) && StringUtils.hasText(reasonText);
    }

    public static boolean isUpDownSourceAllowed(
            Equipment equipment,
            EquipmentEventDefinition definition) {
        return definition.getFromUpDownStatus() == null
                || definition.getFromUpDownStatus().equals(equipment.getUpDownStatus());
    }

    public static boolean isPrimarySourceAllowed(
            Equipment equipment,
            EquipmentEventDefinition definition) {
        return definition.getFromPrimaryStatus() == null
                || definition.getFromPrimaryStatus().equals(equipment.getPrimaryStatus());
    }
}