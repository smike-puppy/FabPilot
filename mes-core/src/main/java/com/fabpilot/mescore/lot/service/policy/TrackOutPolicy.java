package com.fabpilot.mescore.lot.service.policy;

import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;

/** Track Out 释放设备前使用的设备状态规则。 */
public final class TrackOutPolicy {
    private TrackOutPolicy() {
    }

    public static boolean isEquipmentUp(Equipment equipment) {
        return "U".equals(equipment.getUpDownStatus());
    }

    public static boolean isEquipmentProcessing(Equipment equipment) {
        return "PROC".equals(equipment.getPrimaryStatus());
    }

    public static void assertEquipmentProcessing(Equipment equipment) {
        if (!isEquipmentUp(equipment) || !isEquipmentProcessing(equipment)) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Track Out equipment must be U and PROC");
        }
    }
}