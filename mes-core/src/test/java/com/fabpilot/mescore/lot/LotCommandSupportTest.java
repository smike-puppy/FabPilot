package com.fabpilot.mescore.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabpilot.mescore.common.command.CommandExecutionSupport;
import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.model.LotTransaction;
import com.fabpilot.mescore.lot.service.support.LotCommandSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotCommandSupportTest {

    @Mock
    private LotMapper lotMapper;

    @Mock
    private LotTransactionMapper lotTransactionMapper;

    @Mock
    private CommandExecutionSupport commandExecutionSupport;

    @InjectMocks
    private LotCommandSupport support;

    @Test
    void reasonedIdempotencyReplayReturnsCurrentLotState() {
        Lot lot = mock(Lot.class);
        LotTransaction previous = mock(LotTransaction.class);
        HoldLotRequestTO request = request();
        when(lot.getId()).thenReturn(16L);
        when(lot.getCode()).thenReturn("LOT-016");
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("HELD");
        when(lot.getVersion()).thenReturn(3L);
        when(previous.getLotId()).thenReturn(16L);
        when(previous.getTransactionType()).thenReturn("HOLD");
        when(previous.getReasonCode()).thenReturn("QUALITY_CHECK");
        when(previous.getReasonText()).thenReturn("检测工序前需要复核质量数据");
        when(lotTransactionMapper.selectOne(any())).thenReturn(previous);

        LotCommandResultTO result = support.findIdempotentResultByReason(
                lot,
                request,
                LotTransactionType.HOLD,
                request.getReasonCode(),
                request.getReasonText());

        assertThat(result.getHoldStatus()).isEqualTo("HELD");
        assertThat(result.getVersion()).isEqualTo(3L);
        assertThat(result.isIdempotent()).isTrue();
    }

    @Test
    void reasonedIdempotencyRejectsDifferentReason() {
        Lot lot = mock(Lot.class);
        LotTransaction previous = mock(LotTransaction.class);
        HoldLotRequestTO request = request();
        when(lot.getId()).thenReturn(16L);
        when(previous.getLotId()).thenReturn(16L);
        when(previous.getTransactionType()).thenReturn("HOLD");
        when(previous.getReasonCode()).thenReturn("OTHER_REASON");
        when(lotTransactionMapper.selectOne(any())).thenReturn(previous);

        assertThatThrownBy(() -> support.findIdempotentResultByReason(
                lot,
                request,
                LotTransactionType.HOLD,
                request.getReasonCode(),
                request.getReasonText()))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.IDEMPOTENCY_CONFLICT));
    }

    private HoldLotRequestTO request() {
        return new HoldLotRequestTO(
                2L,
                "POSTMAN-LOT-016-HOLD-001",
                "POSTMAN-USER",
                "QUALITY_CHECK",
                "检测工序前需要复核质量数据");
    }
}