package com.fabpilot.mescore.commandvalidation.service.impl;

import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.build;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.evaluated;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.notEvaluated;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.service.CommandValidator;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.service.policy.LotStatePolicy;
import java.util.List;
import org.springframework.stereotype.Component;

/** Scrap 只读预检查：终态 Lot 不可再次报废，存在的 Step/设备引用必须可解析以保留审计现场。 */
@Component
public class ScrapCommandValidator extends AbstractLotCommandValidator
        implements CommandValidator {
    @Override
    public CommandType supportedCommandType() {
        return CommandType.SCRAP;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        Lot lot = findLot(request.getTargetCode());
        List<RuleCheckResultTO> checks = newChecks(request, lot);
        if (lot == null) {
            checks.add(notEvaluated("LOT_NOT_TERMINAL", "Lot 不存在，无法检查终态"));
            checks.add(notEvaluated("OPTIONAL_ROUTE_STEP_VALID", "Lot 不存在，无法检查 Step"));
            checks.add(notEvaluated("OPTIONAL_EQUIPMENT_EXISTS", "Lot 不存在，无法检查设备"));
            return build(request, null, checks);
        }

        checks.add(evaluated("LOT_NOT_TERMINAL", LotStatePolicy.isNotTerminalForScrap(lot),
                "LOT_STATE_INVALID", "COMPLETED 或 SCRAPPED Lot 不能再次报废",
                "CHECK_LOT_LIFECYCLE"));
        addOptionalStepCheck(checks, lot);
        addOptionalEquipmentCheck(checks, lot);
        return build(request, lot.getVersion(), checks);
    }

    private void addOptionalStepCheck(List<RuleCheckResultTO> checks, Lot lot) {
        if (lot.getCurrentRouteStepId() == null) {
            checks.add(evaluated("OPTIONAL_ROUTE_STEP_VALID", true, "LOT_STATE_INVALID",
                    "无当前 Step 的早期 Lot 允许报废"));
            return;
        }
        int before = checks.size();
        addCurrentStepCheck(checks, lot);
        RuleCheckResultTO stepCheck = checks.remove(before);
        stepCheck.setRuleCode("OPTIONAL_ROUTE_STEP_VALID");
        checks.add(stepCheck);
    }

    private void addOptionalEquipmentCheck(List<RuleCheckResultTO> checks, Lot lot) {
        if (lot.getCurrentEquipmentId() == null) {
            checks.add(evaluated("OPTIONAL_EQUIPMENT_EXISTS", true, "EQUIPMENT_NOT_FOUND",
                    "Lot 未绑定设备，无需释放设备"));
            return;
        }
        Equipment equipment = findEquipment(lot.getCurrentEquipmentId());
        checks.add(evaluated("OPTIONAL_EQUIPMENT_EXISTS", equipment != null,
                "EQUIPMENT_NOT_FOUND", "Lot 已绑定设备时，该设备必须存在",
                "CHECK_EQUIPMENT_DATA"));
    }
}