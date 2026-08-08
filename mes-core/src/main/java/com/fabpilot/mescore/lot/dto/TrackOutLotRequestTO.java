package com.fabpilot.mescore.lot.dto;
import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
/** Track Out 不接收设备或下一 Step：两者必须以 Lot 当前快照和 Route 为事实来源，防止调用方任意推进。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrackOutLotRequestTO extends VersionedCommandRequestTO {
    public TrackOutLotRequestTO(Long expectedVersion, String idempotencyKey, String operatorId) {
        super(expectedVersion, idempotencyKey, operatorId);
    }
}