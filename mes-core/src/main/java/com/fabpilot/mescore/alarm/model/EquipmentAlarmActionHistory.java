package com.fabpilot.mescore.alarm.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 每次告警确认或关闭形成一条不可变审计记录，同时作为幂等重放的判断依据。 */
@TableName("equipment_alarm_action_history")
public class EquipmentAlarmActionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alarmId;
    private String action;
    private String operatorId;
    private String idempotencyKey;
    private Long alarmVersionBefore;
    private Long alarmVersionAfter;
    private LocalDateTime occurredAt;

    public Long getAlarmId() {
        return alarmId;
    }

    public String getAction() {
        return action;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getAlarmVersionAfter() {
        return alarmVersionAfter;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setAlarmVersionBefore(Long alarmVersionBefore) {
        this.alarmVersionBefore = alarmVersionBefore;
    }

    public void setAlarmVersionAfter(Long alarmVersionAfter) {
        this.alarmVersionAfter = alarmVersionAfter;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}