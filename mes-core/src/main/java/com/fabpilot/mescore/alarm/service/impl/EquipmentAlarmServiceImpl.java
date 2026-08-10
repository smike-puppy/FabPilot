package com.fabpilot.mescore.alarm.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.alarm.dto.AlarmActionRequestTO;
import com.fabpilot.mescore.alarm.dto.AlarmActionResultTO;
import com.fabpilot.mescore.alarm.enums.AlarmAction;
import com.fabpilot.mescore.alarm.exception.AlarmCommandErrorCode;
import com.fabpilot.mescore.alarm.exception.AlarmCommandException;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmActionHistoryMapper;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.alarm.model.EquipmentAlarmActionHistory;
import com.fabpilot.mescore.alarm.service.EquipmentAlarmService;
import com.fabpilot.mescore.alarm.service.policy.AlarmActionPolicy;
import com.fabpilot.mescore.common.command.CommandExecutionSupport;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentAlarmServiceImpl implements EquipmentAlarmService {

    @Autowired
    private EquipmentAlarmMapper alarmMapper;

    @Autowired
    private EquipmentAlarmActionHistoryMapper historyMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private CommandExecutionSupport commandExecutionSupport;

    /**
     * 执行告警动作的完整业务流程。
     *
     * <p>先识别幂等重试，再校验版本和状态机；关闭动作还要确认设备已经恢复为 U + IDLE。
     * 最后使用带原状态、原版本条件的更新语句修改快照，并在同一事务内追加审计历史。
     */
    @Override
    @Transactional
    public AlarmActionResultTO executeAction(AlarmActionRequestTO request) {
        EquipmentAlarm alarm = findAlarm(request.getAlarmId());
        AlarmActionResultTO repeatedResult = findIdempotentResult(alarm, request);
        if (repeatedResult != null) {
            return repeatedResult;
        }

        validateVersion(alarm, request.getExpectedVersion());
        AlarmAction action = AlarmAction.fromDatabaseValue(request.getAction());
        validateCurrentStatus(alarm, action);
        if (AlarmAction.CLOSE == action) {
            validateEquipmentRecovered(alarm);
        }

        long nextVersion = commandExecutionSupport.nextVersion(alarm.getVersion());
        LocalDateTime occurredAt = LocalDateTime.now();
        updateAlarmSnapshot(alarm, action, request.getOperatorId(), nextVersion, occurredAt);
        appendActionHistory(alarm, request, nextVersion, occurredAt);
        return new AlarmActionResultTO(
                alarm.getId(), action.targetStatus().databaseValue(), nextVersion, false);
    }

    private EquipmentAlarm findAlarm(Long alarmId) {
        EquipmentAlarm alarm = alarmMapper.selectById(alarmId);
        if (alarm == null) {
            throw new AlarmCommandException(
                    AlarmCommandErrorCode.ALARM_NOT_FOUND, "Alarm not found: " + alarmId);
        }
        return alarm;
    }

    /** 同一幂等键必须对应相同告警、动作和操作人，否则属于错误复用。 */
    private AlarmActionResultTO findIdempotentResult(
            EquipmentAlarm alarm, AlarmActionRequestTO request) {
        EquipmentAlarmActionHistory previous = historyMapper.selectOne(
                Wrappers.<EquipmentAlarmActionHistory>lambdaQuery()
                        .eq(
                                EquipmentAlarmActionHistory::getIdempotencyKey,
                                request.getIdempotencyKey()));
        if (previous == null) {
            return null;
        }

        boolean sameIntent = previous.getAlarmId().equals(alarm.getId())
                && previous.getAction().equals(request.getAction())
                && previous.getOperatorId().equals(request.getOperatorId());
        if (!sameIntent) {
            throw new AlarmCommandException(
                    AlarmCommandErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used by another alarm action");
        }
        return new AlarmActionResultTO(
                alarm.getId(), alarm.getStatus(), alarm.getVersion(), true);
    }

    /** 客户端必须基于当前版本操作，避免两个人从同一旧页面提交时互相覆盖。 */
    private void validateVersion(EquipmentAlarm alarm, Long expectedVersion) {
        commandExecutionSupport.validateExpectedVersion(
                expectedVersion,
                alarm.getVersion(),
                () -> new AlarmCommandException(
                        AlarmCommandErrorCode.VERSION_CONFLICT, "Alarm version is stale"));
    }

    /**
     * 校验动作允许的来源状态。
     *
     * <p>确认只能处理 ACTIVE 告警；关闭只能处理 ACKNOWLEDGED 告警，防止跳过人工确认直接关闭。
     */
    private void validateCurrentStatus(EquipmentAlarm alarm, AlarmAction action) {
        String requiredStatus = action.requiredStatus().databaseValue();
        if (!AlarmActionPolicy.isCurrentStatusAllowed(alarm, action)) {
            throw new AlarmCommandException(
                    AlarmCommandErrorCode.STATE_INVALID,
                    "Only " + requiredStatus + " alarm can execute " + action.databaseValue());
        }
    }

    /** 关闭代表异常已经结束，因此必须同时确认设备真实快照已经恢复为 U + IDLE。 */
    private void validateEquipmentRecovered(EquipmentAlarm alarm) {
        Equipment equipment = equipmentMapper.selectById(alarm.getEquipmentId());
        boolean recovered = AlarmActionPolicy.isEquipmentRecovered(equipment);
        if (!recovered) {
            throw new AlarmCommandException(
                    AlarmCommandErrorCode.EQUIPMENT_NOT_RECOVERED,
                    "Equipment must be U + IDLE before alarm close");
        }
    }

    /** 更新条件同时包含原状态和原版本，防止校验后被另一个事务抢先处理。 */
    private void updateAlarmSnapshot(
            EquipmentAlarm alarm,
            AlarmAction action,
            String operatorId,
            long nextVersion,
            LocalDateTime occurredAt) {
        String targetStatus = action.targetStatus().databaseValue();
        LambdaUpdateWrapper<EquipmentAlarm> update = Wrappers.<EquipmentAlarm>lambdaUpdate()
                .eq(EquipmentAlarm::getId, alarm.getId())
                .eq(EquipmentAlarm::getVersion, alarm.getVersion())
                .eq(EquipmentAlarm::getStatus, alarm.getStatus())
                .set(EquipmentAlarm::getStatus, targetStatus)
                .set(EquipmentAlarm::getVersion, nextVersion);
        if (AlarmAction.ACKNOWLEDGE == action) {
            update.set(EquipmentAlarm::getAcknowledgedBy, operatorId)
                    .set(EquipmentAlarm::getAcknowledgedAt, occurredAt);
        } else {
            update.set(EquipmentAlarm::getClosedBy, operatorId)
                    .set(EquipmentAlarm::getClosedAt, occurredAt);
        }

        if (alarmMapper.update(null, update) != 1) {
            throw new AlarmCommandException(
                    AlarmCommandErrorCode.VERSION_CONFLICT, "Alarm changed concurrently");
        }
    }

    /** 告警快照更新成功后追加不可变历史；事务失败时快照和历史一起回滚。 */
    private void appendActionHistory(
            EquipmentAlarm alarm,
            AlarmActionRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        EquipmentAlarmActionHistory history = new EquipmentAlarmActionHistory();
        history.setAlarmId(alarm.getId());
        history.setAction(request.getAction());
        history.setOperatorId(request.getOperatorId());
        history.setIdempotencyKey(request.getIdempotencyKey());
        history.setAlarmVersionBefore(alarm.getVersion());
        history.setAlarmVersionAfter(nextVersion);
        history.setOccurredAt(occurredAt);
        historyMapper.insert(history);
    }
}