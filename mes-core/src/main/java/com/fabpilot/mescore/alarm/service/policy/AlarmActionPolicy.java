package com.fabpilot.mescore.alarm.service.policy;

import com.fabpilot.mescore.alarm.enums.AlarmAction;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.equipment.model.Equipment;

/** 告警确认、关闭与设备恢复的公共状态机规则。 */
public final class AlarmActionPolicy {
    private AlarmActionPolicy() {
    }

    /** ACKNOWLEDGE 只接受 ACTIVE，CLOSE 只接受 ACKNOWLEDGED。 */
    public static boolean isCurrentStatusAllowed(
            EquipmentAlarm alarm,
            AlarmAction action) {
        return action.requiredStatus().databaseValue().equals(alarm.getStatus());
    }

    /** 关闭告警前，关联设备必须真实存在并恢复到 U + IDLE。 */
    public static boolean isEquipmentRecovered(Equipment equipment) {
        return equipment != null
                && "U".equals(equipment.getUpDownStatus())
                && "IDLE".equals(equipment.getPrimaryStatus());
    }
}