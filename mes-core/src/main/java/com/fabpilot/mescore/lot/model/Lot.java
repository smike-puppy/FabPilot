package com.fabpilot.mescore.lot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;

/**
 * Lot 当前状态快照。
 *
 * <p>完整的状态演进保存在 LotTransaction；本类仅保存高频查询和并发校验所需的当前值。</p>
 */
@TableName("lot")
public class Lot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long workOrderId;
    private Long routeId;
    private Long currentRouteStepId;
    private Long currentEquipmentId;
    private int quantity;
    /** 生产流转阶段，与 Hold 状态分离，避免把两种语义混在同一个枚举中。 */
    private String executionStatus;
    /** 暂停标记；HELD 时写侧状态机必须阻止继续流转。 */
    private String holdStatus;
    private String lastTransactionCode;
    private LocalDateTime lastTransactionAt;
    private String lastOperatorId;
    /** Lot 完成最后一道工序的时间；未完成时为空。 */
    private LocalDateTime completedAt;
    /** 写侧更新时参与乐观锁比较，防止并发请求覆盖最新 Lot 快照。 */
    @Version
    private Long version;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Long getWorkOrderId() { return workOrderId; }
    public Long getRouteId() { return routeId; }
    public Long getCurrentRouteStepId() { return currentRouteStepId; }
    public Long getCurrentEquipmentId() { return currentEquipmentId; }
    public int getQuantity() { return quantity; }
    public String getExecutionStatus() { return executionStatus; }
    public String getHoldStatus() { return holdStatus; }
    public String getLastTransactionCode() { return lastTransactionCode; }
    public LocalDateTime getLastTransactionAt() { return lastTransactionAt; }
    public String getLastOperatorId() { return lastOperatorId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Long getVersion() { return version; }
}
