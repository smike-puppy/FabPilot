package com.fabpilot.mescore.equipment.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;

/**
 * Equipment 当前状态快照；状态变化的完整事实保存在 EquipmentHistory。
 */
@TableName("equipment")
public class Equipment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String equipmentType;
    /** V2 兼容状态；写侧同步维护为 IDLE/RUN。 */
    private String status;
    /** 粗粒度 U/D 状态，用于快速判断设备是否可参与生产。 */
    private String upDownStatus;
    /** 细粒度设备状态，例如 IDLE、PROC、DOWN 或 MAINTENANCE。 */
    private String primaryStatus;
    private String lastEventCode;
    private LocalDateTime lastEventAt;
    /** 设备状态变更时参与乐观锁比较。 */
    @Version
    private Long version;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getEquipmentType() { return equipmentType; }
    public String getStatus() { return status; }
    public String getUpDownStatus() { return upDownStatus; }
    public String getPrimaryStatus() { return primaryStatus; }
    public String getLastEventCode() { return lastEventCode; }
    public LocalDateTime getLastEventAt() { return lastEventAt; }
    public Long getVersion() { return version; }
}
