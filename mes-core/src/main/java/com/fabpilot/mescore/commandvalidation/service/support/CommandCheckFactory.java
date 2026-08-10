package com.fabpilot.mescore.commandvalidation.service.support;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Validator 的公共结果构造器。
 *
 * <p>这里只统一 evaluated、passed、错误码和建议的封装方式，不保存任何 Lot、Equipment 或 Alarm 业务规则。</p>
 */
public final class CommandCheckFactory {
    private CommandCheckFactory() {
    }

    public static RuleCheckResultTO passed(String ruleCode, String message) {
        return result(ruleCode, true, true, null, message, List.of());
    }

    public static RuleCheckResultTO failed(
            String ruleCode,
            String errorCode,
            String message,
            String... suggestedActions) {
        return result(ruleCode, true, false, errorCode, message, List.of(suggestedActions));
    }

    public static RuleCheckResultTO evaluated(
            String ruleCode,
            boolean passed,
            String errorCode,
            String message,
            String... suggestedActions) {
        return passed
                ? passed(ruleCode, message)
                : failed(ruleCode, errorCode, message, suggestedActions);
    }

    public static RuleCheckResultTO notEvaluated(String ruleCode, String message) {
        return result(ruleCode, false, false, null, message, List.of());
    }

    public static CommandValidationResultTO build(
            CommandValidationRequestTO request,
            Long observedVersion,
            List<RuleCheckResultTO> checks) {
        boolean allowed = checks.stream()
                .allMatch(check -> check.isEvaluated() && check.isPassed());
        return CommandValidationResultTO.builder()
                .allowed(allowed)
                .commandType(request.getCommandType())
                .targetType(request.getTargetType())
                .targetCode(request.getTargetCode())
                .observedVersion(observedVersion)
                .observedAt(LocalDateTime.now())
                .checks(checks)
                .build();
    }

    private static RuleCheckResultTO result(
            String ruleCode,
            boolean evaluated,
            boolean passed,
            String errorCode,
            String message,
            List<String> suggestedActions) {
        return RuleCheckResultTO.builder()
                .ruleCode(ruleCode)
                .evaluated(evaluated)
                .passed(passed)
                .errorCode(passed ? null : errorCode)
                .message(message)
                .suggestedActionTypes(passed ? List.of() : suggestedActions)
                .build();
    }
}