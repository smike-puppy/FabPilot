package com.fabpilot.mescore.lot.service.policy;

import com.fabpilot.mescore.lot.enums.LotExecutionStatus;
import com.fabpilot.mescore.lot.enums.LotHoldStatus;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.model.Lot;

/**
 * Lot 状态机的命名业务规则。
 * 每个方法对应一个业务动作，避免在 Service 中重复拼接状态条件，同时保留清晰的规则含义。
 */
public final class LotStatePolicy {
    private LotStatePolicy() {
    }

    public static void assertCanRelease(Lot lot) {
        boolean allowed = isExecution(lot, LotExecutionStatus.CREATED)
                && isHold(lot, LotHoldStatus.RELEASED);
        assertAllowed(allowed, "Only CREATED and RELEASED Lot can be released");
    }

    public static void assertCanTrackIn(Lot lot) {
        boolean allowed = isExecution(lot, LotExecutionStatus.READY)
                && isHold(lot, LotHoldStatus.RELEASED)
                && lot.getCurrentEquipmentId() == null;
        assertAllowed(allowed, "Only READY and RELEASED Lot without equipment can track in");
    }

    public static void assertCanTrackOut(Lot lot) {
        boolean allowed = isExecution(lot, LotExecutionStatus.RUNNING)
                && isHold(lot, LotHoldStatus.RELEASED)
                && lot.getCurrentRouteStepId() != null
                && lot.getCurrentEquipmentId() != null;
        assertAllowed(allowed, "Only RUNNING and RELEASED Lot with equipment can track out");
    }

    public static void assertCanHold(Lot lot) {
        boolean allowed = isActiveExecution(lot)
                && isHold(lot, LotHoldStatus.RELEASED)
                && lot.getCurrentRouteStepId() != null;
        assertAllowed(allowed, "Only READY or RUNNING and RELEASED Lot can be held");
    }

    public static void assertCanReleaseHold(Lot lot) {
        boolean allowed = isActiveExecution(lot)
                && isHold(lot, LotHoldStatus.HELD)
                && lot.getCurrentRouteStepId() != null;
        assertAllowed(allowed, "Only READY or RUNNING and HELD Lot can release hold");
    }

    public static void assertCanScrap(Lot lot) {
        boolean terminal = isExecution(lot, LotExecutionStatus.COMPLETED)
                || isExecution(lot, LotExecutionStatus.SCRAPPED);
        assertAllowed(!terminal, "Completed or scrapped Lot cannot be scrapped");
    }

    private static boolean isActiveExecution(Lot lot) {
        return isExecution(lot, LotExecutionStatus.READY)
                || isExecution(lot, LotExecutionStatus.RUNNING);
    }

    private static boolean isExecution(Lot lot, LotExecutionStatus status) {
        return status.databaseValue().equals(lot.getExecutionStatus());
    }

    private static boolean isHold(Lot lot, LotHoldStatus status) {
        return status.databaseValue().equals(lot.getHoldStatus());
    }

    private static void assertAllowed(boolean allowed, String message) {
        if (!allowed) {
            throw new LotCommandException(LotCommandErrorCode.LOT_STATE_INVALID, message);
        }
    }
}