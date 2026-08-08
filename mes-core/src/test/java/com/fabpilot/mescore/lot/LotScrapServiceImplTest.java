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
import com.fabpilot.mescore.lot.dto.ScrapLotRequestTO;
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
class LotScrapServiceImplTest {
    @Mock private LotMapper lotMapper;
    @Mock private LotTransactionMapper lotTransactionMapper;
    @Mock private RouteStepMapper routeStepMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentHistoryMapper equipmentHistoryMapper;
    @Mock private LotCommandSupport lotCommandSupport;
    @InjectMocks private LotCommandServiceImpl service;
    private Lot lot;
    private RouteStep routeStep;
    private ScrapLotRequestTO request;

    @BeforeAll
    static void initMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "scrap-test");
        TableInfoHelper.initTableInfo(assistant, Lot.class);
        TableInfoHelper.initTableInfo(assistant, Equipment.class);
    }

    @BeforeEach
    void setUp() {
        lot = mock(Lot.class);
        routeStep = mock(RouteStep.class);
        request = new ScrapLotRequestTO(4L, "POSTMAN-LOT-016-SCRAP-001",
                "POSTMAN-USER", "INSPECTION_REJECT", "Inspection result exceeded limit");
    }

    @Test
    void scrapReadyLotCreatesTerminalReasonedHistory() {
        stubReadyLot();
        LotCommandResultTO expected = new LotCommandResultTO(
                "LOT-016", "SCRAP", "SCRAPPED", "RELEASED", 5L, false);
        when(lotCommandSupport.buildResult(lot, LotTransactionType.SCRAP,
                "SCRAPPED", "RELEASED", 5L, false)).thenReturn(expected);

        assertThat(service.scrap("LOT-016", request)).isSameAs(expected);

        verify(lotMapper).update(any(), any());
        ArgumentCaptor<LotTransaction> captor = ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(captor.capture());
        LotTransaction transaction = captor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("SCRAP");
        assertThat(transaction.getExecutionStatusBefore()).isEqualTo("READY");
        assertThat(transaction.getExecutionStatusAfter()).isEqualTo("SCRAPPED");
        assertThat(transaction.getHoldStatusBefore()).isEqualTo("RELEASED");
        assertThat(transaction.getHoldStatusAfter()).isEqualTo("RELEASED");
        assertThat(transaction.getReasonCode()).isEqualTo("INSPECTION_REJECT");
        assertThat(transaction.getReasonText()).isEqualTo("Inspection result exceeded limit");
        assertThat(transaction.getRouteStepId()).isEqualTo(30L);
        assertThat(transaction.getEquipmentId()).isNull();
        assertThat(transaction.getLotVersionBefore()).isEqualTo(4L);
        assertThat(transaction.getLotVersionAfter()).isEqualTo(5L);
        verifyNoInteractions(equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void scrapRunningLotReleasesProcessingEquipmentAndWritesBothHistories() {
        Equipment equipment = mock(Equipment.class);
        stubReadyLot();
        when(lot.getExecutionStatus()).thenReturn("RUNNING");
        when(lot.getCurrentEquipmentId()).thenReturn(103L);
        when(equipmentMapper.selectById(103L)).thenReturn(equipment);
        when(equipment.getId()).thenReturn(103L);
        when(equipment.getPrimaryStatus()).thenReturn("PROC");
        when(equipment.getUpDownStatus()).thenReturn("U");
        when(equipment.getVersion()).thenReturn(7L);
        when(equipmentMapper.update(any(), any())).thenReturn(1);

        service.scrap("LOT-016", request);

        verify(equipmentMapper).update(any(), any());
        ArgumentCaptor<EquipmentHistory> historyCaptor =
                ArgumentCaptor.forClass(EquipmentHistory.class);
        verify(equipmentHistoryMapper).insert(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEventCode()).isEqualTo("SCRAP");
        assertThat(historyCaptor.getValue().getPrimaryStatusBefore()).isEqualTo("PROC");
        assertThat(historyCaptor.getValue().getPrimaryStatusAfter()).isEqualTo("IDLE");
        assertThat(historyCaptor.getValue().getEquipmentVersionBefore()).isEqualTo(7L);
        assertThat(historyCaptor.getValue().getEquipmentVersionAfter()).isEqualTo(8L);
    }

    @Test
    void scrapReturnsIdempotentResultWithoutWritingAgain() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        LotCommandResultTO repeated = new LotCommandResultTO(
                "LOT-016", "SCRAP", "SCRAPPED", "RELEASED", 5L, true);
        when(lotCommandSupport.findIdempotentResultByReason(lot, request,
                LotTransactionType.SCRAP, request.getReasonCode(), request.getReasonText()))
                .thenReturn(repeated);
        assertThat(service.scrap("LOT-016", request)).isSameAs(repeated);
        verifyNoInteractions(lotMapper, lotTransactionMapper, routeStepMapper,
                equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void scrapRejectsCompletedLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("COMPLETED");
        assertThatThrownBy(() -> service.scrap("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));
        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void scrapRejectsConcurrentLotChangeWithoutHistory() {
        stubReadyLot();
        when(lotMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.scrap("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotCommandErrorCode.LOT_VERSION_CONFLICT));
        verifyNoInteractions(lotTransactionMapper, equipmentHistoryMapper);
    }

    private void stubReadyLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        lenient().when(lot.getId()).thenReturn(16L);
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("RELEASED");
        when(lot.getCurrentRouteStepId()).thenReturn(30L);
        lenient().when(lot.getCurrentEquipmentId()).thenReturn(null);
        lenient().when(lot.getVersion()).thenReturn(4L);
        when(lot.getRouteId()).thenReturn(1L);
        when(routeStepMapper.selectById(30L)).thenReturn(routeStep);
        lenient().when(routeStep.getId()).thenReturn(30L);
        when(routeStep.getRouteId()).thenReturn(1L);
        lenient().when(routeStep.getOperationId()).thenReturn(3L);
        when(routeStep.getRequiredEquipmentGroupId()).thenReturn(13L);
        when(lotCommandSupport.nextVersion(lot)).thenReturn(5L);
        lenient().when(lotMapper.update(any(), any())).thenReturn(1);
    }
}