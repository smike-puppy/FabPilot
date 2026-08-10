package com.fabpilot.mescore.alarm.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 设备告警当前快照。
 *
 * <p>EquipmentHistory 记录“一次状态变化”，本表记录“该异常目前处理到哪一步”。
 * ACTIVE 表示尚未确认，ACKNOWLEDGED 表示工程师已接手，CLOSED 表示异常处理结束。</p>
 */
@TableName("equipment_alarm")
public class EquipmentAlarm {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long equipmentId;
    private String alarmCode;
    private String severity;
    private String status;
    private String sourceEventCode;
    private String sourceIdempotencyKey;
    private String message;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String closedBy;
    private LocalDateTime closedAt;
    private Long version;
    private LocalDateTime openedAt;

    public Long getId() { return id; }
    public Long getEquipmentId() { return equipmentId; }
    public String getAlarmCode() { return alarmCode; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getSourceEventCode() { return sourceEventCode; }
    public String getSourceIdempotencyKey() { return sourceIdempotencyKey; }
    public String getMessage() { return message; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public String getClosedBy() { return closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public Long getVersion() { return version; }
    public LocalDateTime getOpenedAt() { return openedAt; }

    public void setEquipmentId(Long value) { equipmentId = value; }
    public void setAlarmCode(String value) { alarmCode = value; }
    public void setSeverity(String value) { severity = value; }
    public void setStatus(String value) { status = value; }
    public void setSourceEventCode(String value) { sourceEventCode = value; }
    public void setSourceIdempotencyKey(String value) { sourceIdempotencyKey = value; }
    public void setMessage(String value) { message = value; }
    public void setVersion(Long value) { version = value; }
    public void setOpenedAt(LocalDateTime value) { openedAt = value; }
}