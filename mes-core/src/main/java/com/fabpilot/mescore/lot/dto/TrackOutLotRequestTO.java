package com.fabpilot.mescore.lot.dto;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Track Out 请求数据；目标设备与当前工序由 Lot 快照确定。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrackOutLotRequestTO extends VersionedCommandRequestTO {

    /** 便于测试和内部调用直接构造完整 Track Out 请求。 */
    public TrackOutLotRequestTO(Long expectedVersion, String idempotencyKey, String operatorId) {
        super(expectedVersion, idempotencyKey, operatorId);
    }
}
