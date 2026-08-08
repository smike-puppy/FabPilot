package com.fabpilot.mescore.lot.service.recording;

import com.fabpilot.mescore.common.enums.OperatorType;
import com.fabpilot.mescore.lot.model.LotTransaction;

/** 统一构造 LotTransaction，避免每个命令重复填写相同的审计、幂等和版本字段。 */
public final class LotTransactionFactory {
    private LotTransactionFactory() {
    }

    public static LotTransaction create(LotTransactionRecordTO record) {
        LotTransaction transaction = new LotTransaction();
        transaction.setLotId(record.getLot().getId());
        transaction.setTransactionType(record.getTransactionType().databaseValue());
        transaction.setRouteStepId(record.getRouteStep() == null ? null : record.getRouteStep().getId());
        transaction.setOperationId(
                record.getRouteStep() == null ? null : record.getRouteStep().getOperationId());
        transaction.setEquipmentId(record.getEquipmentId());
        transaction.setExecutionStatusBefore(record.getLot().getExecutionStatus());
        transaction.setExecutionStatusAfter(record.getExecutionStatusAfter());
        transaction.setHoldStatusBefore(record.getLot().getHoldStatus());
        transaction.setHoldStatusAfter(record.getHoldStatusAfter());
        transaction.setOperatorType(OperatorType.USER.databaseValue());
        transaction.setOperatorId(record.getRequest().getOperatorId());
        transaction.setReasonCode(record.getReasonCode());
        transaction.setReasonText(record.getReasonText());
        transaction.setIdempotencyKey(record.getRequest().getIdempotencyKey());
        transaction.setLotVersionBefore(record.getLot().getVersion());
        transaction.setLotVersionAfter(record.getNextVersion());
        transaction.setOccurredAt(record.getOccurredAt());
        return transaction;
    }
}