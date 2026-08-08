package com.fabpilot.mescore.common.command.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 所有受乐观锁和幂等保护的写命令都必须提供的公共输入。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionedCommandRequestTO {
    /**
     * 调用方最近一次读取到的 Lot version。
     * 服务端要求它等于数据库当前版本，避免用户基于旧快照覆盖其他请求刚完成的状态变更。
     */
    @NotNull(message = "expectedVersion 不能为空")
    private Long expectedVersion;

    /**
     * 一次业务意图的唯一键。网络重试必须复用原键和原参数，服务端才会返回原结果而不重复写履历。
     */
    @NotBlank(message = "idempotencyKey 不能为空")
    private String idempotencyKey;

    /** 实际发起操作的人员标识，会进入 LotTransaction/EquipmentHistory 供审计追溯。 */
    @NotBlank(message = "operatorId 不能为空")
    private String operatorId;
}