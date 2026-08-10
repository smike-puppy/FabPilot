package com.fabpilot.mescore.commandvalidation.dto;

import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 命令预检查汇总；allowed 只有在所有规则均已执行且通过时才为 true。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandValidationResultTO {
    private boolean allowed;
    private CommandType commandType;
    private TargetType targetType;
    private String targetCode;
    private Long observedVersion;
    private LocalDateTime observedAt;
    private List<RuleCheckResultTO> checks;
}