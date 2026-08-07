package com.fabpilot.mescore.workorder.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 生产计划主数据；诊断接口读取其状态、计划量和交期来判断生产影响。 */
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private int planQuantity;
    private String status;
    private LocalDateTime dueAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public int getPlanQuantity() { return planQuantity; }
    public String getStatus() { return status; }
    public LocalDateTime getDueAt() { return dueAt; }
}
