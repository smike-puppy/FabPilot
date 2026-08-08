package com.fabpilot.mescore.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseHoldLotRequestTO;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.exception.LotCommandErrorCode;
import com.fabpilot.mescore.lot.exception.LotCommandException;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.lot.model.LotTransaction;
import com.fabpilot.mescore.lot.service.impl.LotCommandServiceImpl;
import com.fabpilot.mescore.lot.service.support.LotCommandSupport;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.process.model.RouteStep;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotReleaseHoldServiceImplTest {
    @Mock private LotMapper lotMapper;
    @Mock private LotTransactionMapper lotTransactionMapper;
    @Mock private RouteStepMapper routeStepMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentHistoryMapper equipmentHistoryMapper;
    @Mock private LotCommandSupport lotCommandSupport;
    @InjectMocks private LotCommandServiceImpl service;
    private Lot lot;
    private RouteStep routeStep;
    private ReleaseHoldLotRequestTO request;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "release-hold-test");
        TableInfoHelper.initTableInfo(assistant, Lot.class);
        TableInfoHelper.initTableInfo(assistant, Equipment.class);
    }

    @BeforeEach
    void setUp() {
        lot = mock(Lot.class);
        routeStep = mock(RouteStep.class);
        request = new ReleaseHoldLotRequestTO(3L, "POSTMAN-LOT-016-RELEASE-HOLD-001",
                "POSTMAN-USER", "QUALITY_CHECK_RESOLVED", "Quality data review completed");
    }

    @Test
    void releaseHoldKeepsExecutionStatusAndAppendsReasonedHistory() {
        stubValidReadyHeldLot();
        LotCommandResultTO expected = new LotCommandResultTO(
                "LOT-016", "RELEASE_HOLD", "READY", "RELEASED", 4L, false);
        when(lotCommandSupport.buildResult(lot, LotTransactionType.RELEASE_HOLD,
                "READY", "RELEASED", 4L, false)).thenReturn(expected);

        assertThat(service.releaseHold("LOT-016", request)).isSameAs(expected);

        verify(lotMapper).update(any(), any());
        ArgumentCaptor<LotTransaction> captor = ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(captor.capture());
        LotTransaction transaction = captor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("RELEASE_HOLD");
        assertThat(transaction.getExecutionStatusBefore()).isEqualTo("READY");
        assertThat(transaction.getExecutionStatusAfter()).isEqualTo("READY");
        assertThat(transaction.getHoldStatusBefore()).isEqualTo("HELD");
        assertThat(transaction.getHoldStatusAfter()).isEqualTo("RELEASED");
        assertThat(transaction.getReasonCode()).isEqualTo("QUALITY_CHECK_RESOLVED");
        assertThat(transaction.getReasonText()).isEqualTo("Quality data review completed");
        assertThat(transaction.getRouteStepId()).isEqualTo(30L);
        assertThat(transaction.getEquipmentId()).isNull();
        assertThat(transaction.getLotVersionBefore()).isEqualTo(3L);
        assertThat(transaction.getLotVersionAfter()).isEqualTo(4L);
        verifyNoInteractions(equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void releaseHoldRunningLotPreservesCurrentEquipmentInHistory() {
        stubValidReadyHeldLot();
        when(lot.getExecutionStatus()).thenReturn("RUNNING");
        when(lot.getCurrentEquipmentId()).thenReturn(103L);
        when(lotCommandSupport.buildResult(lot, LotTransactionType.RELEASE_HOLD,
                "RUNNING", "RELEASED", 4L, false)).thenReturn(new LotCommandResultTO(
                        "LOT-016", "RELEASE_HOLD", "RUNNING", "RELEASED", 4L, false));

        service.releaseHold("LOT-016", request);

        ArgumentCaptor<LotTransaction> captor = ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getExecutionStatusAfter()).isEqualTo("RUNNING");
        assertThat(captor.getValue().getEquipmentId()).isEqualTo(103L);
        verifyNoInteractions(equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void releaseHoldReturnsIdempotentResultWithoutWritingAgain() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        LotCommandResultTO repeated = new LotCommandResultTO(
                "LOT-016", "RELEASE_HOLD", "READY", "RELEASED", 4L, true);
        when(lotCommandSupport.findIdempotentResultByReason(lot, request,
                LotTransactionType.RELEASE_HOLD, request.getReasonCode(), request.getReasonText()))
                .thenReturn(repeated);

        assertThat(service.releaseHold("LOT-016", request)).isSameAs(repeated);
        verifyNoInteractions(lotMapper, lotTransactionMapper, routeStepMapper,
                equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void releaseHoldRejectsAlreadyReleasedLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("RELEASED");
        assertThatThrownBy(() -> service.releaseHold("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));
        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void releaseHoldRejectsCompletedLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("COMPLETED");
        assertThatThrownBy(() -> service.releaseHold("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));
        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void releaseHoldRejectsConcurrentLotChangeWithoutAppendingHistory() {
        stubValidReadyHeldLot();
        when(lotMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.releaseHold("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotCommandErrorCode.LOT_VERSION_CONFLICT));
        verifyNoInteractions(lotTransactionMapper, equipmentMapper, equipmentHistoryMapper);
    }

    private void stubValidReadyHeldLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        lenient().when(lot.getId()).thenReturn(16L);
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("HELD");
        when(lot.getCurrentRouteStepId()).thenReturn(30L);
        lenient().when(lot.getCurrentEquipmentId()).thenReturn(null);
        lenient().when(lot.getVersion()).thenReturn(3L);
        when(lot.getRouteId()).thenReturn(1L);
        when(routeStepMapper.selectById(30L)).thenReturn(routeStep);
        lenient().when(routeStep.getId()).thenReturn(30L);
        when(routeStep.getRouteId()).thenReturn(1L);
        lenient().when(routeStep.getOperationId()).thenReturn(3L);
        when(routeStep.getRequiredEquipmentGroupId()).thenReturn(13L);
        when(lotCommandSupport.nextVersion(lot)).thenReturn(4L);
        lenient().when(lotMapper.update(any(), any())).thenReturn(1);
    }
}