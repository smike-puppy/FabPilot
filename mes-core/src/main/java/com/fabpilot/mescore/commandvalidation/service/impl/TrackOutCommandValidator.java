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
import com.fabpilot.mescore.lot.service.policy.TrackOutPolicy;
import java.util.List;
import org.springframework.stereotype.Component;

/** Track Out 只读预检查：Lot 必须正在设备加工，关联设备也必须仍为 U + PROC。 */
@Component
public class TrackOutCommandValidator extends AbstractLotCommandValidator
        implements CommandValidator {
    @Override
    public CommandType supportedCommandType() {
        return CommandType.TRACK_OUT;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        Lot lot = findLot(request.getTargetCode());
        List<RuleCheckResultTO> checks = newChecks(request, lot);
        if (lot == null) {
            addMissingLotChecks(checks);
            addCurrentStepCheck(checks, null);
            checks.add(notEvaluated("CURRENT_EQUIPMENT_EXISTS", "Lot 不存在，无法读取绑定设备"));
            checks.add(notEvaluated("EQUIPMENT_UP", "绑定设备未知，无法检查 U/D 状态"));
            checks.add(notEvaluated("EQUIPMENT_PROCESSING", "绑定设备未知，无法检查加工状态"));
            return build(request, null, checks);
        }

        checks.add(evaluated("LOT_RUNNING", LotStatePolicy.isRunningForTrackOut(lot),
                "LOT_STATE_INVALID", "Track Out 要求 Lot 为 RUNNING", "CHECK_LOT_LIFECYCLE"));
        checks.add(evaluated("LOT_RELEASED", LotStatePolicy.isReleasedForTrackOut(lot),
                "LOT_STATE_INVALID", "Track Out 要求 Lot 未被 Hold", "RELEASE_HOLD"));
        checks.add(evaluated("LOT_HAS_CURRENT_STEP", LotStatePolicy.hasStepForTrackOut(lot),
                "LOT_STATE_INVALID", "Track Out 要求 Lot 具有当前 Step", "CHECK_ROUTE"));
        checks.add(evaluated("LOT_HAS_CURRENT_EQUIPMENT",
                LotStatePolicy.hasEquipmentForTrackOut(lot), "LOT_STATE_INVALID",
                "Track Out 要求 Lot 已绑定设备", "CHECK_CURRENT_EQUIPMENT"));
        addCurrentStepCheck(checks, lot);
        addEquipmentChecks(checks, lot);
        return build(request, lot.getVersion(), checks);
    }

    private void addMissingLotChecks(List<RuleCheckResultTO> checks) {
        checks.add(notEvaluated("LOT_RUNNING", "Lot 不存在，无法检查执行状态"));
        checks.add(notEvaluated("LOT_RELEASED", "Lot 不存在，无法检查 Hold 状态"));
        checks.add(notEvaluated("LOT_HAS_CURRENT_STEP", "Lot 不存在，无法检查 Step 绑定"));
        checks.add(notEvaluated("LOT_HAS_CURRENT_EQUIPMENT", "Lot 不存在，无法检查设备绑定"));
    }

    private void addEquipmentChecks(List<RuleCheckResultTO> checks, Lot lot) {
        if (lot.getCurrentEquipmentId() == null) {
            checks.add(evaluated("CURRENT_EQUIPMENT_EXISTS", false, "EQUIPMENT_NOT_FOUND",
                    "Lot 未绑定当前设备", "CHECK_CURRENT_EQUIPMENT"));
            checks.add(notEvaluated("EQUIPMENT_UP", "当前设备未知，无法检查 U/D 状态"));
            checks.add(notEvaluated("EQUIPMENT_PROCESSING", "当前设备未知，无法检查加工状态"));
            return;
        }
        Equipment equipment = findEquipment(lot.getCurrentEquipmentId());
        checks.add(evaluated("CURRENT_EQUIPMENT_EXISTS", equipment != null,
                "EQUIPMENT_NOT_FOUND", "Lot 绑定的设备必须存在", "CHECK_EQUIPMENT_DATA"));
        if (equipment == null) {
            checks.add(notEvaluated("EQUIPMENT_UP", "绑定设备不存在，无法检查 U/D 状态"));
            checks.add(notEvaluated("EQUIPMENT_PROCESSING", "绑定设备不存在，无法检查加工状态"));
            return;
        }
        checks.add(evaluated("EQUIPMENT_UP", TrackOutPolicy.isEquipmentUp(equipment),
                "EQUIPMENT_STATE_INVALID", "Track Out 要求设备为 U", "RECOVER_EQUIPMENT"));
        checks.add(evaluated("EQUIPMENT_PROCESSING",
                TrackOutPolicy.isEquipmentProcessing(equipment), "EQUIPMENT_STATE_INVALID",
                "Track Out 要求设备为 PROC", "CHECK_EQUIPMENT_OCCUPATION"));
    }
}