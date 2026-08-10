package com.fabpilot.mescore.lot.service.policy;

import com.fabpilot.mescore.lot.enums.LotExecutionStatus;
import com.fabpilot.mescore.lot.enums.LotHoldStatus;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.model.Lot;

/**
 * Lot 状态机的命名业务规则。
 *
 * <p>正式命令通过 assert 方法拒绝非法状态，Validator 通过同名 boolean 方法展示每条规则，
 * 从而保证执行与预检查只有一套业务依据。</p>
 */
public final class LotStatePolicy {
    private LotStatePolicy() {
    }

    public static boolean isCreatedForRelease(Lot lot) {
        return isExecution(lot, LotExecutionStatus.CREATED);
    }

    public static boolean isReleasedForRelease(Lot lot) {
        return isHold(lot, LotHoldStatus.RELEASED);
    }

    public static void assertCanRelease(Lot lot) {
        assertAllowed(isCreatedForRelease(lot) && isReleasedForRelease(lot),
                "Only CREATED and RELEASED Lot can be released");
    }

    public static boolean isReadyForTrackIn(Lot lot) {
        return isExecution(lot, LotExecutionStatus.READY);
    }

    public static boolean isReleasedForTrackIn(Lot lot) {
        return isHold(lot, LotHoldStatus.RELEASED);
    }

    public static boolean hasNoEquipmentForTrackIn(Lot lot) {
        return lot.getCurrentEquipmentId() == null;
    }

    public static void assertCanTrackIn(Lot lot) {
        assertAllowed(isReadyForTrackIn(lot)
                        && isReleasedForTrackIn(lot)
                        && hasNoEquipmentForTrackIn(lot),
                "Only READY and RELEASED Lot without equipment can track in");
    }

    public static boolean isRunningForTrackOut(Lot lot) {
        return isExecution(lot, LotExecutionStatus.RUNNING);
    }

    public static boolean isReleasedForTrackOut(Lot lot) {
        return isHold(lot, LotHoldStatus.RELEASED);
    }

    public static boolean hasStepForTrackOut(Lot lot) {
        return lot.getCurrentRouteStepId() != null;
    }

    public static boolean hasEquipmentForTrackOut(Lot lot) {
        return lot.getCurrentEquipmentId() != null;
    }

    public static void assertCanTrackOut(Lot lot) {
        assertAllowed(isRunningForTrackOut(lot)
                        && isReleasedForTrackOut(lot)
                        && hasStepForTrackOut(lot)
                        && hasEquipmentForTrackOut(lot),
                "Only RUNNING and RELEASED Lot with equipment can track out");
    }

    public static boolean isActiveForHold(Lot lot) {
        return isActiveExecution(lot);
    }

    public static boolean isReleasedForHold(Lot lot) {
        return isHold(lot, LotHoldStatus.RELEASED);
    }

    public static boolean hasStepForHold(Lot lot) {
        return lot.getCurrentRouteStepId() != null;
    }

    public static void assertCanHold(Lot lot) {
        assertAllowed(isActiveForHold(lot) && isReleasedForHold(lot) && hasStepForHold(lot),
                "Only READY or RUNNING and RELEASED Lot can be held");
    }

    public static boolean isActiveForReleaseHold(Lot lot) {
        return isActiveExecution(lot);
    }

    public static boolean isHeldForReleaseHold(Lot lot) {
        return isHold(lot, LotHoldStatus.HELD);
    }

    public static boolean hasStepForReleaseHold(Lot lot) {
        return lot.getCurrentRouteStepId() != null;
    }

    public static void assertCanReleaseHold(Lot lot) {
        assertAllowed(isActiveForReleaseHold(lot)
                        && isHeldForReleaseHold(lot)
                        && hasStepForReleaseHold(lot),
                "Only READY or RUNNING and HELD Lot can release hold");
    }

    public static boolean isNotTerminalForScrap(Lot lot) {
        return !isExecution(lot, LotExecutionStatus.COMPLETED)
                && !isExecution(lot, LotExecutionStatus.SCRAPPED);
    }

    public static void assertCanScrap(Lot lot) {
        assertAllowed(isNotTerminalForScrap(lot),
                "Completed or scrapped Lot cannot be scrapped");
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