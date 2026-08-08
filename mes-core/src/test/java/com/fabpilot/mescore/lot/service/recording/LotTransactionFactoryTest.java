package com.fabpilot.mescore.lot.service.recording;

import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.lot;
import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.routeStep;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabpilot.mescore.common.enums.OperatorType;
import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.enums.LotExecutionStatus;
import com.fabpilot.mescore.lot.enums.LotHoldStatus;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.model.LotTransaction;
import com.fabpilot.mescore.process.model.RouteStep;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LotTransactionFactoryTest {

    @Test
    void shouldFillCommonAuditStateAndVersionFields() {
        Lot source = lot("LOT-001", LotExecutionStatus.READY.databaseValue(),
                LotHoldStatus.RELEASED.databaseValue(), 30L, 103L, 2L);
        RouteStep step = routeStep(30L, 3L);
        HoldLotRequestTO request = new HoldLotRequestTO(
                2L, "hold-idem-001", "operator-01", "QUALITY", "等待质量确认");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 8, 10, 30);

        LotTransaction transaction = LotTransactionFactory.create(
                LotTransactionRecordTO.builder()
                        .lot(source)
                        .transactionType(LotTransactionType.HOLD)
                        .routeStep(step)
                        .equipmentId(103L)
                        .executionStatusAfter(LotExecutionStatus.READY.databaseValue())
                        .holdStatusAfter(LotHoldStatus.HELD.databaseValue())
                        .reasonCode(request.getReasonCode())
                        .reasonText(request.getReasonText())
                        .request(request)
                        .nextVersion(3L)
                        .occurredAt(occurredAt)
                        .build());

        assertThat(transaction.getLotId()).isEqualTo(16L);
        assertThat(transaction.getTransactionType()).isEqualTo("HOLD");
        assertThat(transaction.getRouteStepId()).isEqualTo(30L);
        assertThat(transaction.getOperationId()).isEqualTo(3L);
        assertThat(transaction.getEquipmentId()).isEqualTo(103L);
        assertThat(transaction.getExecutionStatusBefore()).isEqualTo("READY");
        assertThat(transaction.getExecutionStatusAfter()).isEqualTo("READY");
        assertThat(transaction.getHoldStatusBefore()).isEqualTo("RELEASED");
        assertThat(transaction.getHoldStatusAfter()).isEqualTo("HELD");
        assertThat(transaction.getOperatorType()).isEqualTo(OperatorType.USER.databaseValue());
        assertThat(transaction.getOperatorId()).isEqualTo("operator-01");
        assertThat(transaction.getReasonCode()).isEqualTo("QUALITY");
        assertThat(transaction.getReasonText()).isEqualTo("等待质量确认");
        assertThat(transaction.getIdempotencyKey()).isEqualTo("hold-idem-001");
        assertThat(transaction.getLotVersionBefore()).isEqualTo(2L);
        assertThat(transaction.getLotVersionAfter()).isEqualTo(3L);
        assertThat(transaction.getOccurredAt()).isEqualTo(occurredAt);
    }
}
