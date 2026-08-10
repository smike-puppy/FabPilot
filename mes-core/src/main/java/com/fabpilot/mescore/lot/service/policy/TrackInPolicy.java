package com.fabpilot.mescore.lot.service.policy;

import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;

/**
 * Track In 的设备侧确定性规则。
 *
 * <p>正式执行与只读预检查都调用这里的判断，保证预检查结论与真正执行命令时的规则一致。</p>
 */
public final class TrackInPolicy {
    private static final String EQUIPMENT_UP = "U";
    private static final String EQUIPMENT_IDLE = "IDLE";

    private TrackInPolicy() {
    }

    /** U 表示设备没有 Down；Down 或维护中的设备不能承接生产 Lot。 */
    public static boolean isEquipmentUp(Equipment equipment) {
        return EQUIPMENT_UP.equals(equipment.getUpDownStatus());
    }

    /** IDLE 表示设备当前没有加工任务；PROC 等状态不能再次 Track In。 */
    public static boolean isEquipmentIdle(Equipment equipment) {
        return EQUIPMENT_IDLE.equals(equipment.getPrimaryStatus());
    }

    /** 设备必须同时满足 U 和 IDLE，任一条件不满足都拒绝正式 Track In。 */
    public static void assertEquipmentAvailable(Equipment equipment) {
        if (!isEquipmentUp(equipment) || !isEquipmentIdle(equipment)) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Equipment must be U and IDLE");
        }
    }

    /** 能力组关系必须恰好存在一条；缺失或重复配置都不应允许上机。 */
    public static boolean hasRequiredCapability(int membershipCount) {
        return membershipCount == 1;
    }

    public static void assertRequiredCapability(int membershipCount) {
        if (!hasRequiredCapability(membershipCount)) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_CAPABILITY_MISMATCH,
                    "Equipment does not match the current route step");
        }
    }

    /** Lot 占用关系是设备状态之外的第二道保护，防止 IDLE 快照与实际绑定不一致。 */
    public static boolean isNotOccupied(long occupiedCount) {
        return occupiedCount == 0;
    }

    public static void assertNotOccupied(long occupiedCount) {
        if (!isNotOccupied(occupiedCount)) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_OCCUPIED,
                    "Equipment is occupied by another Lot");
        }
    }
}