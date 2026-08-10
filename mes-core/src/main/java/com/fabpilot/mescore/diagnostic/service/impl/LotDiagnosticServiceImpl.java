package com.fabpilot.mescore.diagnostic.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.alarm.enums.AlarmStatus;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.diagnostic.dto.LotDiagnosticContextTO;
import com.fabpilot.mescore.diagnostic.exception.LotNotFoundException;
import com.fabpilot.mescore.diagnostic.service.LotDiagnosticService;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.model.LotTransaction;
import com.fabpilot.mescore.process.mapper.OperationMapper;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.process.model.Operation;
import com.fabpilot.mescore.process.model.RouteStep;
import com.fabpilot.mescore.workorder.mapper.WorkOrderMapper;
import com.fabpilot.mescore.workorder.model.WorkOrder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 诊断上下文聚合实现。
 *
 * <p>这里仅把 MES 当前快照和不可变历史转为 API 模型，不改变 Lot 或 Equipment；
 * 任何状态变更都留给后续具备事务、审批、幂等和审计能力的写侧服务。</p>
 */
@Service
public class LotDiagnosticServiceImpl implements LotDiagnosticService {
    /** 控制单次诊断返回量，避免完整历史无限制地传给调用方或 Agent。 */
    private static final int HISTORY_LIMIT = 10;

    @Autowired
    private LotMapper lotMapper;

    @Autowired
    private LotTransactionMapper lotTransactionMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private RouteStepMapper routeStepMapper;

    @Autowired
    private OperationMapper operationMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentHistoryMapper equipmentHistoryMapper;

    @Autowired
    private EquipmentAlarmMapper equipmentAlarmMapper;

