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
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
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
class LotCommandServiceImplTest {

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
    private RouteStep routeStep;
    private TrackInLotRequestTO request;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "track-in-test");
        TableInfoHelper.initTableInfo(assistant, Equipment.class);
        TableInfoHelper.initTableInfo(assistant, Lot.class);
    }

    @BeforeEach
    void setUp() {
        lot = mock(Lot.class);
        equipment = mock(Equipment.class);
        routeStep = mock(RouteStep.class);
        request = new TrackInLotRequestTO(
                2L,
                "IDEMP-LOT-014-TRACK-IN-001",
                "OP-001",
                "ETCH-02");
    }

    @Test
    void trackInUpdatesBothSnapshotsAndAppendsBothHistories() {
        stubValidTrackIn();
        LotCommandResultTO expected = new LotCommandResultTO(
                "LOT-014",
                "TRACK_IN",
                "RUNNING",
                "RELEASED",
                3L,
                false);
        when(lotCommandSupport.buildResult(
                lot,
                com.fabpilot.mescore.lot.enums.LotTransactionType.TRACK_IN,
                "RUNNING",
                "RELEASED",
                3L,
                false))
                .thenReturn(expected);

        LotCommandResultTO result = service.trackIn("LOT-014", request);

        assertThat(result).isSameAs(expected);
        verify(equipmentMapper).update(any(), any());
        verify(lotMapper).update(any(), any());

        ArgumentCaptor<LotTransaction> transactionCaptor =
                ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(transactionCaptor.capture());
        LotTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("TRACK_IN");
        assertThat(transaction.getRouteStepId()).isEqualTo(20L);
        assertThat(transaction.getOperationId()).isEqualTo(2L);
        assertThat(transaction.getEquipmentId()).isEqualTo(102L);
        assertThat(transaction.getExecutionStatusBefore()).isEqualTo("READY");
        assertThat(transaction.getExecutionStatusAfter()).isEqualTo("RUNNING");
        assertThat(transaction.getLotVersionBefore()).isEqualTo(2L);
        assertThat(transaction.getLotVersionAfter()).isEqualTo(3L);

        ArgumentCaptor<EquipmentHistory> historyCaptor =
                ArgumentCaptor.forClass(EquipmentHistory.class);
        verify(equipmentHistoryMapper).insert(historyCaptor.capture());
        EquipmentHistory history = historyCaptor.getValue();
        assertThat(history.getEventCode()).isEqualTo("TRACK_IN");
        assertThat(history.getPrimaryStatusBefore()).isEqualTo("IDLE");
        assertThat(history.getPrimaryStatusAfter()).isEqualTo("PROC");
        assertThat(history.getEquipmentVersionBefore()).isEqualTo(0L);
        assertThat(history.getEquipmentVersionAfter()).isEqualTo(1L);
        assertThat(history.getIdempotencyKey()).isEqualTo(request.getIdempotencyKey());
    }

    @Test
    void trackInReturnsIdempotentResultWithoutWritingAgain() {
        when(lotCommandSupport.findLot("LOT-014")).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(equipment.getId()).thenReturn(102L);
        LotCommandResultTO repeated = new LotCommandResultTO(
                "LOT-014",
                "TRACK_IN",
                "RUNNING",
                "RELEASED",
                3L,
                true);
        when(lotCommandSupport.findIdempotentResult(
                lot,
                request,
                com.fabpilot.mescore.lot.enums.LotTransactionType.TRACK_IN,
                102L))
                .thenReturn(repeated);

        LotCommandResultTO result = service.trackIn("LOT-014", request);

        assertThat(result).isSameAs(repeated);
        verify(lotMapper, never()).update(any(), any());
        verify(equipmentMapper, never()).update(any(), any());
        verifyNoInteractions(lotTransactionMapper, equipmentHistoryMapper, routeStepMapper);
    }

    @Test
    void trackInRejectsHeldLotBeforeChangingEquipment() {
        stubLookup();
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("HELD");

        assertThatThrownBy(() -> service.trackIn("LOT-014", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));

        verify(equipmentMapper, never()).update(any(), any());
        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void trackInRejectsEquipmentOutsideRequiredCapabilityGroup() {
        stubLookup();
        stubReadyLot();
        when(lot.getCurrentRouteStepId()).thenReturn(20L);
        when(lot.getRouteId()).thenReturn(1L);
        when(routeStepMapper.selectById(20L)).thenReturn(routeStep);
        when(routeStep.getRouteId()).thenReturn(1L);
        when(routeStep.getRequiredEquipmentGroupId()).thenReturn(12L);
        when(equipment.getUpDownStatus()).thenReturn("U");
        when(equipment.getPrimaryStatus()).thenReturn("IDLE");
        when(equipmentMapper.countGroupMembership(12L, 102L)).thenReturn(0);

        assertThatThrownBy(() -> service.trackIn("LOT-014", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.EQUIPMENT_CAPABILITY_MISMATCH));

        verify(equipmentMapper, never()).update(any(), any());
        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void trackInRejectsConcurrentEquipmentStateChange() {
        stubValidTrackIn();
        when(equipmentMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.trackIn("LOT-014", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.EQUIPMENT_STATE_INVALID));

        verify(lotMapper, never()).update(any(), any());
        verifyNoInteractions(lotTransactionMapper, equipmentHistoryMapper);
    }

    private void stubValidTrackIn() {
        stubLookup();
        stubReadyLot();
        when(lot.getCurrentRouteStepId()).thenReturn(20L);
        when(lot.getRouteId()).thenReturn(1L);
        lenient().when(lot.getVersion()).thenReturn(2L);
        when(routeStepMapper.selectById(20L)).thenReturn(routeStep);
        lenient().when(routeStep.getId()).thenReturn(20L);
        when(routeStep.getRouteId()).thenReturn(1L);
        lenient().when(routeStep.getOperationId()).thenReturn(2L);
        when(routeStep.getRequiredEquipmentGroupId()).thenReturn(12L);
        when(equipment.getVersion()).thenReturn(0L);
        when(equipment.getUpDownStatus()).thenReturn("U");
        when(equipment.getPrimaryStatus()).thenReturn("IDLE");
        when(equipmentMapper.countGroupMembership(12L, 102L)).thenReturn(1);
        when(lotMapper.selectCount(any())).thenReturn(0L);
        when(lotCommandSupport.nextVersion(lot)).thenReturn(3L);
        when(equipmentMapper.update(any(), any())).thenReturn(1);
        lenient().when(lotMapper.update(any(), any())).thenReturn(1);
    }

    private void stubLookup() {
        when(lotCommandSupport.findLot("LOT-014")).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(equipment.getId()).thenReturn(102L);
    }

    private void stubReadyLot() {
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("RELEASED");
        when(lot.getCurrentEquipmentId()).thenReturn(null);
    }
}