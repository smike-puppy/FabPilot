package com.fabpilot.mescore.lot.enums;

/** lot_transaction.transaction_type 字段允许的生产操作类型。 */
public enum LotTransactionType {
    CREATE,
    RELEASE,
    TRACK_IN,
    TRACK_OUT,
    HOLD,
    RELEASE_HOLD,
    FINISH,
    SCRAP;

    /** 返回写入数据库和接口响应使用的稳定值。 */
    public String databaseValue() {
        return name();
    }
}