package com.fabpilot.mescore.lot.dto;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Release Lot 的请求数据；当前没有 Release 独有字段。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReleaseLotRequestTO extends VersionedCommandRequestTO {

    /** 便于测试和内部调用直接构造完整 Release 请求。 */
    public ReleaseLotRequestTO(Long expectedVersion, String idempotencyKey, String operatorId) {
        super(expectedVersion, idempotencyKey, operatorId);
    }
}