package com.fabpilot.mescore.lot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 写侧命令完成后返回的 Lot 最新状态摘要。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LotCommandResultTO {
    /** Lot 业务编码。 */
    private String lotCode;

    /** 本次业务操作类型。 */
    private String transactionType;

    /** 操作后的执行状态。 */
    private String executionStatus;

    /** 操作后的 Hold 状态。 */
    private String holdStatus;

    /** 操作后的乐观锁版本。 */
    private Long version;

    /** 是否命中了此前已成功执行的同一幂等请求。 */
    private boolean idempotent;
}