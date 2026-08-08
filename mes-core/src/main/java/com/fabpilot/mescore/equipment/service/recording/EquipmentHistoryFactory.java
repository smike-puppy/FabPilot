package com.fabpilot.mescore.equipment.service.recording;

import com.fabpilot.mescore.common.enums.OperatorType;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;

/** 统一构造 EquipmentHistory，公共操作人、幂等、状态前值和版本字段只维护一份。 */
public final class EquipmentHistoryFactory {
    private EquipmentHistoryFactory() {
    }

    public static EquipmentHistory create(EquipmentHistoryRecordTO record) {
        EquipmentHistory history = new EquipmentHistory();
        history.setEquipmentId(record.getEquipment().getId());
        history.setEventCode(record.getEventCode());
        history.setUpDownStatusBefore(record.getEquipment().getUpDownStatus());
        history.setUpDownStatusAfter(record.getEquipment().getUpDownStatus());
        history.setPrimaryStatusBefore(record.getEquipment().getPrimaryStatus());
        history.setPrimaryStatusAfter(record.getPrimaryStatusAfter());
        history.setOperatorType(OperatorType.USER.databaseValue());
        history.setOperatorId(record.getRequest().getOperatorId());
        history.setOperatorRole("MANUFACTURING");
        history.setIdempotencyKey(record.getRequest().getIdempotencyKey());
        history.setEquipmentVersionBefore(record.getEquipment().getVersion());
        history.setEquipmentVersionAfter(record.getNextVersion());
        history.setOccurredAt(record.getOccurredAt());
        return history;
    }
}