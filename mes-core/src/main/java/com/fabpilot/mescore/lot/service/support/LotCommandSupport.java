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

/** 所有 Lot 写操作共享的查询、幂等和乐观锁支持。 */
@Component
public class LotCommandSupport {

    @Autowired
    private LotMapper lotMapper;

    @Autowired
    private LotTransactionMapper lotTransactionMapper;

    @Autowired
    private CommandExecutionSupport commandExecutionSupport;

    /** 按业务编码读取 Lot；不存在时统一返回稳定业务错误。 */
    public Lot findLot(String lotCode) {
        Lot lot = lotMapper.selectOne(
                Wrappers.<Lot>lambdaQuery().eq(Lot::getCode, lotCode));
        if (lot == null) {
            throw new LotCommandException(
                    LotCommandErrorCode.LOT_NOT_FOUND,
                    "Lot not found: " + lotCode);
        }
        return lot;
    }

    /**
     * 查找相同幂等键对应的已完成命令。
     *
     * <p>相同 Lot、相同操作视为安全重放；键被其他命令占用则拒绝执行。</p>
     */
    public LotCommandResultTO findIdempotentResult(
            Lot lot,
            VersionedCommandRequestTO request,
            LotTransactionType transactionType) {
        return findIdempotentResult(lot, request, transactionType, null);
    }

    /** Track In 额外比较设备，防止同一幂等键被不同请求参数复用。 */
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

        boolean sameCommand = previous.getLotId().equals(lot.getId())
                && transactionType.databaseValue().equals(previous.getTransactionType())
                && (expectedEquipmentId == null
                        || expectedEquipmentId.equals(previous.getEquipmentId()));
        if (!sameCommand) {
            throw new LotCommandException(
                    LotCommandErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used by another command");
        }

        return buildResult(
                lot,
                transactionType,
                lot.getExecutionStatus(),
                lot.getHoldStatus(),
                lot.getVersion(),
                true);
    }

    /** 委托公共命令组件执行统一的 expectedVersion 校验。 */
    public void validateExpectedVersion(Lot lot, Long expectedVersion) {
        commandExecutionSupport.validateExpectedVersion(
                expectedVersion,
                lot.getVersion(),
                () -> new LotCommandException(
                        LotCommandErrorCode.LOT_VERSION_CONFLICT,
                        "Lot version is stale"));
    }

    /** 返回 Lot 下一个乐观锁版本。 */
    public long nextVersion(Lot lot) {
        return commandExecutionSupport.nextVersion(lot.getVersion());
    }

    /** 统一组装所有 Lot 写命令的响应摘要。 */
    public LotCommandResultTO buildResult(
            Lot lot,
            LotTransactionType transactionType,
            String executionStatus,
            String holdStatus,
            Long version,
            boolean idempotent) {
        return new LotCommandResultTO(
                lot.getCode(),
                transactionType.databaseValue(),
                executionStatus,
                holdStatus,
                version,
                idempotent);
    }
}