package com.fabpilot.mescore.alarm.dto;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 告警确认或关闭请求；所有字段共同描述一次完整且可审计的业务命令。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AlarmActionRequestTO extends VersionedCommandRequestTO {

    /** 要处理的告警主键。 */
    @NotNull(message = "alarmId must not be null")
    private Long alarmId;

    /** 业务动作：ACKNOWLEDGE 表示确认，CLOSE 表示关闭。 */
    @NotBlank(message = "action must not be blank")
    @Pattern(
            regexp = "ACKNOWLEDGE|CLOSE",
            message = "action must be ACKNOWLEDGE or CLOSE")
    private String action;
}