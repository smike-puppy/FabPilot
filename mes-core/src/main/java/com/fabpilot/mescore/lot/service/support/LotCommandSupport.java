package com.fabpilot.mescore.lot.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fabpilot.mescore.common.command.CommandExecutionSupport;
import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.model.LotTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 集中提供所有 Lot 写命令共用的 Lot 查询、幂等识别、版本校验和响应组装。 */
@Component
public class LotCommandSupport {
    @Autowired private LotMapper lotMapper;
    @Autowired private LotTransactionMapper lotTransactionMapper;
    @Autowired private CommandExecutionSupport commandExecutionSupport;

    /** 按业务编码读取当前 Lot 快照；不存在时立即终止命令，后续不会产生任何写入。 */
    public Lot findLot(String lotCode) {
        Lot lot = lotMapper.selectOne(
                Wrappers.<Lot>lambdaQuery().eq(Lot::getCode, lotCode));
        if (lot == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_NOT_FOUND, "Lot not found: " + lotCode);
        }
        return lot;
    }

    /** 对没有额外业务参数的命令，幂等身份由 idempotencyKey + Lot + 命令类型共同确定。 */
    public LotCommandResultTO findIdempotentResult(
            Lot lot, VersionedCommandRequestTO request, LotTransactionType transactionType) {
        return findIdempotentResult(lot, request, transactionType, null);
    }

    /**
     * 查询幂等键是否已经产生过结果。
     * 没查到返回 null，调用方继续首次执行；查到且 Lot/命令/目标设备一致则返回当前结果并标记 idempotent=true；
     * 查到但业务参数不同，说明同一个键被用于另一个业务意图，必须报 IDEMPOTENCY_CONFLICT。
     */
    public LotCommandResultTO findIdempotentResult(
            Lot lot,
            VersionedCommandRequestTO request,
            LotTransactionType transactionType,
            Long expectedEquipmentId) {
        LotTransaction previous = lotTransactionMapper.selectOne(
                Wrappers.<LotTransaction>lambdaQuery()
                        .eq(LotTransaction::getIdempotencyKey, request.getIdempotencyKey()));
        if (previous == null) {
            return null;
        }

        // Track In 还要核对目标设备，防止同一键第一次上 ETCH-01、重试时却改成 ETCH-02。
        boolean sameCommand = previous.getLotId().equals(lot.getId())
                && transactionType.databaseValue().equals(previous.getTransactionType())
                && (expectedEquipmentId == null
                        || expectedEquipmentId.equals(previous.getEquipmentId()));
        if (!sameCommand) {
            throw new LotCommandException(
                    LotCommandErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used by another command");
        }
        return buildResult(lot, transactionType, lot.getExecutionStatus(),
                lot.getHoldStatus(), lot.getVersion(), true);
    }

    /**
     * Hold、Release Hold、Scrap 的原因本身就是业务意图的一部分，因此重放时必须逐字核对 reasonCode/reasonText。
     * 这可以防止调用方复用旧键，却悄悄把“质量复核”改成另一种原因而污染审计含义。
     */
    public LotCommandResultTO findIdempotentResultByReason(
            Lot lot,
            VersionedCommandRequestTO request,
            LotTransactionType transactionType,
            String expectedReasonCode,
            String expectedReasonText) {
        LotTransaction previous = lotTransactionMapper.selectOne(
                Wrappers.<LotTransaction>lambdaQuery()
                        .eq(LotTransaction::getIdempotencyKey, request.getIdempotencyKey()));
        if (previous == null) {
            return null;
        }

        boolean sameCommand = previous.getLotId().equals(lot.getId())
                && transactionType.databaseValue().equals(previous.getTransactionType())
                && expectedReasonCode.equals(previous.getReasonCode())
                && expectedReasonText.equals(previous.getReasonText());
        if (!sameCommand) {
            throw new LotCommandException(
                    LotCommandErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used by another command or reason");
        }
        return buildResult(lot, transactionType, lot.getExecutionStatus(),
                lot.getHoldStatus(), lot.getVersion(), true);
    }

    /**
     * 首次执行前比较 expectedVersion 与当前 Lot version。
     * 不相等表示调用方基于旧快照发命令，应重新查询再决定，不能让服务端静默覆盖最新业务状态。
     */
    public void validateExpectedVersion(Lot lot, Long expectedVersion) {
        commandExecutionSupport.validateExpectedVersion(
                expectedVersion,
                lot.getVersion(),
                () -> new LotCommandException(
                        LotCommandErrorCode.LOT_VERSION_CONFLICT, "Lot version is stale"));
    }

    /** 每次成功状态变更把 Lot version 增加 1，成为下一条命令的 expectedVersion。 */
    public long nextVersion(Lot lot) {
        return commandExecutionSupport.nextVersion(lot.getVersion());
    }

    /** 统一返回命令完成后的 Lot 摘要；idempotent 用于区分首次执行和安全重放。 */
    public LotCommandResultTO buildResult(
            Lot lot,
            LotTransactionType transactionType,
            String executionStatus,
            String holdStatus,
            Long version,
            boolean idempotent) {
        return new LotCommandResultTO(lot.getCode(), transactionType.databaseValue(),
                executionStatus, holdStatus, version, idempotent);
    }
}