package com.fabpilot.mescore.lot.dto;
import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
/** Release Hold 请求：原因解释暂停条件为什么已经解除。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReleaseHoldLotRequestTO extends VersionedCommandRequestTO {
    /** 稳定、可统计的原因编码；会进入不可变 LotTransaction，也是幂等身份的一部分。 */
    @NotBlank(message = "reasonCode 不能为空")
    @Size(max = 64, message = "reasonCode 长度不能超过 64")
    private String reasonCode;
    /** 给现场人员和审计人员阅读的具体说明；重放时必须与首次请求完全一致。 */
    @NotBlank(message = "reasonText 不能为空")
    @Size(max = 500, message = "reasonText 长度不能超过 500")
    private String reasonText;
    public ReleaseHoldLotRequestTO(Long expectedVersion, String idempotencyKey, String operatorId,
            String reasonCode, String reasonText) {
        super(expectedVersion, idempotencyKey, operatorId);
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
    }
}