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
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.service.policy.LotStatePolicy;
import com.fabpilot.mescore.lot.service.policy.TrackInPolicy;
import com.fabpilot.mescore.process.model.RouteStep;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Track In 只读预检查器。
 *
 * <p>按 Lot 状态、当前 Step、目标设备、能力组和占用关系一次返回全部结果；
 * 只调用 select，不执行任何 update、insert 或履历写入。</p>
 */
@Component
public class TrackInCommandValidator extends AbstractLotCommandValidator
        implements CommandValidator {
    @Override
    public CommandType supportedCommandType() {
        return CommandType.TRACK_IN;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        Lot lot = findLot(request.getTargetCode());
        List<RuleCheckResultTO> checks = newChecks(request, lot);
        addLotStateChecks(checks, lot);
        RouteStep routeStep = addCurrentStepCheck(checks, lot);

        boolean equipmentCodeProvided = StringUtils.hasText(request.getEquipmentCode());
        checks.add(evaluated("EQUIPMENT_CODE_PROVIDED", equipmentCodeProvided,
                "EQUIPMENT_NOT_FOUND", "Track In 必须提供目标 equipmentCode",
                "SELECT_EQUIPMENT"));
        Equipment equipment = equipmentCodeProvided
                ? findEquipment(request.getEquipmentCode()) : null;
        checks.add(evaluated("EQUIPMENT_EXISTS", equipment != null, "EQUIPMENT_NOT_FOUND",
                equipment == null ? "设备不存在" : "设备存在", "CHECK_EQUIPMENT_CODE"));
        addEquipmentChecks(checks, equipment, routeStep);
        return build(request, lot == null ? null : lot.getVersion(), checks);
    }

    private Equipment findEquipment(String equipmentCode) {
        return equipmentMapper.selectOne(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getCode, equipmentCode));
    }

    private void addLotStateChecks(List<RuleCheckResultTO> checks, Lot lot) {
        if (lot == null) {
            checks.add(notEvaluated("LOT_READY", "Lot 不存在，无法检查执行状态"));
            checks.add(notEvaluated("LOT_RELEASED", "Lot 不存在，无法检查 Hold 状态"));
            checks.add(notEvaluated("LOT_WITHOUT_EQUIPMENT", "Lot 不存在，无法检查设备绑定"));
            return;
        }
        checks.add(evaluated("LOT_READY", LotStatePolicy.isReadyForTrackIn(lot),
                "LOT_STATE_INVALID", "Track In 要求 Lot 为 READY", "RELEASE_LOT"));
        checks.add(evaluated("LOT_RELEASED", LotStatePolicy.isReleasedForTrackIn(lot),
                "LOT_STATE_INVALID", "Track In 要求 Lot 未被 Hold", "RELEASE_HOLD"));
        checks.add(evaluated("LOT_WITHOUT_EQUIPMENT",
                LotStatePolicy.hasNoEquipmentForTrackIn(lot), "LOT_STATE_INVALID",
                "Track In 前 Lot 不能已绑定设备", "CHECK_CURRENT_EQUIPMENT"));
    }

    private void addEquipmentChecks(
            List<RuleCheckResultTO> checks,
            Equipment equipment,
            RouteStep routeStep) {
        if (equipment == null) {
            checks.add(notEvaluated("EQUIPMENT_UP", "设备不存在，无法检查 U/D 状态"));
            checks.add(notEvaluated("EQUIPMENT_IDLE", "设备不存在，无法检查加工状态"));
            checks.add(notEvaluated("EQUIPMENT_CAPABILITY_MATCH", "设备不存在，无法检查能力组"));
            checks.add(notEvaluated("EQUIPMENT_NOT_OCCUPIED", "设备不存在，无法检查占用关系"));
            return;
        }
        checks.add(evaluated("EQUIPMENT_UP", TrackInPolicy.isEquipmentUp(equipment),
                "EQUIPMENT_STATE_INVALID", "Track In 要求设备为 U", "RESTORE_EQUIPMENT_UP"));
        checks.add(evaluated("EQUIPMENT_IDLE", TrackInPolicy.isEquipmentIdle(equipment),
                "EQUIPMENT_STATE_INVALID", "Track In 要求设备为 IDLE",
                "WAIT_FOR_EQUIPMENT_IDLE"));
        addCapabilityCheck(checks, equipment, routeStep);
        Long occupiedCount = lotMapper.selectCount(
                Wrappers.<Lot>lambdaQuery()
                        .eq(Lot::getCurrentEquipmentId, equipment.getId()));
        checks.add(evaluated("EQUIPMENT_NOT_OCCUPIED",
                TrackInPolicy.isNotOccupied(occupiedCount), "EQUIPMENT_OCCUPIED",
                "设备不能被其他 Lot 占用", "SELECT_ALTERNATIVE_EQUIPMENT",
                "WAIT_FOR_TRACK_OUT"));
    }

    private void addCapabilityCheck(
            List<RuleCheckResultTO> checks,
            Equipment equipment,
            RouteStep routeStep) {
        if (routeStep == null) {
            checks.add(notEvaluated("EQUIPMENT_CAPABILITY_MATCH",
                    "当前 Step 无效，无法检查设备能力组"));
            return;
        }
        int membershipCount = equipmentMapper.countGroupMembership(
                routeStep.getRequiredEquipmentGroupId(), equipment.getId());
        checks.add(evaluated("EQUIPMENT_CAPABILITY_MATCH",
                TrackInPolicy.hasRequiredCapability(membershipCount),
                "EQUIPMENT_CAPABILITY_MISMATCH",
                "设备必须恰好属于当前 Step 要求的设备组",
                "SELECT_ALTERNATIVE_EQUIPMENT"));
    }
}