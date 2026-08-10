package com.fabpilot.mescore.commandvalidation.service.impl;

import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.build;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.evaluated;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.notEvaluated;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import com.fabpilot.mescore.commandvalidation.service.CommandValidator;
import com.fabpilot.mescore.equipment.mapper.EquipmentEventDefinitionMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentEventDefinition;
import com.fabpilot.mescore.equipment.service.policy.EquipmentEventPolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 设备事件只读预检查：设备、版本、活动事件定义、原因和来源状态必须全部满足。 */
@Component
public class EquipmentEventCommandValidator implements CommandValidator {
    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentEventDefinitionMapper eventDefinitionMapper;

    @Override
    public CommandType supportedCommandType() {
        return CommandType.EXECUTE_EQUIPMENT_EVENT;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        List<RuleCheckResultTO> checks = new ArrayList<>();
        checks.add(evaluated("TARGET_TYPE_EQUIPMENT",
                request.getTargetType() == TargetType.EQUIPMENT, "TARGET_TYPE_INVALID",
                "设备事件要求 targetType=EQUIPMENT", "SELECT_EQUIPMENT_TARGET"));
        Equipment equipment = findEquipment(request.getTargetCode());
        checks.add(evaluated("EQUIPMENT_EXISTS", equipment != null, "EQUIPMENT_NOT_FOUND",
                equipment == null ? "设备不存在" : "设备存在", "CHECK_EQUIPMENT_CODE"));
        addVersionCheck(checks, request, equipment);

        boolean eventCodeProvided = StringUtils.hasText(request.getEventCode());
        checks.add(evaluated("EVENT_CODE_PROVIDED", eventCodeProvided,
                "EQUIPMENT_EVENT_NOT_FOUND", "设备事件预检查必须提供 eventCode",
                "SELECT_EQUIPMENT_EVENT"));
        EquipmentEventDefinition definition = eventCodeProvided
                ? findActiveDefinition(request.getEventCode()) : null;
        checks.add(evaluated("ACTIVE_EVENT_DEFINITION_EXISTS", definition != null,
                "EQUIPMENT_EVENT_NOT_FOUND", "eventCode 必须对应启用中的事件定义",
                "CHECK_EVENT_DEFINITION"));
        addDefinitionChecks(checks, request, equipment, definition);
        return build(request, equipment == null ? null : equipment.getVersion(), checks);
    }

    private Equipment findEquipment(String equipmentCode) {
        return equipmentMapper.selectOne(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getCode, equipmentCode));
    }

    private EquipmentEventDefinition findActiveDefinition(String eventCode) {
        return eventDefinitionMapper.selectOne(
                Wrappers.<EquipmentEventDefinition>lambdaQuery()
                        .eq(EquipmentEventDefinition::getEventCode, eventCode)
                        .eq(EquipmentEventDefinition::getStatus, "ACTIVE"));
    }

    private void addVersionCheck(
            List<RuleCheckResultTO> checks,
            CommandValidationRequestTO request,
            Equipment equipment) {
        if (equipment == null) {
            checks.add(notEvaluated("EQUIPMENT_VERSION_MATCH",
                    "设备不存在，无法比较版本"));
            return;
        }
        checks.add(evaluated("EQUIPMENT_VERSION_MATCH",
                request.getExpectedVersion().equals(equipment.getVersion()),
                "EQUIPMENT_VERSION_CONFLICT", "请求版本必须等于设备当前版本",
                "REFRESH_EQUIPMENT"));
    }

    private void addDefinitionChecks(
            List<RuleCheckResultTO> checks,
            CommandValidationRequestTO request,
            Equipment equipment,
            EquipmentEventDefinition definition) {
        if (definition == null) {
            checks.add(notEvaluated("EVENT_REASON_SATISFIED", "事件定义不存在，无法判断原因要求"));
            checks.add(notEvaluated("EVENT_UP_DOWN_SOURCE_MATCH", "事件定义不存在，无法检查 U/D 来源状态"));
            checks.add(notEvaluated("EVENT_PRIMARY_SOURCE_MATCH", "事件定义不存在，无法检查主状态"));
            return;
        }
        checks.add(evaluated("EVENT_REASON_SATISFIED",
                EquipmentEventPolicy.isReasonSatisfied(
                        definition, request.getReasonCode(), request.getReasonText()),
                "EQUIPMENT_EVENT_REASON_REQUIRED", "事件要求原因时必须同时填写原因码和原因说明",
                "PROVIDE_EVENT_REASON"));
        if (equipment == null) {
            checks.add(notEvaluated("EVENT_UP_DOWN_SOURCE_MATCH", "设备不存在，无法检查 U/D 来源状态"));
            checks.add(notEvaluated("EVENT_PRIMARY_SOURCE_MATCH", "设备不存在，无法检查主状态"));
            return;
        }
        checks.add(evaluated("EVENT_UP_DOWN_SOURCE_MATCH",
                EquipmentEventPolicy.isUpDownSourceAllowed(equipment, definition),
                "EQUIPMENT_STATE_INVALID", "设备 U/D 状态必须匹配事件定义来源",
                "SELECT_MATCHING_EVENT"));
        checks.add(evaluated("EVENT_PRIMARY_SOURCE_MATCH",
                EquipmentEventPolicy.isPrimarySourceAllowed(equipment, definition),
                "EQUIPMENT_STATE_INVALID", "设备主状态必须匹配事件定义来源",
                "SELECT_MATCHING_EVENT"));
    }
}