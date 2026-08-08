package com.fabpilot.mescore.lot.dto;
import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
/** Release 没有额外业务参数：目标首 Step 由 Lot 所属 Route 决定。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReleaseLotRequestTO extends VersionedCommandRequestTO {
    public ReleaseLotRequestTO(Long expectedVersion, String idempotencyKey, String operatorId) {
        super(expectedVersion, idempotencyKey, operatorId);
    }
}