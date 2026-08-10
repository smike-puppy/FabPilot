package com.fabpilot.mescore.commandvalidation.service.impl;

import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.evaluated;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.notEvaluated;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.service.policy.LotRoutePolicy;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.process.model.RouteStep;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Lot 命令 Validator 的只读查询与公共存在性、版本、Step 检查。 */
abstract class AbstractLotCommandValidator {
    @Autowired
    protected LotMapper lotMapper;

    @Autowired
    protected RouteStepMapper routeStepMapper;

    @Autowired
    protected EquipmentMapper equipmentMapper;

    protected List<RuleCheckResultTO> newChecks(CommandValidationRequestTO request, Lot lot) {
        List<RuleCheckResultTO> checks = new ArrayList<>();
        checks.add(evaluated("TARGET_TYPE_LOT", request.getTargetType() == TargetType.LOT,
                "TARGET_TYPE_INVALID", "Lot 命令要求 targetType=LOT", "SELECT_LOT_TARGET"));
        checks.add(evaluated("LOT_EXISTS", lot != null, "LOT_NOT_FOUND",
                lot == null ? "Lot 不存在" : "Lot 存在", "CHECK_LOT_CODE"));
        if (lot == null) {
            checks.add(notEvaluated("LOT_VERSION_MATCH", "Lot 不存在，无法比较版本"));
        } else {
            checks.add(evaluated("LOT_VERSION_MATCH",
                    request.getExpectedVersion().equals(lot.getVersion()),
                    "LOT_VERSION_CONFLICT", "请求版本必须等于 Lot 当前版本", "REFRESH_LOT"));
        }
        return checks;
    }

    protected Lot findLot(String lotCode) {
        return lotMapper.selectOne(
                Wrappers.<Lot>lambdaQuery().eq(Lot::getCode, lotCode));
    }

    protected RouteStep addCurrentStepCheck(List<RuleCheckResultTO> checks, Lot lot) {
        if (lot == null) {
            checks.add(notEvaluated("CURRENT_ROUTE_STEP_VALID", "Lot 不存在，无法检查当前 Step"));
            return null;
        }
        if (lot.getCurrentRouteStepId() == null) {
            checks.add(evaluated("CURRENT_ROUTE_STEP_VALID", false, "LOT_STATE_INVALID",
                    "Lot 没有当前工艺 Step", "CHECK_ROUTE"));
            return null;
        }
        RouteStep routeStep = routeStepMapper.selectById(lot.getCurrentRouteStepId());
        boolean valid = LotRoutePolicy.isCurrentStepValid(lot, routeStep);
        checks.add(evaluated("CURRENT_ROUTE_STEP_VALID", valid, "LOT_STATE_INVALID",
                "当前 Step 必须存在、属于 Lot Route 并配置设备组",
                "FIX_ROUTE_STEP_CONFIGURATION"));
        return valid ? routeStep : null;
    }

    protected Equipment findEquipment(Long equipmentId) {
        return equipmentId == null ? null : equipmentMapper.selectById(equipmentId);
    }
}