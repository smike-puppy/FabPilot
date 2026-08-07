package com.fabpilot.mescore.lot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * lot_transaction 表对应的不可变生产履历。
 *
 * <p>业务代码只允许新增履历，不允许修改或删除既有记录。</p>
 */
@TableName("lot_transaction")
public class LotTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long lotId;
    private String transactionType;
    private Long routeStepId;
    private Long operationId;
    private Long equipmentId;
    private String executionStatusBefore;
    private String executionStatusAfter;
    private String holdStatusBefore;
    private String holdStatusAfter;
    private String operatorType;
    private String operatorId;
    private String reasonCode;
    private String reasonText;
    private String idempotencyKey;
    private Long lotVersionBefore;
    private Long lotVersionAfter;
    private LocalDateTime occurredAt;

    public Long getId() {
        return id;
    }

    public Long getLotId() {
        return lotId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Long getRouteStepId() { return routeStepId; }
    public Long getOperationId() { return operationId; }
    public Long getEquipmentId() { return equipmentId; }

    public String getExecutionStatusBefore() {
        return executionStatusBefore;
    }

    public String getExecutionStatusAfter() {
        return executionStatusAfter;
    }

    public String getHoldStatusBefore() {
        return holdStatusBefore;
    }

    public String getHoldStatusAfter() {
        return holdStatusAfter;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getLotVersionBefore() {
        return lotVersionBefore;
    }

    public Long getLotVersionAfter() {
        return lotVersionAfter;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public void setRouteStepId(Long routeStepId) { this.routeStepId = routeStepId; }
    public void setOperationId(Long operationId) { this.operationId = operationId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public void setExecutionStatusBefore(String executionStatusBefore) {
        this.executionStatusBefore = executionStatusBefore;
    }

    public void setExecutionStatusAfter(String executionStatusAfter) {
        this.executionStatusAfter = executionStatusAfter;
    }

    public void setHoldStatusBefore(String holdStatusBefore) {
        this.holdStatusBefore = holdStatusBefore;
    }

    public void setHoldStatusAfter(String holdStatusAfter) {
        this.holdStatusAfter = holdStatusAfter;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setLotVersionBefore(Long lotVersionBefore) {
        this.lotVersionBefore = lotVersionBefore;
    }

    public void setLotVersionAfter(Long lotVersionAfter) {
        this.lotVersionAfter = lotVersionAfter;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}