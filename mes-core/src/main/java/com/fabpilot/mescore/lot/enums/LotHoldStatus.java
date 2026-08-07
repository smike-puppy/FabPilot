package com.fabpilot.mescore.lot.enums;

/** lot.hold_status 字段允许的暂停状态。 */
public enum LotHoldStatus {
    RELEASED,
    HELD;

    /** 返回写入数据库和接口响应使用的稳定值。 */
    public String databaseValue() {
        return name();
    }
}