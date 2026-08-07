package com.fabpilot.mescore.common.command;

import com.fabpilot.mescore.common.error.BusinessException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 所有状态变更命令共享的并发控制算法。 */
@Component
public class CommandExecutionSupport {

    /**
     * 校验调用方读取的版本仍是数据库当前版本。
     *
     * <p>异常由业务模块提供，使公共模块不依赖 Lot、Equipment 等具体错误码。</p>
     */
    public void validateExpectedVersion(
            Long expectedVersion,
            Long currentVersion,
            Supplier<? extends BusinessException> conflictExceptionSupplier) {
        if (!expectedVersion.equals(currentVersion)) {
            throw conflictExceptionSupplier.get();
        }
    }

    /** 统一计算状态快照成功变更后的下一个版本。 */
    public long nextVersion(Long currentVersion) {
        return currentVersion + 1;
    }
}