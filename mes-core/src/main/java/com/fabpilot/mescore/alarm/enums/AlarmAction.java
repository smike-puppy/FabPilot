package com.fabpilot.mescore.alarm.enums;

/**
 * 人工告警操作及其允许的状态迁移。
 *
 * <p>确认只能把 ACTIVE 变为 ACKNOWLEDGED；关闭只能把 ACKNOWLEDGED 变为 CLOSED。
 * 将迁移关系集中定义后，接口服务和测试都使用同一套业务依据。
 */
public enum AlarmAction {
    ACKNOWLEDGE(AlarmStatus.ACTIVE, AlarmStatus.ACKNOWLEDGED),
    CLOSE(AlarmStatus.ACKNOWLEDGED, AlarmStatus.CLOSED);

    private final AlarmStatus requiredStatus;
    private final AlarmStatus targetStatus;

    AlarmAction(AlarmStatus requiredStatus, AlarmStatus targetStatus) {
        this.requiredStatus = requiredStatus;
        this.targetStatus = targetStatus;
    }

    public AlarmStatus requiredStatus() {
        return requiredStatus;
    }

    public AlarmStatus targetStatus() {
        return targetStatus;
    }

    public String databaseValue() {
        return name();
    }

    public static AlarmAction fromDatabaseValue(String value) {
        return AlarmAction.valueOf(value);
    }
}
