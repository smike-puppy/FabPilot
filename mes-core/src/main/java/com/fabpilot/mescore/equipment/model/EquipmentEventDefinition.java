package com.fabpilot.mescore.equipment.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备事件定义决定某个事件允许从什么状态发生，以及成功后设备应进入什么状态。
 * 服务读取定义执行状态迁移，避免把 VACUUM_LOW 等事件规则散落在 Java 条件分支中。
 */
@TableName("equipment_event_definition")
public class EquipmentEventDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventCode;
    private String name;
    private String eventCategory;
    private String fromUpDownStatus;
    private String fromPrimaryStatus;
    private String toUpDownStatus;
    private String toPrimaryStatus;
    private Boolean requiresReason;
    private String status;
    private Long version;

    public Long getId() { return id; }
    public String getEventCode() { return eventCode; }
    public String getName() { return name; }
    public String getEventCategory() { return eventCategory; }
    public String getFromUpDownStatus() { return fromUpDownStatus; }
    public String getFromPrimaryStatus() { return fromPrimaryStatus; }
    public String getToUpDownStatus() { return toUpDownStatus; }
    public String getToPrimaryStatus() { return toPrimaryStatus; }
    public Boolean getRequiresReason() { return requiresReason; }
    public String getStatus() { return status; }
    public Long getVersion() { return version; }
}