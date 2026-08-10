package com.fabpilot.mescore.commandvalidation.service.impl;

import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.build;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.evaluated;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.notEvaluated;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.service.CommandValidator;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.service.policy.LotStatePolicy;
import com.fabpilot.mescore.process.model.RouteStep;
import java.util.List;
import org.springframework.stereotype.Component;

/** Release 只读预检查：确认 CREATED、未 Hold，并且能够确定 Release 后的首 Step。 */
@Component
public class ReleaseCommandValidator extends AbstractLotCommandValidator
        implements CommandValidator {
    @Override
    public CommandType supportedCommandType() {
        return CommandType.RELEASE;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        Lot lot = findLot(request.getTargetCode());
        List<RuleCheckResultTO> checks = newChecks(request, lot);
        if (lot == null) {
            checks.add(notEvaluated("LOT_CREATED", "Lot 不存在，无法检查执行状态"));
            checks.add(notEvaluated("LOT_RELEASED", "Lot 不存在，无法检查 Hold 状态"));
            checks.add(notEvaluated("RELEASE_ROUTE_STEP_RESOLVABLE", "Lot 不存在，无法确定首 Step"));
        } else {
            checks.add(evaluated("LOT_CREATED", LotStatePolicy.isCreatedForRelease(lot),
                    "LOT_STATE_INVALID", "Release 要求 Lot 为 CREATED", "CHECK_LOT_LIFECYCLE"));
            checks.add(evaluated("LOT_RELEASED", LotStatePolicy.isReleasedForRelease(lot),
                    "LOT_STATE_INVALID", "Release 要求 Lot 未被 Hold", "RELEASE_HOLD"));
            checks.add(evaluated("RELEASE_ROUTE_STEP_RESOLVABLE", canResolveReleaseStep(lot),
                    "LOT_STATE_INVALID", "Release 后必须能够确定当前或路线首 Step",
                    "CONFIGURE_ROUTE_STEP"));
        }
        return build(request, lot == null ? null : lot.getVersion(), checks);
    }

    private boolean canResolveReleaseStep(Lot lot) {
        if (lot.getCurrentRouteStepId() != null) {
            return true;
        }
        RouteStep firstStep = routeStepMapper.selectOne(
                Wrappers.<RouteStep>lambdaQuery()
                        .eq(RouteStep::getRouteId, lot.getRouteId())
                        .orderByAsc(RouteStep::getSequenceNo)
                        .last("LIMIT 1"));
        return firstStep != null;
    }
}