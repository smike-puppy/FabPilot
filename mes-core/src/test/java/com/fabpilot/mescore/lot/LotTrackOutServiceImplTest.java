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
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;
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
class LotTrackOutServiceImplTest {

    @Mock
    private LotMapper lotMapper;

    @Mock
    private LotTransactionMapper lotTransactionMapper;

    @Mock
    private RouteStepMapper routeStepMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private EquipmentHistoryMapper equipmentHistoryMapper;

    @Mock
    private LotCommandSupport lotCommandSupport;

    @InjectMocks
    private LotCommandServiceImpl service;

    private Lot lot;
    private Equipment equipment;
    private RouteStep currentRouteStep;
    private RouteStep nextRouteStep;
    private TrackOutLotRequestTO request;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "track-out-test");
        TableInfoHelper.initTableInfo(assistant, Equipment.class);
        TableInfoHelper.initTableInfo(assistant, Lot.class);
    }

    @BeforeEach
    void setUp() {
        lot = mock(Lot.class);
        equipment = mock(Equipment.class);
        currentRouteStep = mock(RouteStep.class);
        nextRouteStep = mock(RouteStep.class);
        request = new TrackOutLotRequestTO(
                1L,
                "IDEMP-LOT-016-TRACK-OUT-001",
                "OP-001");
    }

    @Test
    void trackOutMovesLotToNextStepAndReleasesEquipment() {
        stubValidTrackOut();
        when(routeStepMapper.selectOne(any())).thenReturn(nextRouteStep);
        when(nextRouteStep.getId()).thenReturn(30L);
        LotCommandResultTO expected = stubResult("READY");

        LotCommandResultTO result = service.trackOut("LOT-016", request);

        assertThat(result).isSameAs(expected);
        verify(equipmentMapper).update(any(), any());
        verify(lotMapper).update(any(), any());

        ArgumentCaptor<LotTransaction> transactionCaptor =
                ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(transactionCaptor.capture());
        LotTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("TRACK_OUT");
        assertThat(transaction.getRouteStepId()).isEqualTo(20L);
        assertThat(transaction.getOperationId()).isEqualTo(2L);
        assertThat(transaction.getEquipmentId()).isEqualTo(102L);
        assertThat(transaction.getExecutionStatusBefore()).isEqualTo("RUNNING");
        assertThat(transaction.getExecutionStatusAfter()).isEqualTo("READY");
        assertThat(transaction.getLotVersionBefore()).isEqualTo(1L);
        assertThat(transaction.getLotVersionAfter()).isEqualTo(2L);

        ArgumentCaptor<EquipmentHistory> historyCaptor =
                ArgumentCaptor.forClass(EquipmentHistory.class);
        verify(equipmentHistoryMapper).insert(historyCaptor.capture());
        EquipmentHistory history = historyCaptor.getValue();
        assertThat(history.getEventCode()).isEqualTo("TRACK_OUT");
        assertThat(history.getPrimaryStatusBefore()).isEqualTo("PROC");
        assertThat(history.getPrimaryStatusAfter()).isEqualTo("IDLE");
        assertThat(history.getEquipmentVersionBefore()).isEqualTo(1L);
        assertThat(history.getEquipmentVersionAfter()).isEqualTo(2L);
    }

    @Test
    void trackOutCompletesLotWhenCurrentStepIsLast() {
        stubValidTrackOut();
        when(routeStepMapper.selectOne(any())).thenReturn(null);
        LotCommandResultTO expected = stubResult("COMPLETED");

        LotCommandResultTO result = service.trackOut("LOT-016", request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<LotTransaction> transactionCaptor =
                ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getExecutionStatusAfter())
                .isEqualTo("COMPLETED");
        verify(equipmentHistoryMapper).insert(any(EquipmentHistory.class));
    }

    @Test
    void trackOutReturnsIdempotentResultWithoutWritingAgain() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        LotCommandResultTO repeated = new LotCommandResultTO(
                "LOT-016",
                "TRACK_OUT",
                "READY",
                "RELEASED",
                2L,
                true);
        when(lotCommandSupport.findIdempotentResult(
                lot,
                request,
                LotTransactionType.TRACK_OUT))
                .thenReturn(repeated);

        LotCommandResultTO result = service.trackOut("LOT-016", request);

        assertThat(result).isSameAs(repeated);
        verifyNoInteractions(equipmentMapper, routeStepMapper, lotMapper,
                lotTransactionMapper, equipmentHistoryMapper);
    }

    @Test
    void trackOutRejectsHeldLotBeforeReadingEquipment() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("RUNNING");
        when(lot.getHoldStatus()).thenReturn("HELD");

        assertThatThrownBy(() -> service.trackOut("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));

        verifyNoInteractions(equipmentMapper, routeStepMapper, lotMapper,
                lotTransactionMapper, equipmentHistoryMapper);
    }

    @Test
    void trackOutRejectsEquipmentThatIsNotProcessing() {
        stubRunningLot();
        stubCurrentRouteStep();
        when(equipmentMapper.selectById(102L)).thenReturn(equipment);
        when(equipment.getUpDownStatus()).thenReturn("U");
        when(equipment.getPrimaryStatus()).thenReturn("IDLE");

        assertThatThrownBy(() -> service.trackOut("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.EQUIPMENT_STATE_INVALID));

        verify(equipmentMapper, never()).update(any(), any());
        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void trackOutRejectsConcurrentEquipmentChangeBeforeChangingLot() {
        stubValidTrackOut();
        when(routeStepMapper.selectOne(any())).thenReturn(nextRouteStep);
        when(equipmentMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.trackOut("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.EQUIPMENT_STATE_INVALID));

        verify(lotMapper, never()).update(any(), any());
        verifyNoInteractions(lotTransactionMapper, equipmentHistoryMapper);
    }

    private void stubValidTrackOut() {
        stubRunningLot();
        stubCurrentRouteStep();
        when(equipmentMapper.selectById(102L)).thenReturn(equipment);
        lenient().when(equipment.getId()).thenReturn(102L);
        lenient().when(equipment.getVersion()).thenReturn(1L);
        when(equipment.getUpDownStatus()).thenReturn("U");
        when(equipment.getPrimaryStatus()).thenReturn("PROC");
        when(lotCommandSupport.nextVersion(lot)).thenReturn(2L);
        when(equipmentMapper.update(any(), any())).thenReturn(1);
        lenient().when(lotMapper.update(any(), any())).thenReturn(1);
    }

    private void stubRunningLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("RUNNING");
        when(lot.getHoldStatus()).thenReturn("RELEASED");
        when(lot.getCurrentRouteStepId()).thenReturn(20L);
        when(lot.getCurrentEquipmentId()).thenReturn(102L);
        lenient().when(lot.getRouteId()).thenReturn(1L);
        lenient().when(lot.getVersion()).thenReturn(1L);
    }

    private void stubCurrentRouteStep() {
        when(routeStepMapper.selectById(20L)).thenReturn(currentRouteStep);
        lenient().when(currentRouteStep.getId()).thenReturn(20L);
        when(currentRouteStep.getRouteId()).thenReturn(1L);
        lenient().when(currentRouteStep.getSequenceNo()).thenReturn(20);
        lenient().when(currentRouteStep.getOperationId()).thenReturn(2L);
        when(currentRouteStep.getRequiredEquipmentGroupId()).thenReturn(12L);
    }

    private LotCommandResultTO stubResult(String executionStatus) {
        LotCommandResultTO expected = new LotCommandResultTO(
                "LOT-016",
                "TRACK_OUT",
                executionStatus,
                "RELEASED",
                2L,
                false);
        when(lotCommandSupport.buildResult(
                lot,
                LotTransactionType.TRACK_OUT,
                executionStatus,
                "RELEASED",
                2L,
                false))
                .thenReturn(expected);
        return expected;
    }
}
