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
import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
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
class LotHoldServiceImplTest {

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
    private RouteStep routeStep;
    private HoldLotRequestTO request;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "hold-test");
        TableInfoHelper.initTableInfo(assistant, Lot.class);
        TableInfoHelper.initTableInfo(assistant, Equipment.class);
    }

    @BeforeEach
    void setUp() {
        lot = mock(Lot.class);
        routeStep = mock(RouteStep.class);
        request = new HoldLotRequestTO(
                2L,
                "POSTMAN-LOT-016-HOLD-001",
                "POSTMAN-USER",
                "QUALITY_CHECK",
                "检测工序前需要复核质量数据");
    }

    @Test
    void holdKeepsExecutionStatusAndAppendsReasonedHistory() {
        stubValidReadyLot();
        LotCommandResultTO expected = new LotCommandResultTO(
                "LOT-016", "HOLD", "READY", "HELD", 3L, false);
        when(lotCommandSupport.buildResult(
                lot,
                LotTransactionType.HOLD,
                "READY",
                "HELD",
                3L,
                false))
                .thenReturn(expected);

        LotCommandResultTO result = service.hold("LOT-016", request);

        assertThat(result).isSameAs(expected);
        verify(lotMapper).update(any(), any());
        ArgumentCaptor<LotTransaction> captor =
                ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(captor.capture());
        LotTransaction transaction = captor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("HOLD");
        assertThat(transaction.getExecutionStatusBefore()).isEqualTo("READY");
        assertThat(transaction.getExecutionStatusAfter()).isEqualTo("READY");
        assertThat(transaction.getHoldStatusBefore()).isEqualTo("RELEASED");
        assertThat(transaction.getHoldStatusAfter()).isEqualTo("HELD");
        assertThat(transaction.getReasonCode()).isEqualTo("QUALITY_CHECK");
        assertThat(transaction.getReasonText()).isEqualTo("检测工序前需要复核质量数据");
        assertThat(transaction.getRouteStepId()).isEqualTo(30L);
        assertThat(transaction.getEquipmentId()).isNull();
        assertThat(transaction.getLotVersionBefore()).isEqualTo(2L);
        assertThat(transaction.getLotVersionAfter()).isEqualTo(3L);
        verifyNoInteractions(equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void holdRunningLotPreservesCurrentEquipmentInAuditHistory() {
        stubValidReadyLot();
        when(lot.getExecutionStatus()).thenReturn("RUNNING");
        when(lot.getCurrentEquipmentId()).thenReturn(103L);
        when(lotCommandSupport.buildResult(
                lot,
                LotTransactionType.HOLD,
                "RUNNING",
                "HELD",
                3L,
                false))
                .thenReturn(new LotCommandResultTO(
                        "LOT-016", "HOLD", "RUNNING", "HELD", 3L, false));

        service.hold("LOT-016", request);

        ArgumentCaptor<LotTransaction> captor =
                ArgumentCaptor.forClass(LotTransaction.class);
        verify(lotTransactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getExecutionStatusAfter()).isEqualTo("RUNNING");
        assertThat(captor.getValue().getEquipmentId()).isEqualTo(103L);
        verifyNoInteractions(equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void holdReturnsIdempotentResultWithoutWritingAgain() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        LotCommandResultTO repeated = new LotCommandResultTO(
                "LOT-016", "HOLD", "READY", "HELD", 3L, true);
        when(lotCommandSupport.findIdempotentResultByReason(
                lot,
                request,
                LotTransactionType.HOLD,
                request.getReasonCode(),
                request.getReasonText()))
                .thenReturn(repeated);

        LotCommandResultTO result = service.hold("LOT-016", request);

        assertThat(result).isSameAs(repeated);
        verifyNoInteractions(lotMapper, lotTransactionMapper, routeStepMapper,
                equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void holdRejectsAlreadyHeldLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("HELD");

        assertThatThrownBy(() -> service.hold("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));

        verify(lotMapper, never()).update(any(), any());
        verifyNoInteractions(lotTransactionMapper, routeStepMapper,
                equipmentMapper, equipmentHistoryMapper);
    }

    @Test
    void holdRejectsCompletedLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        when(lot.getExecutionStatus()).thenReturn("COMPLETED");

        assertThatThrownBy(() -> service.hold("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.LOT_STATE_INVALID));

        verify(lotMapper, never()).update(any(), any());
    }

    @Test
    void holdRejectsConcurrentLotChangeWithoutAppendingHistory() {
        stubValidReadyLot();
        when(lotMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.hold("LOT-016", request))
                .isInstanceOfSatisfying(LotCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(LotCommandErrorCode.LOT_VERSION_CONFLICT));

        verifyNoInteractions(lotTransactionMapper, equipmentMapper, equipmentHistoryMapper);
    }

    private void stubValidReadyLot() {
        when(lotCommandSupport.findLot("LOT-016")).thenReturn(lot);
        lenient().when(lot.getId()).thenReturn(16L);
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("RELEASED");
        when(lot.getCurrentRouteStepId()).thenReturn(30L);
        lenient().when(lot.getCurrentEquipmentId()).thenReturn(null);
        lenient().when(lot.getVersion()).thenReturn(2L);
        when(lot.getRouteId()).thenReturn(1L);
        when(routeStepMapper.selectById(30L)).thenReturn(routeStep);
        lenient().when(routeStep.getId()).thenReturn(30L);
        when(routeStep.getRouteId()).thenReturn(1L);
        lenient().when(routeStep.getOperationId()).thenReturn(3L);
        when(routeStep.getRequiredEquipmentGroupId()).thenReturn(13L);
        when(lotCommandSupport.nextVersion(lot)).thenReturn(3L);
        lenient().when(lotMapper.update(any(), any())).thenReturn(1);
    }
}
