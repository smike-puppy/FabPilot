package com.fabpilot.mescore.lot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.common.enums.OperatorType;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;
import com.fabpilot.mescore.lot.enums.LotExecutionStatus;
import com.fabpilot.mescore.lot.enums.LotHoldStatus;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.model.LotTransaction;
import com.fabpilot.mescore.lot.service.LotCommandService;
import com.fabpilot.mescore.lot.service.support.LotCommandSupport;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.process.model.RouteStep;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lot 写侧状态机实现，集中管理状态校验、并发控制和生产履历。 */
@Service
public class LotCommandServiceImpl implements LotCommandService {

    private static final String EQUIPMENT_UP = "U";
    private static final String EQUIPMENT_IDLE = "IDLE";
    private static final String EQUIPMENT_PROCESSING = "PROC";

    @Autowired
    private LotMapper lotMapper;

    @Autowired
    private LotTransactionMapper lotTransactionMapper;

    @Autowired
    private RouteStepMapper routeStepMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentHistoryMapper equipmentHistoryMapper;

    @Autowired
    private LotCommandSupport lotCommandSupport;

    /**
     * 在同一事务内更新 Lot 快照并追加 Release 履历。
     *
     * <p>如果任意一步失败，Spring 会回滚整个事务，避免快照与履历不一致。</p>
     */
    @Override
    @Transactional
    public LotCommandResultTO release(String lotCode, ReleaseLotRequestTO request) {
        Lot lot = lotCommandSupport.findLot(lotCode);

        LotCommandResultTO repeatedResult = lotCommandSupport.findIdempotentResult(
                lot,
                request,
                LotTransactionType.RELEASE);
        if (repeatedResult != null) {
            return repeatedResult;
        }

        lotCommandSupport.validateExpectedVersion(lot, request.getExpectedVersion());
        validateReleaseState(lot);

        Long firstRouteStepId = resolveFirstRouteStepId(lot);
        long nextVersion = lotCommandSupport.nextVersion(lot);
        LocalDateTime occurredAt = LocalDateTime.now();

        updateReleasedLot(lot, request, firstRouteStepId, nextVersion, occurredAt);
        appendReleaseTransaction(lot, request, nextVersion, occurredAt);

        return lotCommandSupport.buildResult(
                lot,
                LotTransactionType.RELEASE,
                LotExecutionStatus.READY.databaseValue(),
                LotHoldStatus.RELEASED.databaseValue(),
                nextVersion,
                false);
    }

    /**
     * Track In 原子更新 Lot 与 Equipment 两个快照，并分别追加生产履历。
     *
     * <p>设备先以旧版本和 IDLE 状态参与条件更新；后续 Lot 更新或任一履历插入失败时，
     * 整个事务都会回滚，设备不会被遗留为无 Lot 占用的 PROC 状态。</p>
     */
    @Override
    @Transactional
    public LotCommandResultTO trackIn(String lotCode, TrackInLotRequestTO request) {
        Lot lot = lotCommandSupport.findLot(lotCode);
        Equipment equipment = findEquipment(request.getEquipmentCode());

        LotCommandResultTO repeatedResult = lotCommandSupport.findIdempotentResult(
                lot,
                request,
                LotTransactionType.TRACK_IN,
                equipment.getId());
        if (repeatedResult != null) {
            return repeatedResult;
        }

        lotCommandSupport.validateExpectedVersion(lot, request.getExpectedVersion());
        validateTrackInLotState(lot);

        RouteStep routeStep = findCurrentRouteStep(lot);
        validateEquipmentForTrackIn(equipment, routeStep);
        validateEquipmentNotOccupied(equipment);

        long nextLotVersion = lotCommandSupport.nextVersion(lot);
        long nextEquipmentVersion = equipment.getVersion() + 1;
        LocalDateTime occurredAt = LocalDateTime.now();

        updateEquipmentForTrackIn(
                equipment,
                nextEquipmentVersion,
                occurredAt);
        updateLotForTrackIn(
                lot,
                equipment,
                request,
                nextLotVersion,
                occurredAt);
        appendTrackInTransaction(
                lot,
                routeStep,
                equipment,
                request,
                nextLotVersion,
                occurredAt);
        appendTrackInEquipmentHistory(
                equipment,
                request,
                nextEquipmentVersion,
                occurredAt);

        return lotCommandSupport.buildResult(
                lot,
                LotTransactionType.TRACK_IN,
                LotExecutionStatus.RUNNING.databaseValue(),
                LotHoldStatus.RELEASED.databaseValue(),
                nextLotVersion,
                false);
    }

