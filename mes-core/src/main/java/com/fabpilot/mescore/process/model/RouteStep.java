package com.fabpilot.mescore.process.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 工艺路线中的一个有序 Step；设备能力组用于写侧 Track In 校验。
 */
@TableName("route_step")
public class RouteStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private String stepCode;
    private String name;
    private int sequenceNo;
    private Long operationId;
    private Long requiredEquipmentGroupId;

    public Long getId() { return id; }
    public Long getRouteId() { return routeId; }
    public String getStepCode() { return stepCode; }
    public String getName() { return name; }
    public int getSequenceNo() { return sequenceNo; }
    public Long getOperationId() { return operationId; }
    public Long getRequiredEquipmentGroupId() { return requiredEquipmentGroupId; }
}
