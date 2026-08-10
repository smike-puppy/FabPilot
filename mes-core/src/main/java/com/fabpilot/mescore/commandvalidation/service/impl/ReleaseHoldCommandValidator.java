package com.fabpilot.mescore.commandvalidation.service.impl;

import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.build;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.evaluated;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.notEvaluated;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.service.CommandValidator;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.service.policy.LotStatePolicy;
import java.util.List;
import org.springframework.stereotype.Component;

/** Release Hold 只读预检查：活动 Lot 必须确实处于 HELD，且当前 Step 仍有效。 */
@Component
public class ReleaseHoldCommandValidator extends AbstractLotCommandValidator
        implements CommandValidator {
    @Override
    public CommandType supportedCommandType() {
        return CommandType.RELEASE_HOLD;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        Lot lot = findLot(request.getTargetCode());
        List<RuleCheckResultTO> checks = newChecks(request, lot);
        if (lot == null) {
            checks.add(notEvaluated("LOT_ACTIVE", "Lot 不存在，无法检查执行阶段"));
            checks.add(notEvaluated("LOT_HELD", "Lot 不存在，无法检查 Hold 状态"));
        } else {
            checks.add(evaluated("LOT_ACTIVE", LotStatePolicy.isActiveForReleaseHold(lot),
                    "LOT_STATE_INVALID", "Release Hold 只允许 READY 或 RUNNING Lot",
                    "CHECK_LOT_LIFECYCLE"));
            checks.add(evaluated("LOT_HELD", LotStatePolicy.isHeldForReleaseHold(lot),
                    "LOT_STATE_INVALID", "Release Hold 要求 Lot 当前为 HELD",
                    "CHECK_HOLD_STATUS"));
        }
        addCurrentStepCheck(checks, lot);
        return build(request, lot == null ? null : lot.getVersion(), checks);
    }
}