    /**
     * Track Out 原子释放设备并推进 Lot；末工序完成时写入 completedAt。
     */
    @Override
    @Transactional
    public LotCommandResultTO trackOut(String lotCode, TrackOutLotRequestTO request) {
        Lot lot = lotCommandSupport.findLot(lotCode);

        LotCommandResultTO repeatedResult = lotCommandSupport.findIdempotentResult(
                lot,
                request,
                LotTransactionType.TRACK_OUT);
        if (repeatedResult != null) {
            return repeatedResult;
        }

        lotCommandSupport.validateExpectedVersion(lot, request.getExpectedVersion());
        validateTrackOutLotState(lot);

        RouteStep currentRouteStep = findCurrentRouteStep(lot);
        Equipment equipment = findEquipment(lot.getCurrentEquipmentId());
        validateEquipmentForTrackOut(equipment);

        RouteStep nextRouteStep = findNextRouteStep(lot, currentRouteStep);
        boolean completed = nextRouteStep == null;
        String targetExecutionStatus = completed
                ? LotExecutionStatus.COMPLETED.databaseValue()
                : LotExecutionStatus.READY.databaseValue();
        long nextLotVersion = lotCommandSupport.nextVersion(lot);
        long nextEquipmentVersion = equipment.getVersion() + 1;
        LocalDateTime occurredAt = LocalDateTime.now();

        updateEquipmentForTrackOut(equipment, nextEquipmentVersion, occurredAt);
        updateLotForTrackOut(
                lot,
                equipment,
                nextRouteStep,
                request,
                targetExecutionStatus,
                nextLotVersion,
                occurredAt,
                completed);
        appendTrackOutTransaction(
                lot,
                currentRouteStep,
                equipment,
                request,
                targetExecutionStatus,
                nextLotVersion,
                occurredAt);
        appendTrackOutEquipmentHistory(
                equipment,
                request,
                nextEquipmentVersion,
                occurredAt);

        return lotCommandSupport.buildResult(
                lot,
                LotTransactionType.TRACK_OUT,
                targetExecutionStatus,
                LotHoldStatus.RELEASED.databaseValue(),
                nextLotVersion,
                false);
    }

