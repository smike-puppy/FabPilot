package com.fabpilot.mescore.equipment.service.recording;

import com.fabpilot.mescore.common.enums.OperatorType;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;

/** 统一构造 EquipmentHistory，让状态、操作来源、原因、幂等和版本字段只维护一份。 */
public final class EquipmentHistoryFactory {
    private EquipmentHistoryFactory() {
    }

    public static EquipmentHistory create(EquipmentHistoryRecordTO record) {
        EquipmentHistory history = new EquipmentHistory();
        history.setEquipmentId(record.getEquipment().getId());
        history.setEventCode(record.getEventCode());
        history.setUpDownStatusBefore(record.getEquipment().getUpDownStatus());
        history.setUpDownStatusAfter(record.getUpDownStatusAfter() == null
                ? record.getEquipment().getUpDownStatus()
                : record.getUpDownStatusAfter());
        history.setPrimaryStatusBefore(record.getEquipment().getPrimaryStatus());
        history.setPrimaryStatusAfter(record.getPrimaryStatusAfter());
        history.setOperatorType(record.getOperatorType() == null
                ? OperatorType.USER.databaseValue()
                : record.getOperatorType());
        history.setOperatorId(record.getRequest().getOperatorId());
        history.setOperatorRole(record.getOperatorRole() == null
                ? "MANUFACTURING"
                : record.getOperatorRole());
        history.setReasonCode(record.getReasonCode());
        history.setReasonText(record.getReasonText());
        history.setIdempotencyKey(record.getRequest().getIdempotencyKey());
        history.setEquipmentVersionBefore(record.getEquipment().getVersion());
        history.setEquipmentVersionAfter(record.getNextVersion());
        history.setOccurredAt(record.getOccurredAt());
        return history;
    }
}