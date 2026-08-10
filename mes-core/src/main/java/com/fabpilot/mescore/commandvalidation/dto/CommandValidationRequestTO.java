package com.fabpilot.mescore.commandvalidation.dto;

import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用命令预检查请求。
 *
 * <p>公共字段描述目标和乐观锁版本；equipmentCode、eventCode、alarmId、reasonCode、reasonText
 * 只在对应命令中使用。字段保持强类型，具体必填关系由所属领域 Validator 解释并返回规则结果。</p>
 */
@Data
@NoArgsConstructor
public class CommandValidationRequestTO {
    @NotNull(message = "commandType is required")
    private CommandType commandType;

    @NotNull(message = "targetType is required")
    private TargetType targetType;

    @NotBlank(message = "targetCode is required")
    @Size(max = 64, message = "targetCode length must not exceed 64")
    private String targetCode;

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @Size(max = 64, message = "equipmentCode length must not exceed 64")
    private String equipmentCode;

    @Size(max = 64, message = "eventCode length must not exceed 64")
    private String eventCode;

    private Long alarmId;

    @Size(max = 64, message = "reasonCode length must not exceed 64")
    private String reasonCode;

    @Size(max = 500, message = "reasonText length must not exceed 500")
    private String reasonText;
}