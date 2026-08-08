package com.fabpilot.mescore.lot.service.policy;

import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.lot;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabpilot.mescore.lot.enums.LotExecutionStatus;
import com.fabpilot.mescore.lot.enums.LotHoldStatus;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import org.junit.jupiter.api.Test;

class LotStatePolicyTest {

    @Test
    void releaseShouldRequireCreatedAndReleased() {
        assertThatCode(() -> LotStatePolicy.assertCanRelease(lot("LOT-001", "CREATED",
                "RELEASED", null, null, 0L))).doesNotThrowAnyException();

        assertThatThrownBy(() -> LotStatePolicy.assertCanRelease(lot("LOT-001", "READY",
                "RELEASED", 30L, null, 1L))).isInstanceOf(LotCommandException.class);
    }

    @Test
    void trackInShouldRequireReadyReleasedAndNoEquipment() {
        assertThatCode(() -> LotStatePolicy.assertCanTrackIn(lot("LOT-001", "READY",
                "RELEASED", 30L, null, 1L))).doesNotThrowAnyException();

        assertThatThrownBy(() -> LotStatePolicy.assertCanTrackIn(lot("LOT-001", "READY",
                "RELEASED", 30L, 103L, 1L))).isInstanceOf(LotCommandException.class);
    }

    @Test
    void trackOutShouldRequireRunningReleasedStepAndEquipment() {
        assertThatCode(() -> LotStatePolicy.assertCanTrackOut(lot("LOT-001", "RUNNING",
                "RELEASED", 30L, 103L, 2L))).doesNotThrowAnyException();

        assertThatThrownBy(() -> LotStatePolicy.assertCanTrackOut(lot("LOT-001", "RUNNING",
                "RELEASED", 30L, null, 2L))).isInstanceOf(LotCommandException.class);
    }

    @Test
    void holdAndReleaseHoldShouldUseOppositeHoldStates() {
        assertThatCode(() -> LotStatePolicy.assertCanHold(lot("LOT-001", "READY",
                "RELEASED", 30L, null, 2L))).doesNotThrowAnyException();
        assertThatCode(() -> LotStatePolicy.assertCanReleaseHold(lot("LOT-001", "READY",
                "HELD", 30L, null, 3L))).doesNotThrowAnyException();

        assertThatThrownBy(() -> LotStatePolicy.assertCanReleaseHold(lot("LOT-001", "READY",
                "RELEASED", 30L, null, 3L))).isInstanceOf(LotCommandException.class);
    }

    @Test
    void scrapShouldRejectTerminalExecutionStates() {
        assertThatCode(() -> LotStatePolicy.assertCanScrap(lot("LOT-001", "RUNNING",
                LotHoldStatus.HELD.databaseValue(), 30L, 103L, 2L)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> LotStatePolicy.assertCanScrap(lot("LOT-001",
                LotExecutionStatus.COMPLETED.databaseValue(), "RELEASED", 30L, null, 4L)))
                .isInstanceOf(LotCommandException.class);
        assertThatThrownBy(() -> LotStatePolicy.assertCanScrap(lot("LOT-001",
                LotExecutionStatus.SCRAPPED.databaseValue(), "RELEASED", 30L, null, 4L)))
                .isInstanceOf(LotCommandException.class);
    }
}
