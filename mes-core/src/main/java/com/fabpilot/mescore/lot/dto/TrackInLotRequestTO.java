package com.fabpilot.mescore.lot.dto;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Track In 请求数据，用于指定 Lot 本次上机的目标设备。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrackInLotRequestTO extends VersionedCommandRequestTO {

    /** 目标设备业务编码。 */
    @NotBlank(message = "equipmentCode 不能为空")
    private String equipmentCode;

    /** 便于测试和内部调用直接构造完整 Track In 请求。 */
    public TrackInLotRequestTO(
            Long expectedVersion,
            String idempotencyKey,
            String operatorId,
            String equipmentCode) {
        super(expectedVersion, idempotencyKey, operatorId);
        this.equipmentCode = equipmentCode;
    }
}