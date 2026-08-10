package com.fabpilot.mescore.commandvalidation.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条可解释的业务规则结果；evaluated=false 表示因前置对象不存在而无法继续判断。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCheckResultTO {
    private String ruleCode;
    private boolean evaluated;
    private boolean passed;
    private String errorCode;
    private String message;
    @Builder.Default
    private List<String> suggestedActionTypes = List.of();
}