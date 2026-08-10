package com.fabpilot.mescore.alarm.enums;

/**
 * 告警生命周期状态。
 *
 * <p>状态统一放在枚举中，避免服务代码散落字符串，导致数据库值拼写不一致。
 */
public enum AlarmStatus {
    ACTIVE,
    ACKNOWLEDGED,
    CLOSED;

    public String databaseValue() {
        return name();
    }
}
