package com.fabpilot.mescore.lot.dto;
import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
/** Track In 请求：调用方选择目标设备，服务端再校验设备状态、能力组和占用关系。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TrackInLotRequestTO extends VersionedCommandRequestTO {
    /** 要上机的设备业务编码；编码只是选择目标，不代表该设备一定允许加工当前 Step。 */
    @NotBlank(message = "equipmentCode 不能为空")
    private String equipmentCode;
    public TrackInLotRequestTO(Long expectedVersion, String idempotencyKey,
            String operatorId, String equipmentCode) {
        super(expectedVersion, idempotencyKey, operatorId);
        this.equipmentCode = equipmentCode;
    }
}