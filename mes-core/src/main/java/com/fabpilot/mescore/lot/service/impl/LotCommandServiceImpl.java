package com.fabpilot.mescore.lot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.service.recording.EquipmentHistoryFactory;
import com.fabpilot.mescore.equipment.service.recording.EquipmentHistoryRecordTO;
import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseLotRequestTO;
import com.fabpilot.mescore.lot.dto.ReleaseHoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;
import com.fabpilot.mescore.lot.dto.ScrapLotRequestTO;
import com.fabpilot.mescore.lot.enums.LotExecutionStatus;
import com.fabpilot.mescore.lot.enums.LotHoldStatus;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.service.LotCommandService;
import com.fabpilot.mescore.lot.service.policy.LotRoutePolicy;
import com.fabpilot.mescore.lot.service.policy.LotStatePolicy;
import com.fabpilot.mescore.lot.service.policy.TrackInPolicy;
import com.fabpilot.mescore.lot.service.policy.TrackOutPolicy;
import com.fabpilot.mescore.lot.service.recording.LotTransactionFactory;
import com.fabpilot.mescore.lot.service.recording.LotTransactionRecordTO;
import com.fabpilot.mescore.lot.service.support.LotCommandSupport;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.process.model.RouteStep;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lot 写侧状态机实现，集中管理状态校验、并发控制和生产履历�?*/
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
     * 在同一事务内更�?Lot 快照并追�?Release 履历�?     *
     * <p>如果任意一步失败，Spring 会回滚整个事务，避免快照与履历不一致�?/p>
     */
    @Override
    @Transactional
    /**
     * Release 业务流程：查 Lot → 处理幂等重放 → 校验请求版本 → 校验 CREATED + RELEASED →
     * 确定路线首 Step → 条件更新为 READY → 追加 RELEASE 履历。快照与履历由同一事务保证一致。
     */
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
        LotStatePolicy.assertCanRelease(lot);

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
     * Track In 原子更新 Lot �?Equipment 两个快照，并分别追加生产履历�?     *
     * <p>设备先以旧版本和 IDLE 状态参与条件更新；后续 Lot 更新或任一履历插入失败时，
     * 整个事务都会回滚，设备不会被遗留为无 Lot 占用�?PROC 状态�?/p>
     */
    @Override
    @Transactional
    /**
     * Track In 业务流程：读取 Lot/目标设备 → 幂等与版本校验 → 校验 Lot、Step、设备能力和占用 →
     * 设备 IDLE→PROC、Lot READY→RUNNING 并绑定设备 → 分别追加 Lot/Equipment 履历。
     */
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
        LotStatePolicy.assertCanTrackIn(lot);

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
     * Track Out 原子释放设备并推�?Lot；末工序完成时写�?completedAt�?     */
    @Override
    @Transactional
    /**
     * Track Out 业务流程：校验 RUNNING Lot 与当前 PROC 设备 → 判断是否还有下一 Step →
     * 释放设备 → 普通工序进入下一 Step/READY，末工序进入 COMPLETED → 追加双履历。
     */
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
        LotStatePolicy.assertCanTrackOut(lot);

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

    /**
     * Hold 仅切�?Lot 的独立暂停状态；Equipment 状态由设备事件单独维护�?     */
    @Override
    @Transactional
    /**
     * Hold 业务流程：幂等键同时核对暂停原因 → 校验 READY/RUNNING + RELEASED →
     * 只把 hold_status 改为 HELD → 追加带原因的 HOLD 履历；执行阶段和设备都不改变。
     */
    public LotCommandResultTO hold(String lotCode, HoldLotRequestTO request) {
        Lot lot = lotCommandSupport.findLot(lotCode);

        LotCommandResultTO repeatedResult = lotCommandSupport.findIdempotentResultByReason(
                lot,
                request,
                LotTransactionType.HOLD,
                request.getReasonCode(),
                request.getReasonText());
        if (repeatedResult != null) {
            return repeatedResult;
        }

        lotCommandSupport.validateExpectedVersion(lot, request.getExpectedVersion());
        LotStatePolicy.assertCanHold(lot);

        RouteStep currentRouteStep = findCurrentRouteStep(lot);
        long nextVersion = lotCommandSupport.nextVersion(lot);
        LocalDateTime occurredAt = LocalDateTime.now();

        updateLotForHold(lot, request, nextVersion, occurredAt);
        appendHoldTransaction(
                lot,
                currentRouteStep,
                request,
                nextVersion,
                occurredAt);

        return lotCommandSupport.buildResult(
                lot,
                LotTransactionType.HOLD,
                lot.getExecutionStatus(),
                LotHoldStatus.HELD.databaseValue(),
                nextVersion,
                false);
    }

    @Override
    @Transactional
    /**
     * Release Hold 业务流程：幂等键同时核对解除原因 → 校验 READY/RUNNING + HELD →
     * 只把 hold_status 改回 RELEASED → 追加 RELEASE_HOLD 履历；不推进工艺、不操作设备。
     */
    public LotCommandResultTO releaseHold(String lotCode, ReleaseHoldLotRequestTO request) {
        Lot lot = lotCommandSupport.findLot(lotCode);
        LotCommandResultTO repeatedResult = lotCommandSupport.findIdempotentResultByReason(
                lot, request, LotTransactionType.RELEASE_HOLD,
                request.getReasonCode(), request.getReasonText());
        if (repeatedResult != null) {
            return repeatedResult;
        }
        lotCommandSupport.validateExpectedVersion(lot, request.getExpectedVersion());
        LotStatePolicy.assertCanReleaseHold(lot);
        RouteStep currentRouteStep = findCurrentRouteStep(lot);
        long nextVersion = lotCommandSupport.nextVersion(lot);
        LocalDateTime occurredAt = LocalDateTime.now();
        updateLotForReleaseHold(lot, request, nextVersion, occurredAt);
        appendReleaseHoldTransaction(lot, currentRouteStep, request, nextVersion, occurredAt);
        return lotCommandSupport.buildResult(lot, LotTransactionType.RELEASE_HOLD,
                lot.getExecutionStatus(), LotHoldStatus.RELEASED.databaseValue(), nextVersion, false);
    }
    @Override
    @Transactional
    /**
     * Scrap 业务流程：校验非终态 → 保存当前 Step/设备作为审计上下文 → 必要时释放 PROC 设备 →
     * Lot 进入 SCRAPPED + RELEASED 并清除设备绑定 → 追加报废履历。报废不是正常完工，不写 completedAt。
     */
    public LotCommandResultTO scrap(String lotCode, ScrapLotRequestTO request) {
        Lot lot = lotCommandSupport.findLot(lotCode);
        LotCommandResultTO repeatedResult = lotCommandSupport.findIdempotentResultByReason(
                lot, request, LotTransactionType.SCRAP,
                request.getReasonCode(), request.getReasonText());
        if (repeatedResult != null) {
            return repeatedResult;
        }
        lotCommandSupport.validateExpectedVersion(lot, request.getExpectedVersion());
        LotStatePolicy.assertCanScrap(lot);

        RouteStep currentRouteStep = lot.getCurrentRouteStepId() == null
                ? null : findCurrentRouteStep(lot);
        Equipment equipment = lot.getCurrentEquipmentId() == null
                ? null : findEquipment(lot.getCurrentEquipmentId());
        long nextLotVersion = lotCommandSupport.nextVersion(lot);
        LocalDateTime occurredAt = LocalDateTime.now();

        Long nextEquipmentVersion = releaseProcessingEquipmentForScrap(
                equipment, request, occurredAt);
        updateLotForScrap(lot, request, nextLotVersion, occurredAt);
        appendScrapTransaction(lot, currentRouteStep, equipment, request,
                nextLotVersion, occurredAt);
        if (nextEquipmentVersion != null) {
            appendScrapEquipmentHistory(
                    equipment, request, nextEquipmentVersion, occurredAt);
        }
        return lotCommandSupport.buildResult(lot, LotTransactionType.SCRAP,
                LotExecutionStatus.SCRAPPED.databaseValue(),
                LotHoldStatus.RELEASED.databaseValue(), nextLotVersion, false);
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

    /** Lot 尚未指定当前 Step 时，Release 按序号选择路线中的第一道工序�?*/
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

    /**
     * 读取并验证 Lot 当前工艺 Step：Step 必须存在、属于 Lot 当前 Route，并配置所需设备组。
     * 这可以阻止跨路线 Step 或不完整工艺配置进入后续状态迁移和审计履历。
     */
    private RouteStep findCurrentRouteStep(Lot lot) {
        if (lot.getCurrentRouteStepId() == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Lot has no current route step");
        }

        RouteStep routeStep = routeStepMapper.selectById(lot.getCurrentRouteStepId());
        boolean validStep = LotRoutePolicy.isCurrentStepValid(lot, routeStep);
        if (!validStep) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_STATE_INVALID,
                    "Lot current route step is invalid");
        }
        return routeStep;
    }

    /** 返回同一路线中序号大于当�?Step 的第一道工序；为空表示当前是末工序�?*/
    private RouteStep findNextRouteStep(Lot lot, RouteStep currentRouteStep) {
        return routeStepMapper.selectOne(
                Wrappers.<RouteStep>lambdaQuery()
                        .eq(RouteStep::getRouteId, lot.getRouteId())
                        .gt(RouteStep::getSequenceNo, currentRouteStep.getSequenceNo())
                        .orderByAsc(RouteStep::getSequenceNo)
                        .last("LIMIT 1"));
    }

    /**
     * Track In 设备规则分两层：设备当前必须可生产（U + IDLE），并且必须属于当前 Step 要求的能力组。
     * 第一层防止占用停机或加工中设备，第二层防止把 Lot 上到工艺能力不匹配的设备。
     */
    private void validateEquipmentForTrackIn(
            Equipment equipment,
            RouteStep routeStep) {
        TrackInPolicy.assertEquipmentAvailable(equipment);

        int membershipCount = equipmentMapper.countGroupMembership(
                routeStep.getRequiredEquipmentGroupId(),
                equipment.getId());
        TrackInPolicy.assertRequiredCapability(membershipCount);

    }

    /** Track Out 只能释放仍处于 U + PROC 的设备，防止覆盖设备侧已发生的 Down/Maintenance 等独立事件。 */
    private void validateEquipmentForTrackOut(Equipment equipment) {
        TrackOutPolicy.assertEquipmentProcessing(equipment);
    }

    /** 再查 Lot 占用关系，防止设备虽显示 IDLE，但实际上已经被另一个 Lot 绑定。 */
    private void validateEquipmentNotOccupied(Equipment equipment) {
        Long occupiedCount = lotMapper.selectCount(
                Wrappers.<Lot>lambdaQuery()
                        .eq(Lot::getCurrentEquipmentId, equipment.getId()));
        TrackInPolicy.assertNotOccupied(occupiedCount);
    }

    /** 条件更新 CREATED Lot 为 READY，并写入首 Step、最后交易、操作人和新版本；旧版本条件用于阻止并发覆盖。 */
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

    /** 设备上机快照：仅当旧版本且仍为 U + IDLE 时更新为 RUN/PROC；条件不成立说明设备已被并发改变。 */
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

    /** 设备下机快照：仅释放本次读取到的 PROC 版本，更新为 IDLE，避免覆盖设备侧并发事件。 */
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

    /** Scrap 仅把仍处于 PROC 的绑定设备释放为 IDLE；DOWN/MAINTENANCE 等独立异常状态保持不变。 */
    private Long releaseProcessingEquipmentForScrap(
            Equipment equipment,
            ScrapLotRequestTO request,
            LocalDateTime occurredAt) {
        if (equipment == null || !EQUIPMENT_PROCESSING.equals(equipment.getPrimaryStatus())) {
            return null;
        }
        long nextVersion = equipment.getVersion() + 1;
        int affectedRows = equipmentMapper.update(null, Wrappers.<Equipment>lambdaUpdate()
                .eq(Equipment::getId, equipment.getId())
                .eq(Equipment::getVersion, equipment.getVersion())
                .eq(Equipment::getPrimaryStatus, EQUIPMENT_PROCESSING)
                .set(Equipment::getStatus, EQUIPMENT_IDLE)
                .set(Equipment::getPrimaryStatus, EQUIPMENT_IDLE)
                .set(Equipment::getLastEventCode, LotTransactionType.SCRAP.databaseValue())
                .set(Equipment::getLastEventAt, occurredAt)
                .set(Equipment::getVersion, nextVersion));
        if (affectedRows != 1) {
            throw new LotCommandException(
                    LotCommandErrorCode.EQUIPMENT_STATE_INVALID,
                    "Equipment was changed by another request");
        }
        return nextVersion;
    }
    /** Lot 上机快照：确认仍未绑定设备后，将 READY 改为 RUNNING 并建立 Lot→Equipment 当前占用关系。 */
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

    /** Lot 下机快照：校验原 Step/设备/状态后清除设备；有下一 Step 则 READY，末工序则 COMPLETED。 */
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

    /** Hold 快照只切换独立 hold_status；execution_status、Step 和 Equipment 都保留原值。 */
    private void updateLotForHold(
            Lot lot,
            HoldLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        int affectedRows = lotMapper.update(
                null,
                Wrappers.<Lot>lambdaUpdate()
                        .eq(Lot::getId, lot.getId())
                        .eq(Lot::getVersion, lot.getVersion())
                        .eq(Lot::getExecutionStatus, lot.getExecutionStatus())
                        .eq(Lot::getHoldStatus, LotHoldStatus.RELEASED.databaseValue())
                        .set(Lot::getHoldStatus, LotHoldStatus.HELD.databaseValue())
                        .set(Lot::getLastTransactionCode, LotTransactionType.HOLD.databaseValue())
                        .set(Lot::getLastTransactionAt, occurredAt)
                        .set(Lot::getLastOperatorId, request.getOperatorId())
                        .set(Lot::getVersion, nextVersion));

        assertLotUpdated(affectedRows);
    }

    /** Release Hold 快照只执行 HELD→RELEASED，并用旧版本、旧执行状态和 HELD 条件防止并发误解除。 */
    private void updateLotForReleaseHold(Lot lot, ReleaseHoldLotRequestTO request,
            long nextVersion, LocalDateTime occurredAt) {
        int affectedRows = lotMapper.update(null, Wrappers.<Lot>lambdaUpdate()
                .eq(Lot::getId, lot.getId())
                .eq(Lot::getVersion, lot.getVersion())
                .eq(Lot::getExecutionStatus, lot.getExecutionStatus())
                .eq(Lot::getHoldStatus, LotHoldStatus.HELD.databaseValue())
                .set(Lot::getHoldStatus, LotHoldStatus.RELEASED.databaseValue())
                .set(Lot::getLastTransactionCode, LotTransactionType.RELEASE_HOLD.databaseValue())
                .set(Lot::getLastTransactionAt, occurredAt)
                .set(Lot::getLastOperatorId, request.getOperatorId())
                .set(Lot::getVersion, nextVersion));
        assertLotUpdated(affectedRows);
    }
    /** Scrap 快照进入 SCRAPPED + RELEASED、解除设备绑定；保留 Step，且不写 completedAt 以区分正常完工。 */
    private void updateLotForScrap(
            Lot lot,
            ScrapLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        int affectedRows = lotMapper.update(null, Wrappers.<Lot>lambdaUpdate()
                .eq(Lot::getId, lot.getId())
                .eq(Lot::getVersion, lot.getVersion())
                .eq(Lot::getExecutionStatus, lot.getExecutionStatus())
                .eq(Lot::getHoldStatus, lot.getHoldStatus())
                .eq(lot.getCurrentEquipmentId() != null,
                        Lot::getCurrentEquipmentId, lot.getCurrentEquipmentId())
                .isNull(lot.getCurrentEquipmentId() == null, Lot::getCurrentEquipmentId)
                .set(Lot::getCurrentEquipmentId, null)
                .set(Lot::getExecutionStatus, LotExecutionStatus.SCRAPPED.databaseValue())
                .set(Lot::getHoldStatus, LotHoldStatus.RELEASED.databaseValue())
                .set(Lot::getLastTransactionCode, LotTransactionType.SCRAP.databaseValue())
                .set(Lot::getLastTransactionAt, occurredAt)
                .set(Lot::getLastOperatorId, request.getOperatorId())
                .set(Lot::getVersion, nextVersion));
        assertLotUpdated(affectedRows);
    }
    /** 条件更新必须且只能命中一行；0 行表示版本或状态已被其他请求改变，统一转为乐观锁冲突。 */
    private void assertLotUpdated(int affectedRows) {
        if (affectedRows != 1) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_VERSION_CONFLICT,
                    "Lot was changed by another request");
        }
    }

    /** 履历只允许新增，完整记录 Release 前后状态和版本�?*/
    /** 追加 RELEASE 生产履历，固定保存 CREATED→READY 和版本前后值；历史只新增、不回写。 */
    private void appendReleaseTransaction(
            Lot lot,
            ReleaseLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransactionRecordTO record = LotTransactionRecordTO.builder()
                .lot(lot)
                .transactionType(LotTransactionType.RELEASE)
                .executionStatusAfter(LotExecutionStatus.READY.databaseValue())
                .holdStatusAfter(LotHoldStatus.RELEASED.databaseValue())
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        lotTransactionMapper.insert(LotTransactionFactory.create(record));
    }

    /** 追加 TRACK_IN 履历，记录当时的 Step、Operation、Equipment 和 READY→RUNNING。 */
    private void appendTrackInTransaction(
            Lot lot,
            RouteStep routeStep,
            Equipment equipment,
            TrackInLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransactionRecordTO record = LotTransactionRecordTO.builder()
                .lot(lot)
                .transactionType(LotTransactionType.TRACK_IN)
                .routeStep(routeStep)
                .equipmentId(equipment.getId())
                .executionStatusAfter(LotExecutionStatus.RUNNING.databaseValue())
                .holdStatusAfter(LotHoldStatus.RELEASED.databaseValue())
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        lotTransactionMapper.insert(LotTransactionFactory.create(record));
    }

    /** 追加 HOLD 履历：执行状态前后相同，Hold 为 RELEASED→HELD，并原样保存暂停原因。 */
    private void appendHoldTransaction(
            Lot lot,
            RouteStep currentRouteStep,
            HoldLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransactionRecordTO record = LotTransactionRecordTO.builder()
                .lot(lot)
                .transactionType(LotTransactionType.HOLD)
                .routeStep(currentRouteStep)
                .equipmentId(lot.getCurrentEquipmentId())
                .executionStatusAfter(lot.getExecutionStatus())
                .holdStatusAfter(LotHoldStatus.HELD.databaseValue())
                .reasonCode(request.getReasonCode())
                .reasonText(request.getReasonText())
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        lotTransactionMapper.insert(LotTransactionFactory.create(record));
    }

    /** 追加 RELEASE_HOLD 履历：执行状态前后相同，Hold 为 HELD→RELEASED，并保存解除原因。 */
    private void appendReleaseHoldTransaction(
            Lot lot,
            RouteStep currentRouteStep,
            ReleaseHoldLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransactionRecordTO record = LotTransactionRecordTO.builder()
                .lot(lot)
                .transactionType(LotTransactionType.RELEASE_HOLD)
                .routeStep(currentRouteStep)
                .equipmentId(lot.getCurrentEquipmentId())
                .executionStatusAfter(lot.getExecutionStatus())
                .holdStatusAfter(LotHoldStatus.RELEASED.databaseValue())
                .reasonCode(request.getReasonCode())
                .reasonText(request.getReasonText())
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        lotTransactionMapper.insert(LotTransactionFactory.create(record));
    }
    /** 追加 SCRAP 履历：保存报废前执行/Hold 状态以及当时 Step、设备和原因，目标固定为 SCRAPPED + RELEASED。 */
    private void appendScrapTransaction(
            Lot lot,
            RouteStep routeStep,
            Equipment equipment,
            ScrapLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransactionRecordTO record = LotTransactionRecordTO.builder()
                .lot(lot)
                .transactionType(LotTransactionType.SCRAP)
                .routeStep(routeStep)
                .equipmentId(equipment == null ? null : equipment.getId())
                .executionStatusAfter(LotExecutionStatus.SCRAPPED.databaseValue())
                .holdStatusAfter(LotHoldStatus.RELEASED.databaseValue())
                .reasonCode(request.getReasonCode())
                .reasonText(request.getReasonText())
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        lotTransactionMapper.insert(LotTransactionFactory.create(record));
    }

    /** 仅当 Scrap 实际释放 PROC 设备时追加设备履历，记录 PROC→IDLE 和设备版本变化。 */
    private void appendScrapEquipmentHistory(
            Equipment equipment,
            ScrapLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        EquipmentHistoryRecordTO record = EquipmentHistoryRecordTO.builder()
                .equipment(equipment)
                .eventCode(LotTransactionType.SCRAP.databaseValue())
                .primaryStatusAfter(EQUIPMENT_IDLE)
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        equipmentHistoryMapper.insert(EquipmentHistoryFactory.create(record));
    }
    /** 追加 TRACK_OUT 履历；记录离开的当前 Step，而不是推进后的下一 Step，便于还原实际加工位置。 */
    private void appendTrackOutTransaction(
            Lot lot,
            RouteStep currentRouteStep,
            Equipment equipment,
            TrackOutLotRequestTO request,
            String targetExecutionStatus,
            long nextVersion,
            LocalDateTime occurredAt) {
        LotTransactionRecordTO record = LotTransactionRecordTO.builder()
                .lot(lot)
                .transactionType(LotTransactionType.TRACK_OUT)
                .routeStep(currentRouteStep)
                .equipmentId(equipment.getId())
                .executionStatusAfter(targetExecutionStatus)
                .holdStatusAfter(LotHoldStatus.RELEASED.databaseValue())
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        lotTransactionMapper.insert(LotTransactionFactory.create(record));
    }

    /** 追加 Track Out 设备履历，证明该设备由 PROC 正常释放为 IDLE。 */
    private void appendTrackOutEquipmentHistory(
            Equipment equipment,
            TrackOutLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        EquipmentHistoryRecordTO record = EquipmentHistoryRecordTO.builder()
                .equipment(equipment)
                .eventCode(LotTransactionType.TRACK_OUT.databaseValue())
                .primaryStatusAfter(EQUIPMENT_IDLE)
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        equipmentHistoryMapper.insert(EquipmentHistoryFactory.create(record));
    }

    /** 追加 Track In 设备履历，证明该设备由 IDLE 被本次 Lot 占用为 PROC。 */
    private void appendTrackInEquipmentHistory(
            Equipment equipment,
            TrackInLotRequestTO request,
            long nextVersion,
            LocalDateTime occurredAt) {
        EquipmentHistoryRecordTO record = EquipmentHistoryRecordTO.builder()
                .equipment(equipment)
                .eventCode(LotTransactionType.TRACK_IN.databaseValue())
                .primaryStatusAfter(EQUIPMENT_PROCESSING)
                .request(request)
                .nextVersion(nextVersion)
                .occurredAt(occurredAt)
                .build();
        equipmentHistoryMapper.insert(EquipmentHistoryFactory.create(record));
    }
}
