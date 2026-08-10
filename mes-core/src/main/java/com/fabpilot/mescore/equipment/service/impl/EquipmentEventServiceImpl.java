package com.fabpilot.mescore.equipment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.common.command.CommandExecutionSupport;
import com.fabpilot.mescore.equipment.dto.EquipmentEventResultTO;
import com.fabpilot.mescore.equipment.dto.ExecuteEquipmentEventRequestTO;
import com.fabpilot.mescore.equipment.exception.EquipmentCommandErrorCode;
import com.fabpilot.mescore.equipment.exception.EquipmentCommandException;
import com.fabpilot.mescore.equipment.mapper.EquipmentEventDefinitionMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentEventDefinition;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import com.fabpilot.mescore.equipment.service.EquipmentEventService;
import com.fabpilot.mescore.equipment.service.policy.EquipmentEventPolicy;
import com.fabpilot.mescore.equipment.service.recording.EquipmentHistoryFactory;
import com.fabpilot.mescore.equipment.service.recording.EquipmentHistoryRecordTO;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentEventServiceImpl implements EquipmentEventService {
    @Autowired private EquipmentMapper equipmentMapper;
    @Autowired private EquipmentAlarmMapper equipmentAlarmMapper;
    @Autowired private EquipmentEventDefinitionMapper eventDefinitionMapper;
    @Autowired private EquipmentHistoryMapper equipmentHistoryMapper;
    @Autowired private CommandExecutionSupport commandExecutionSupport;

    /**
     * 故障上报业务流程：
     * 1. 先读取设备，再按幂等键检查历史；重试相同请求直接返回，不重复写库。
     * 2. 校验 expectedVersion，防止上报方使用旧设备快照覆盖并发变化。
     * 3. 读取事件定义并确认它会把设备置为 D + DOWN。
     * 4. 校验设备当前状态符合事件定义的来源状态，例如 VACUUM_LOW 只允许 U + PROC。
     * 5. 使用“设备编号 + 原状态 + 原版本”条件更新快照，再追加不可变 EquipmentHistory。
     * 更新快照和写历史处于同一事务；任何一步失败都会整体回滚。
     */
    @Override
    @Transactional
    public EquipmentEventResultTO executeEvent(ExecuteEquipmentEventRequestTO request) {
        Equipment equipment = findEquipment(request.getEquipmentCode());

        EquipmentEventResultTO repeated = findIdempotentResult(equipment, request);
        if (repeated != null) {
            return repeated;
        }

        validateExpectedVersion(equipment, request.getExpectedVersion());
        EquipmentEventDefinition definition = findActiveDefinition(request.getEventCode());
        validateRequiredReason(definition, request);
        validateSourceState(equipment, definition);

        long nextVersion = commandExecutionSupport.nextVersion(equipment.getVersion());
        LocalDateTime occurredAt = LocalDateTime.now();
        updateEquipmentSnapshot(equipment, definition, nextVersion, occurredAt);
        appendHistory(equipment, definition, request, nextVersion, occurredAt);
        createAlarmWhenEquipmentGoesDown(equipment, definition, request, occurredAt);

        return buildResult(equipment, definition, nextVersion, false);
    }

    private Equipment findEquipment(String equipmentCode) {
        Equipment equipment = equipmentMapper.selectOne(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getCode, equipmentCode));
        if (equipment == null) {
            throw new EquipmentCommandException(EquipmentCommandErrorCode.EQUIPMENT_NOT_FOUND,
                    "Equipment not found: " + equipmentCode);
        }
        return equipment;
    }

    private EquipmentEventDefinition findActiveDefinition(String eventCode) {
        EquipmentEventDefinition definition = eventDefinitionMapper.selectOne(
                Wrappers.<EquipmentEventDefinition>lambdaQuery()
                        .eq(EquipmentEventDefinition::getEventCode, eventCode)
                        .eq(EquipmentEventDefinition::getStatus, "ACTIVE"));
        if (definition == null) {
            throw new EquipmentCommandException(EquipmentCommandErrorCode.EVENT_NOT_FOUND,
                    "Active equipment event not found: " + eventCode);
        }
        return definition;
    }

    /** 事件定义要求原因时，原因码和原因说明必须同时填写，保证统计和人工审计都有依据。 */
    private void validateRequiredReason(EquipmentEventDefinition definition,
            ExecuteEquipmentEventRequestTO request) {
        if (!EquipmentEventPolicy.isReasonSatisfied(
                definition, request.getReasonCode(), request.getReasonText())) {
            throw new EquipmentCommandException(EquipmentCommandErrorCode.REASON_REQUIRED,
                    "Event requires both reasonCode and reasonText: " + definition.getEventCode());
        }
    }
    /** 定义中的来源状态为空表示不限制；非空时必须与当前设备快照完全一致。 */
    private void validateSourceState(Equipment equipment, EquipmentEventDefinition definition) {
        boolean upDownAllowed = EquipmentEventPolicy.isUpDownSourceAllowed(
                equipment, definition);
        boolean primaryAllowed = EquipmentEventPolicy.isPrimarySourceAllowed(
                equipment, definition);
        if (!upDownAllowed || !primaryAllowed) {
            throw new EquipmentCommandException(EquipmentCommandErrorCode.STATE_INVALID,
                    "Equipment state does not match event source state");
        }
    }

    private void validateExpectedVersion(Equipment equipment, Long expectedVersion) {
        commandExecutionSupport.validateExpectedVersion(expectedVersion, equipment.getVersion(),
                () -> new EquipmentCommandException(
                        EquipmentCommandErrorCode.VERSION_CONFLICT, "Equipment version is stale"));
    }

    /**
     * 幂等身份由设备、事件和原因共同决定；同键改设备、改事件或改原因都会被视为冲突。
     * 幂等检查先于版本和状态校验，因此网络重试即使携带旧版本也能安全返回首次结果。
     */
    private EquipmentEventResultTO findIdempotentResult(
            Equipment equipment, ExecuteEquipmentEventRequestTO request) {
        EquipmentHistory previous = equipmentHistoryMapper.selectOne(
                Wrappers.<EquipmentHistory>lambdaQuery()
                        .eq(EquipmentHistory::getIdempotencyKey, request.getIdempotencyKey()));
        if (previous == null) {
            return null;
        }

        boolean sameIntent = previous.getEquipmentId().equals(equipment.getId())
                && request.getEventCode().equals(previous.getEventCode())
                && request.getOperatorType().equals(previous.getOperatorType())
                && Objects.equals(request.getReasonCode(), previous.getReasonCode())
                && Objects.equals(request.getReasonText(), previous.getReasonText());
        if (!sameIntent) {
            throw new EquipmentCommandException(
                    EquipmentCommandErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used by another equipment event");
        }
        return new EquipmentEventResultTO(equipment.getCode(), previous.getEventCode(),
                equipment.getUpDownStatus(), equipment.getPrimaryStatus(),
                equipment.getVersion(), true);
    }

    /** 条件更新同时比较原状态和原版本，防止校验完成后又被另一个事务抢先修改。 */
    private void updateEquipmentSnapshot(Equipment equipment,
            EquipmentEventDefinition definition, long nextVersion, LocalDateTime occurredAt) {
        int updated = equipmentMapper.update(null,
                Wrappers.<Equipment>lambdaUpdate()
                        .eq(Equipment::getId, equipment.getId())
                        .eq(Equipment::getVersion, equipment.getVersion())
                        .eq(Equipment::getUpDownStatus, equipment.getUpDownStatus())
                        .eq(Equipment::getPrimaryStatus, equipment.getPrimaryStatus())
                        .set(Equipment::getStatus, legacyStatus(definition))
                        .set(Equipment::getUpDownStatus, definition.getToUpDownStatus())
                        .set(Equipment::getPrimaryStatus, definition.getToPrimaryStatus())
                        .set(Equipment::getLastEventCode, definition.getEventCode())
                        .set(Equipment::getLastEventAt, occurredAt)
                        .set(Equipment::getVersion, nextVersion));
        if (updated != 1) {
            throw new EquipmentCommandException(EquipmentCommandErrorCode.VERSION_CONFLICT,
                    "Equipment changed concurrently while executing event");
        }
    }

    /** 将双状态同步映射为旧 status 字段，兼容现有查询。 */
    private String legacyStatus(EquipmentEventDefinition definition) {
        if ("DOWN".equals(definition.getToPrimaryStatus())) return "DOWN";
        if ("MAINTENANCE".equals(definition.getToPrimaryStatus())) return "MAINTENANCE";
        if ("PROC".equals(definition.getToPrimaryStatus())) return "RUN";
        return "IDLE";
    }

    private void appendHistory(Equipment equipment, EquipmentEventDefinition definition,
            ExecuteEquipmentEventRequestTO request, long nextVersion, LocalDateTime occurredAt) {
        equipmentHistoryMapper.insert(EquipmentHistoryFactory.create(
                EquipmentHistoryRecordTO.builder()
                        .equipment(equipment)
                        .eventCode(definition.getEventCode())
                        .upDownStatusAfter(definition.getToUpDownStatus())
                        .primaryStatusAfter(definition.getToPrimaryStatus())
                        .operatorType(request.getOperatorType())
                        .operatorRole(definition.getEventCategory())
                        .reasonCode(request.getReasonCode())
                        .reasonText(request.getReasonText())
                        .request(request)
                        .nextVersion(nextVersion)
                        .occurredAt(occurredAt)
                        .build()));
    }

    /**
     * 只有把设备切换到 D + DOWN 的事件才代表一个新故障，需要创建待处理告警。
     *
     * <p>告警码优先使用 reasonCode，便于同类故障统计；未提供时退回事件码。
     * sourceIdempotencyKey 与设备事件共用同一业务键，数据库唯一约束提供最后一道防重复保护。
     * 本方法运行在 executeEvent 的事务中，因此告警插入失败时设备快照和历史也会回滚。</p>
     */
    private void createAlarmWhenEquipmentGoesDown(Equipment equipment,
            EquipmentEventDefinition definition, ExecuteEquipmentEventRequestTO request,
            LocalDateTime occurredAt) {
        if (!"D".equals(definition.getToUpDownStatus())
                || !"DOWN".equals(definition.getToPrimaryStatus())) {
            return;
        }

        EquipmentAlarm alarm = new EquipmentAlarm();
        alarm.setEquipmentId(equipment.getId());
        alarm.setAlarmCode(StringUtils.hasText(request.getReasonCode())
                ? request.getReasonCode() : definition.getEventCode());
        alarm.setSeverity("HIGH");
        alarm.setStatus("ACTIVE");
        alarm.setSourceEventCode(definition.getEventCode());
        alarm.setSourceIdempotencyKey(request.getIdempotencyKey());
        alarm.setMessage(StringUtils.hasText(request.getReasonText())
                ? request.getReasonText() : definition.getName());
        alarm.setVersion(0L);
        alarm.setOpenedAt(occurredAt);
        equipmentAlarmMapper.insert(alarm);
    }
    private EquipmentEventResultTO buildResult(Equipment equipment,
            EquipmentEventDefinition definition, long version, boolean idempotent) {
        return new EquipmentEventResultTO(equipment.getCode(), definition.getEventCode(),
                definition.getToUpDownStatus(), definition.getToPrimaryStatus(),
                version, idempotent);
    }
}