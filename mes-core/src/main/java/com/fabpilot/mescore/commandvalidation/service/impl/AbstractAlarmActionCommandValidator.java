package com.fabpilot.mescore.commandvalidation.service.impl;

import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.build;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.evaluated;
import static com.fabpilot.mescore.commandvalidation.service.support.CommandCheckFactory.notEvaluated;

import com.fabpilot.mescore.alarm.enums.AlarmAction;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.alarm.service.policy.AlarmActionPolicy;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** 告警动作 Validator 的公共只读流程。 */
abstract class AbstractAlarmActionCommandValidator {
    @Autowired
    private EquipmentAlarmMapper alarmMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    protected CommandValidationResultTO validateAction(
            CommandValidationRequestTO request,
            AlarmAction action) {
        List<RuleCheckResultTO> checks = new ArrayList<>();
        checks.add(evaluated("TARGET_TYPE_ALARM", request.getTargetType() == TargetType.ALARM,
                "TARGET_TYPE_INVALID", "告警动作要求 targetType=ALARM", "SELECT_ALARM_TARGET"));
        boolean alarmIdProvided = request.getAlarmId() != null;
        checks.add(evaluated("ALARM_ID_PROVIDED", alarmIdProvided, "ALARM_NOT_FOUND",
                "告警动作预检查必须提供 alarmId", "SELECT_ALARM"));
        EquipmentAlarm alarm = alarmIdProvided ? alarmMapper.selectById(request.getAlarmId()) : null;
        checks.add(evaluated("ALARM_EXISTS", alarm != null, "ALARM_NOT_FOUND",
                alarm == null ? "告警不存在" : "告警存在", "CHECK_ALARM_ID"));
        addAlarmChecks(checks, request, alarm, action);
        if (AlarmAction.CLOSE == action) {
            addRecoveryCheck(checks, alarm);
        }
        return build(request, alarm == null ? null : alarm.getVersion(), checks);
    }

    private void addAlarmChecks(
            List<RuleCheckResultTO> checks,
            CommandValidationRequestTO request,
            EquipmentAlarm alarm,
            AlarmAction action) {
        if (alarm == null) {
            checks.add(notEvaluated("ALARM_VERSION_MATCH", "告警不存在，无法比较版本"));
            checks.add(notEvaluated("ALARM_STATUS_ALLOWED", "告警不存在，无法检查状态机"));
            return;
        }
        checks.add(evaluated("ALARM_VERSION_MATCH",
                request.getExpectedVersion().equals(alarm.getVersion()),
                "ALARM_VERSION_CONFLICT", "请求版本必须等于告警当前版本", "REFRESH_ALARM"));
        checks.add(evaluated("ALARM_STATUS_ALLOWED",
                AlarmActionPolicy.isCurrentStatusAllowed(alarm, action),
                "ALARM_STATE_INVALID", action == AlarmAction.ACKNOWLEDGE
                        ? "只有 ACTIVE 告警可以确认" : "只有 ACKNOWLEDGED 告警可以关闭",
                "CHECK_ALARM_STATUS"));
    }

    private void addRecoveryCheck(List<RuleCheckResultTO> checks, EquipmentAlarm alarm) {
        if (alarm == null) {
            checks.add(notEvaluated("ALARM_EQUIPMENT_RECOVERED", "告警不存在，无法读取关联设备"));
            return;
        }
        Equipment equipment = equipmentMapper.selectById(alarm.getEquipmentId());
        checks.add(evaluated("ALARM_EQUIPMENT_RECOVERED",
                AlarmActionPolicy.isEquipmentRecovered(equipment),
                "EQUIPMENT_NOT_RECOVERED", "关闭告警前设备必须恢复为 U + IDLE",
                "RECOVER_EQUIPMENT"));
    }
}