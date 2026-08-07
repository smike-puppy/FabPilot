package com.fabpilot.mescore.common.enums;

/**
 * 业务履历通用的操作者来源。
 *
 * <p>LotTransaction、EquipmentHistory 和后续审计模块共同使用该枚举。</p>
 */
public enum OperatorType {
    USER,
    AGENT_PROPOSAL,
    SYSTEM;

    /** 返回写入数据库和接口响应使用的稳定值。 */
    public String databaseValue() {
        return name();
    }
}