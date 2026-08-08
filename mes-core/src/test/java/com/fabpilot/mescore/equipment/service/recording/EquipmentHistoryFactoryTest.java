package com.fabpilot.mescore.equipment.service.recording;

import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.equipment;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabpilot.mescore.common.enums.OperatorType;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EquipmentHistoryFactoryTest {

    @Test
    void shouldFillCommonAuditStateAndVersionFields() {
        Equipment source = equipment(103L, "U", "PROC", 7L);
        TrackOutLotRequestTO request = new TrackOutLotRequestTO(
                1L, "track-out-idem-001", "operator-01");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 8, 11, 0);

        EquipmentHistory history = EquipmentHistoryFactory.create(
                EquipmentHistoryRecordTO.builder()
                        .equipment(source)
                        .eventCode(LotTransactionType.TRACK_OUT.databaseValue())
                        .primaryStatusAfter("IDLE")
                        .request(request)
                        .nextVersion(8L)
                        .occurredAt(occurredAt)
                        .build());

        assertThat(history.getEquipmentId()).isEqualTo(103L);
        assertThat(history.getEventCode()).isEqualTo("TRACK_OUT");
        assertThat(history.getUpDownStatusBefore()).isEqualTo("U");
        assertThat(history.getUpDownStatusAfter()).isEqualTo("U");
        assertThat(history.getPrimaryStatusBefore()).isEqualTo("PROC");
        assertThat(history.getPrimaryStatusAfter()).isEqualTo("IDLE");
        assertThat(history.getOperatorType()).isEqualTo(OperatorType.USER.databaseValue());
        assertThat(history.getOperatorId()).isEqualTo("operator-01");
        assertThat(history.getOperatorRole()).isEqualTo("MANUFACTURING");
        assertThat(history.getIdempotencyKey()).isEqualTo("track-out-idem-001");
        assertThat(history.getEquipmentVersionBefore()).isEqualTo(7L);
        assertThat(history.getEquipmentVersionAfter()).isEqualTo(8L);
        assertThat(history.getOccurredAt()).isEqualTo(occurredAt);
    }
}