    /**
     * 在同一只读事务中完成本次聚合，防止查询过程意外触发写入。
     *
     * <p>当前逐项读取以保持诊断语义清楚；当出现批量诊断需求时，再将其收敛为专用 Mapper
     * 查询，避免在 Controller 中拼接 SQL。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public LotDiagnosticContextTO getDiagnosticContext(String lotCode) {
        // Lot code 是业务唯一标识，先确定本次诊断的根实体。
        Lot lot = lotMapper.selectOne(
                Wrappers.<Lot>lambdaQuery().eq(Lot::getCode, lotCode));
        if (lot == null) {
            throw new LotNotFoundException(lotCode);
        }

        // Lot 与工单存在外键关系；缺失表示数据损坏，不能返回看似完整的误导性诊断。
        WorkOrder workOrder = Objects.requireNonNull(
                workOrderMapper.selectById(lot.getWorkOrderId()),
                "Lot references a missing work order: " + lot.getWorkOrderId());

        // 已完工或刚创建的 Lot 可能没有当前 Step/Equipment，因此两者允许为空。
        RouteStep step = lot.getCurrentRouteStepId() == null
                ? null : routeStepMapper.selectById(lot.getCurrentRouteStepId());
        Operation operation = step == null || step.getOperationId() == null
                ? null : operationMapper.selectById(step.getOperationId());
        Equipment equipment = lot.getCurrentEquipmentId() == null
                ? null : equipmentMapper.selectById(lot.getCurrentEquipmentId());

        // 履历倒序截取，首条即最近一次状态变化，且 LotTransaction 只作为事实读取。
        List<LotTransaction> transactions = lotTransactionMapper.selectList(
                Wrappers.<LotTransaction>lambdaQuery()
                        .eq(LotTransaction::getLotId, lot.getId())
                        .orderByDesc(LotTransaction::getOccurredAt)
                        .last("LIMIT " + HISTORY_LIMIT));

        // 没有当前设备时不查询设备历史，避免把无关设备事件混入诊断结果。
        List<EquipmentHistory> equipmentEvents = equipment == null
                ? List.of()
                : equipmentHistoryMapper.selectList(
                        Wrappers.<EquipmentHistory>lambdaQuery()
                                .eq(EquipmentHistory::getEquipmentId, equipment.getId())
                                .orderByDesc(EquipmentHistory::getOccurredAt)
                                .last("LIMIT " + HISTORY_LIMIT));

        // 持久化模型不直接暴露给 API，避免表结构变化破坏外部响应契约。
        // 诊断只关心尚未结束的异常。CLOSED 告警属于历史事实，继续返回会让 Agent
        // 把已经处理完成的问题误判为当前阻塞原因。
        List<EquipmentAlarm> activeAlarms = equipment == null
                ? List.of()
                : equipmentAlarmMapper.selectList(
                        Wrappers.<EquipmentAlarm>lambdaQuery()
                                .eq(EquipmentAlarm::getEquipmentId, equipment.getId())
                                .in(
                                        EquipmentAlarm::getStatus,
                                        AlarmStatus.ACTIVE.databaseValue(),
                                        AlarmStatus.ACKNOWLEDGED.databaseValue())
                                .orderByDesc(EquipmentAlarm::getOpenedAt)
                                .last("LIMIT " + HISTORY_LIMIT));
        return new LotDiagnosticContextTO(
                toLotSnapshot(lot),
                toWorkOrderSnapshot(workOrder),
                toStepSnapshot(step, operation),
                toEquipmentSnapshot(equipment),
                transactions.stream().map(this::toLotHistoryItem).toList(),
                equipmentEvents.stream().map(this::toEquipmentHistoryItem).toList(),
                activeAlarms.stream().map(this::toAlarmSnapshot).toList());
    }

    private LotDiagnosticContextTO.LotSnapshot toLotSnapshot(Lot lot) {
        return new LotDiagnosticContextTO.LotSnapshot(
                lot.getCode(), lot.getQuantity(), lot.getExecutionStatus(),
                lot.getHoldStatus(), lot.getLastTransactionCode(),
                lot.getLastTransactionAt(), lot.getVersion());
    }

    private LotDiagnosticContextTO.WorkOrderSnapshot toWorkOrderSnapshot(
            WorkOrder workOrder) {
        return new LotDiagnosticContextTO.WorkOrderSnapshot(
                workOrder.getCode(), workOrder.getStatus(),
                workOrder.getPlanQuantity(), workOrder.getDueAt());
    }

    private LotDiagnosticContextTO.StepSnapshot toStepSnapshot(
            RouteStep step, Operation operation) {
        if (step == null) {
            return null;
        }
        return new LotDiagnosticContextTO.StepSnapshot(
                step.getStepCode(), step.getName(), step.getSequenceNo(),
                operation == null ? null : operation.getCode(),
                operation == null ? null : operation.getName(),
                step.getRequiredEquipmentGroupId());
    }

    private LotDiagnosticContextTO.EquipmentSnapshot toEquipmentSnapshot(
            Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return new LotDiagnosticContextTO.EquipmentSnapshot(
                equipment.getCode(), equipment.getName(), equipment.getEquipmentType(),
                equipment.getUpDownStatus(), equipment.getPrimaryStatus(),
                equipment.getLastEventCode(), equipment.getLastEventAt(),
                equipment.getVersion());
    }

    private LotDiagnosticContextTO.LotHistoryItem toLotHistoryItem(
            LotTransaction transaction) {
        return new LotDiagnosticContextTO.LotHistoryItem(
                transaction.getTransactionType(), transaction.getExecutionStatusBefore(),
                transaction.getExecutionStatusAfter(), transaction.getHoldStatusBefore(),
                transaction.getHoldStatusAfter(), transaction.getOperatorId(),
                transaction.getReasonCode(), transaction.getReasonText(),
                transaction.getOccurredAt());
    }

    private LotDiagnosticContextTO.EquipmentHistoryItem toEquipmentHistoryItem(
            EquipmentHistory event) {
        return new LotDiagnosticContextTO.EquipmentHistoryItem(
                event.getEventCode(), event.getUpDownStatusBefore(),
                event.getUpDownStatusAfter(), event.getPrimaryStatusBefore(),
                event.getPrimaryStatusAfter(), event.getOperatorId(),
                event.getReasonCode(), event.getReasonText(), event.getOccurredAt());
    }

    /** 把告警持久化模型转换为稳定的诊断契约，并计算查询时刻的持续时间。 */
    private LotDiagnosticContextTO.AlarmSnapshot toAlarmSnapshot(EquipmentAlarm alarm) {
        long openDurationSeconds = alarm.getOpenedAt() == null
                ? 0L
                : Math.max(
                        0L,
                        Duration.between(alarm.getOpenedAt(), LocalDateTime.now()).getSeconds());
        return new LotDiagnosticContextTO.AlarmSnapshot(
                alarm.getId(),
                alarm.getAlarmCode(),
                alarm.getSeverity(),
                alarm.getStatus(),
                alarm.getSourceEventCode(),
                alarm.getMessage(),
                alarm.getOpenedAt(),
                alarm.getAcknowledgedBy(),
                alarm.getAcknowledgedAt(),
                openDurationSeconds,
                alarm.getVersion());
    }}
