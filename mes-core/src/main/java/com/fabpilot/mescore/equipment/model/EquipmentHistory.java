package com.fabpilot.mescore.equipment.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 设备事件后的不可变历史记录，用于还原状态变化原因和操作者。
 */
@TableName("equipment_history")
public class EquipmentHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long equipmentId;
    private String eventCode;
    private String upDownStatusBefore;
    private String upDownStatusAfter;
    private String primaryStatusBefore;
    private String primaryStatusAfter;
    private String operatorType;
    private String operatorId;
    private String operatorRole;
    private String reasonCode;
    private String reasonText;
    private String idempotencyKey;
    private Long equipmentVersionBefore;
    private Long equipmentVersionAfter;
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public Long getEquipmentId() { return equipmentId; }
    public String getEventCode() { return eventCode; }
    public String getUpDownStatusBefore() { return upDownStatusBefore; }
    public String getUpDownStatusAfter() { return upDownStatusAfter; }
    public String getPrimaryStatusBefore() { return primaryStatusBefore; }
    public String getPrimaryStatusAfter() { return primaryStatusAfter; }
    public String getOperatorType() { return operatorType; }
    public String getOperatorId() { return operatorId; }
    public String getOperatorRole() { return operatorRole; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonText() { return reasonText; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getEquipmentVersionBefore() { return equipmentVersionBefore; }
    public Long getEquipmentVersionAfter() { return equipmentVersionAfter; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public void setUpDownStatusBefore(String value) { this.upDownStatusBefore = value; }
    public void setUpDownStatusAfter(String value) { this.upDownStatusAfter = value; }
    public void setPrimaryStatusBefore(String value) { this.primaryStatusBefore = value; }
    public void setPrimaryStatusAfter(String value) { this.primaryStatusAfter = value; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public void setReasonText(String reasonText) { this.reasonText = reasonText; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setEquipmentVersionBefore(Long value) { this.equipmentVersionBefore = value; }
    public void setEquipmentVersionAfter(Long value) { this.equipmentVersionAfter = value; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