    private Equipment findEquipment(String equipmentCode) {
        Equipment equipment = equipmentMapper.selectOne(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getCode, equipmentCode));
        if (equipment == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_NOT_FOUND,
                    "Equipment not found: " + equipmentCode);
        }
        return equipment;
    }

    private Equipment findEquipment(Long equipmentId) {
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_NOT_FOUND,
                    "Current equipment not found: " + equipmentId);
        }
        return equipment;
    }

    private void validateReleaseState(Lot lot) {
        boolean releasable = LotExecutionStatus.CREATED.databaseValue()
                        .equals(lot.getExecutionStatus())
                && LotHoldStatus.RELEASED.databaseValue().equals(lot.getHoldStatus());
        if (!releasable) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Only CREATED and RELEASED Lot can be released");
        }
    }

    private void validateTrackInLotState(Lot lot) {
        boolean trackInAllowed = LotExecutionStatus.READY.databaseValue()
                        .equals(lot.getExecutionStatus())
                && LotHoldStatus.RELEASED.databaseValue().equals(lot.getHoldStatus())
                && lot.getCurrentEquipmentId() == null;
        if (!trackInAllowed) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Only READY and RELEASED Lot without equipment can track in");
        }
    }

    private void validateTrackOutLotState(Lot lot) {
        boolean trackOutAllowed = LotExecutionStatus.RUNNING.databaseValue()
                        .equals(lot.getExecutionStatus())
                && LotHoldStatus.RELEASED.databaseValue().equals(lot.getHoldStatus())
                && lot.getCurrentRouteStepId() != null
                && lot.getCurrentEquipmentId() != null;
        if (!trackOutAllowed) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Only RUNNING and RELEASED Lot with equipment can track out");
        }
    }

    /** Lot 尚未指定当前 Step 时，Release 按序号选择路线中的第一道工序。 */
    private Long resolveFirstRouteStepId(Lot lot) {
        if (lot.getCurrentRouteStepId() != null) {
            return lot.getCurrentRouteStepId();
        }

        RouteStep firstStep = routeStepMapper.selectOne(
                Wrappers.<RouteStep>lambdaQuery()
                        .eq(RouteStep::getRouteId, lot.getRouteId())
                        .orderByAsc(RouteStep::getSequenceNo)
                        .last("LIMIT 1"));
        if (firstStep == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Lot route has no step");
        }
        return firstStep.getId();
    }

    private RouteStep findCurrentRouteStep(Lot lot) {
        if (lot.getCurrentRouteStepId() == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Lot has no current route step");
        }

        RouteStep routeStep = routeStepMapper.selectById(lot.getCurrentRouteStepId());
        boolean validStep = routeStep != null
                && lot.getRouteId().equals(routeStep.getRouteId())
                && routeStep.getRequiredEquipmentGroupId() != null;
        if (!validStep) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Lot current route step is invalid");
        }
        return routeStep;
    }

    /** 返回同一路线中序号大于当前 Step 的第一道工序；为空表示当前是末工序。 */
    private RouteStep findNextRouteStep(Lot lot, RouteStep currentRouteStep) {
        return routeStepMapper.selectOne(
                Wrappers.<RouteStep>lambdaQuery()
                        .eq(RouteStep::getRouteId, lot.getRouteId())
                        .gt(RouteStep::getSequenceNo, currentRouteStep.getSequenceNo())
                        .orderByAsc(RouteStep::getSequenceNo)
                        .last("LIMIT 1"));
    }

    private void validateEquipmentForTrackIn(
            Equipment equipment,
            RouteStep routeStep) {
        boolean available = EQUIPMENT_UP.equals(equipment.getUpDownStatus())
                && EQUIPMENT_IDLE.equals(equipment.getPrimaryStatus());
        if (!available) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Equipment must be U and IDLE");
        }

        int membershipCount = equipmentMapper.countGroupMembership(
                routeStep.getRequiredEquipmentGroupId(),
                equipment.getId());
        if (membershipCount != 1) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_CAPABILITY_MISMATCH,
                    "Equipment does not match the current route step");
        }
    }

    private void validateEquipmentForTrackOut(Equipment equipment) {
        boolean processing = EQUIPMENT_UP.equals(equipment.getUpDownStatus())
                && EQUIPMENT_PROCESSING.equals(equipment.getPrimaryStatus());
        if (!processing) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Track Out equipment must be U and PROC");
        }
    }

    private void validateEquipmentNotOccupied(Equipment equipment) {
        Long occupiedCount = lotMapper.selectCount(
                Wrappers.<Lot>lambdaQuery()
                        .eq(Lot::getCurrentEquipmentId, equipment.getId()));
        if (occupiedCount > 0) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_OCCUPIED,
                    "Equipment is occupied by another Lot");
        }
    }

    private void updateReleasedLot(
            Lot lot,
            ReleaseLotRequestTO request,
            Long firstRouteStepId,
            long nextVersion,
            LocalDateTime occurredAt) {
        int affectedRows = lotMapper.update(
                null,
                Wrappers.<Lot>lambdaUpdate()
                        .eq(Lot::getId, lot.getId())
                        .eq(Lot::getVersion, lot.getVersion())
                        .set(Lot::getExecutionStatus, LotExecutionStatus.READY.databaseValue())
                        .set(Lot::getCurrentRouteStepId, firstRouteStepId)
                        .set(Lot::getLastTransactionCode, LotTransactionType.RELEASE.databaseValue())
                        .set(Lot::getLastTransactionAt, occurredAt)
                        .set(Lot::getLastOperatorId, request.getOperatorId())
                        .set(Lot::getVersion, nextVersion));

        assertLotUpdated(affectedRows);
    }

    private void updateEquipmentForTrackIn(
            Equipment equipment,
            long nextVersion,
            LocalDateTime occurredAt) {
        int affectedRows = equipmentMapper.update(
                null,
                Wrappers.<Equipment>lambdaUpdate()
                        .eq(Equipment::getId, equipment.getId())
                        .eq(Equipment::getVersion, equipment.getVersion())
                        .eq(Equipment::getUpDownStatus, EQUIPMENT_UP)
                        .eq(Equipment::getPrimaryStatus, EQUIPMENT_IDLE)
                        .set(Equipment::getStatus, "RUN")
                        .set(Equipment::getPrimaryStatus, EQUIPMENT_PROCESSING)
                        .set(Equipment::getLastEventCode, LotTransactionType.TRACK_IN.databaseValue())
                        .set(Equipment::getLastEventAt, occurredAt)
                        .set(Equipment::getVersion, nextVersion));

        if (affectedRows != 1) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Equipment was changed or occupied by another request");
        }
    }

    private void updateEquipmentForTrackOut(
            Equipment equipment,
            long nextVersion,
            LocalDateTime occurredAt) {
        int affectedRows = equipmentMapper.update(
                null,
                Wrappers.<Equipment>lambdaUpdate()
                        .eq(Equipment::getId, equipment.getId())
                        .eq(Equipment::getVersion, equipment.getVersion())
                        .eq(Equipment::getUpDownStatus, EQUIPMENT_UP)
                        .eq(Equipment::getPrimaryStatus, EQUIPMENT_PROCESSING)
                        .set(Equipment::getStatus, EQUIPMENT_IDLE)
                        .set(Equipment::getPrimaryStatus, EQUIPMENT_IDLE)
                        .set(Equipment::getLastEventCode, LotTransactionType.TRACK_OUT.databaseValue())
                        .set(Equipment::getLastEventAt, occurredAt)
                        .set(Equipment::getVersion, nextVersion));

        if (affectedRows != 1) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Equipment was changed by another request");
        }
    }

    private void updateLotForTrackIn(
            Lot lot,
            Equipment equipment,
            TrackInLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        int affectedRows = lotMapper.update(
                null,
                Wrappers.<Lot>lambdaUpdate()
                        .eq(Lot::getId, lot.getId())
                        .eq(Lot::getVersion, lot.getVersion())
                        .isNull(Lot::getCurrentEquipmentId)
                        .set(Lot::getCurrentEquipmentId, equipment.getId())
                        .set(Lot::getExecutionStatus, LotExecutionStatus.RUNNING.databaseValue())
                        .set(Lot::getLastTransactionCode, LotTransactionType.TRACK_IN.databaseValue())
                        .set(Lot::getLastTransactionAt, occurredAt)
                        .set(Lot::getLastOperatorId, request.getOperatorId())
                        .set(Lot::getVersion, nextVersion));

        assertLotUpdated(affectedRows);
    }

    private void updateLotForTrackOut(
            Lot lot,
            Equipment equipment,
            RouteStep nextRouteStep,
            TrackOutLotRequestTO request,
            String targetExecutionStatus,
            long nextVersion,
            LocalDateTime occurredAt,
            boolean completed) {
        Long targetRouteStepId = completed
                ? lot.getCurrentRouteStepId()
                : nextRouteStep.getId();
        int affectedRows = lotMapper.update(
                null,
                Wrappers.<Lot>lambdaUpdate()
                        .eq(Lot::getId, lot.getId())
                        .eq(Lot::getVersion, lot.getVersion())
                        .eq(Lot::getCurrentRouteStepId, lot.getCurrentRouteStepId())
                        .eq(Lot::getCurrentEquipmentId, equipment.getId())
                        .eq(Lot::getExecutionStatus, LotExecutionStatus.RUNNING.databaseValue())
                        .eq(Lot::getHoldStatus, LotHoldStatus.RELEASED.databaseValue())
                        .set(Lot::getCurrentRouteStepId, targetRouteStepId)
                        .set(Lot::getCurrentEquipmentId, null)
                        .set(Lot::getExecutionStatus, targetExecutionStatus)
                        .set(Lot::getLastTransactionCode, LotTransactionType.TRACK_OUT.databaseValue())
                        .set(Lot::getLastTransactionAt, occurredAt)
                        .set(Lot::getLastOperatorId, request.getOperatorId())
                        .set(completed, Lot::getCompletedAt, occurredAt)
                        .set(Lot::getVersion, nextVersion));

        assertLotUpdated(affectedRows);
    }

    private void assertLotUpdated(int affectedRows) {
        if (affectedRows != 1) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_VERSION_CONFLICT,
                    "Lot was changed by another request");
        }
    }

    /** 履历只允许新增，完整记录 Release 前后状态和版本。 */
    private void appendReleaseTransaction(
            Lot lot,
            ReleaseLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransaction transaction = new LotTransaction();
        transaction.setLotId(lot.getId());
        transaction.setTransactionType(LotTransactionType.RELEASE.databaseValue());
        transaction.setExecutionStatusBefore(LotExecutionStatus.CREATED.databaseValue());
        transaction.setExecutionStatusAfter(LotExecutionStatus.READY.databaseValue());
        transaction.setHoldStatusBefore(LotHoldStatus.RELEASED.databaseValue());
        transaction.setHoldStatusAfter(LotHoldStatus.RELEASED.databaseValue());
        transaction.setOperatorType(OperatorType.USER.databaseValue());
        transaction.setOperatorId(request.getOperatorId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setLotVersionBefore(lot.getVersion());
        transaction.setLotVersionAfter(nextVersion);
        transaction.setOccurredAt(occurredAt);
        lotTransactionMapper.insert(transaction);
    }

    private void appendTrackInTransaction(
            Lot lot,
            RouteStep routeStep,
            Equipment equipment,
            TrackInLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransaction transaction = new LotTransaction();
        transaction.setLotId(lot.getId());
        transaction.setTransactionType(LotTransactionType.TRACK_IN.databaseValue());
        transaction.setRouteStepId(routeStep.getId());
        transaction.setOperationId(routeStep.getOperationId());
        transaction.setEquipmentId(equipment.getId());
        transaction.setExecutionStatusBefore(LotExecutionStatus.READY.databaseValue());
        transaction.setExecutionStatusAfter(LotExecutionStatus.RUNNING.databaseValue());
        transaction.setHoldStatusBefore(LotHoldStatus.RELEASED.databaseValue());
        transaction.setHoldStatusAfter(LotHoldStatus.RELEASED.databaseValue());
        transaction.setOperatorType(OperatorType.USER.databaseValue());
        transaction.setOperatorId(request.getOperatorId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setLotVersionBefore(lot.getVersion());
        transaction.setLotVersionAfter(nextVersion);
        transaction.setOccurredAt(occurredAt);
        lotTransactionMapper.insert(transaction);
    }

    private void appendTrackOutTransaction(
            Lot lot,
            RouteStep currentRouteStep,
            Equipment equipment,
            TrackOutLotRequestTO request,
            String targetExecutionStatus,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransaction transaction = new LotTransaction();
        transaction.setLotId(lot.getId());
        transaction.setTransactionType(LotTransactionType.TRACK_OUT.databaseValue());
        transaction.setRouteStepId(currentRouteStep.getId());
        transaction.setOperationId(currentRouteStep.getOperationId());
        transaction.setEquipmentId(equipment.getId());
        transaction.setExecutionStatusBefore(LotExecutionStatus.RUNNING.databaseValue());
        transaction.setExecutionStatusAfter(targetExecutionStatus);
        transaction.setHoldStatusBefore(LotHoldStatus.RELEASED.databaseValue());
        transaction.setHoldStatusAfter(LotHoldStatus.RELEASED.databaseValue());
        transaction.setOperatorType(OperatorType.USER.databaseValue());
        transaction.setOperatorId(request.getOperatorId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setLotVersionBefore(lot.getVersion());
        transaction.setLotVersionAfter(nextVersion);
        transaction.setOccurredAt(occurredAt);
        lotTransactionMapper.insert(transaction);
    }

    private void appendTrackOutEquipmentHistory(
            Equipment equipment,
            TrackOutLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        EquipmentHistory history = new EquipmentHistory();
        history.setEquipmentId(equipment.getId());
        history.setEventCode(LotTransactionType.TRACK_OUT.databaseValue());
        history.setUpDownStatusBefore(EQUIPMENT_UP);
        history.setUpDownStatusAfter(EQUIPMENT_UP);
        history.setPrimaryStatusBefore(EQUIPMENT_PROCESSING);
        history.setPrimaryStatusAfter(EQUIPMENT_IDLE);
        history.setOperatorType(OperatorType.USER.databaseValue());
        history.setOperatorId(request.getOperatorId());
        history.setOperatorRole("MANUFACTURING");
        history.setIdempotencyKey(request.getIdempotencyKey());
        history.setEquipmentVersionBefore(equipment.getVersion());
        history.setEquipmentVersionAfter(nextVersion);
        history.setOccurredAt(occurredAt);
        equipmentHistoryMapper.insert(history);
    }

    private void appendTrackInEquipmentHistory(
            Equipment equipment,
            TrackInLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        EquipmentHistory history = new EquipmentHistory();
        history.setEquipmentId(equipment.getId());
        history.setEventCode(LotTransactionType.TRACK_IN.databaseValue());
        history.setUpDownStatusBefore(EQUIPMENT_UP);
        history.setUpDownStatusAfter(EQUIPMENT_UP);
        history.setPrimaryStatusBefore(EQUIPMENT_IDLE);
        history.setPrimaryStatusAfter(EQUIPMENT_PROCESSING);
        history.setOperatorType(OperatorType.USER.databaseValue());
        history.setOperatorId(request.getOperatorId());
        history.setOperatorRole("MANUFACTURING");
        history.setIdempotencyKey(request.getIdempotencyKey());
        history.setEquipmentVersionBefore(equipment.getVersion());
        history.setEquipmentVersionAfter(nextVersion);
        history.setOccurredAt(occurredAt);
        equipmentHistoryMapper.insert(history);
    }
}