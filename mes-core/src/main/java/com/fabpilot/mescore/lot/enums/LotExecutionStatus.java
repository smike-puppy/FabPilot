package com.fabpilot.mescore.lot.enums;

/** lot.execution_status 字段允许的生产执行状态。 */
public enum LotExecutionStatus {
    CREATED,
    READY,
    RUNNING,
    COMPLETED,
    SCRAPPED;

    /** 返回写入数据库和接口响应使用的稳定值。 */
    public String databaseValue() {
        return name();
    }
}