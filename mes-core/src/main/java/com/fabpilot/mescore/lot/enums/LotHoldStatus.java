package com.fabpilot.mescore.lot.enums;
/** 与执行阶段正交的暂停状态：暂停不会自动改变 Step、设备绑定或执行阶段。 */
public enum LotHoldStatus {
    /** 未暂停，满足其他规则时可以继续生产流转。 */ RELEASED,
    /** 已暂停，禁止 Track In、Track Out 和正常 Finish。 */ HELD;
    public String databaseValue() { return name(); }
}