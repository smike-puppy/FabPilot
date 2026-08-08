package com.fabpilot.mescore.lot.enums;
/** Lot 的生产执行阶段；与独立的 Hold 状态组合后才构成完整业务状态。 */
public enum LotExecutionStatus {
    /** 已创建但未释放，不能上机。 */ CREATED,
    /** 已进入当前 Step，等待上机。 */ READY,
    /** 已绑定设备，正在加工。 */ RUNNING,
    /** 全部工序正常完成，不可再进入生产。 */ COMPLETED,
    /** 已报废，不可再进入生产。 */ SCRAPPED;
    /** 返回数据库和 API 使用的稳定字面值。 */
    public String databaseValue() { return name(); }
}