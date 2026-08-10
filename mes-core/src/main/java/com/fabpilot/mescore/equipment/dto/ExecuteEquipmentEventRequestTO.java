package com.fabpilot.mescore.equipment.dto;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 执行设备事件的请求。
 *
 * <p>设备、事件、期望版本和幂等键共同描述一次完整的状态切换命令，因此统一放在请求体中。
 * 原因是否必填由数据库事件定义决定：维护、故障等事件可要求原因，普通生产事件可以不填。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ExecuteEquipmentEventRequestTO extends VersionedCommandRequestTO {
    @NotBlank(message = "equipmentCode 不能为空")
    @Size(max = 64, message = "equipmentCode 长度不能超过 64")
    private String equipmentCode;

    @NotBlank(message = "eventCode 不能为空")
    @Size(max = 64, message = "eventCode 长度不能超过 64")
    private String eventCode;

    @NotBlank(message = "operatorType 不能为空")
    @Pattern(regexp = "USER|SYSTEM", message = "operatorType 只能是 USER 或 SYSTEM")
    private String operatorType;

    @Size(max = 64, message = "reasonCode 长度不能超过 64")
    private String reasonCode;

    @Size(max = 500, message = "reasonText 长度不能超过 500")
    private String reasonText;

    public ExecuteEquipmentEventRequestTO(Long expectedVersion, String idempotencyKey,
            String operatorId, String equipmentCode, String eventCode, String operatorType,
            String reasonCode, String reasonText) {
        super(expectedVersion, idempotencyKey, operatorId);
        this.equipmentCode = equipmentCode;
        this.eventCode = eventCode;
        this.operatorType = operatorType;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
    }
}