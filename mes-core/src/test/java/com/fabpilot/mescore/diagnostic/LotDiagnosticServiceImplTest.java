package com.fabpilot.mescore.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabpilot.mescore.diagnostic.exception.LotNotFoundException;
import com.fabpilot.mescore.diagnostic.service.impl.LotDiagnosticServiceImpl;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.mapper.OperationMapper;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.workorder.mapper.WorkOrderMapper;
import com.fabpilot.mescore.workorder.model.WorkOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotDiagnosticServiceImplTest {
    @Mock
    private LotMapper lotMapper;

    @Mock
    private LotTransactionMapper lotTransactionMapper;

    @Mock
    private WorkOrderMapper workOrderMapper;

    @Mock
    private RouteStepMapper routeStepMapper;

    @Mock
    private OperationMapper operationMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private EquipmentHistoryMapper equipmentHistoryMapper;

    @InjectMocks
    private LotDiagnosticServiceImpl service;

    @Test
    void returnsDiagnosticContextForExistingLotWithoutCurrentAssignment() {
        Lot lot = mock(Lot.class);
        WorkOrder workOrder = mock(WorkOrder.class);
        when(lot.getId()).thenReturn(13L);
        when(lot.getCode()).thenReturn("LOT-013");
        when(lot.getWorkOrderId()).thenReturn(8L);
        when(lot.getCurrentRouteStepId()).thenReturn(null);
        when(lot.getCurrentEquipmentId()).thenReturn(null);
        when(lot.getExecutionStatus()).thenReturn("READY");
        when(lot.getHoldStatus()).thenReturn("RELEASED");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(workOrderMapper.selectById(8L)).thenReturn(workOrder);
        when(workOrder.getCode()).thenReturn("WO-2026-008");
        when(lotTransactionMapper.selectList(any())).thenReturn(List.of());

        var response = service.getDiagnosticContext("LOT-013");

        assertThat(response.getLot().getCode()).isEqualTo("LOT-013");
        assertThat(response.getWorkOrder().getCode()).isEqualTo("WO-2026-008");
        assertThat(response.getCurrentStep()).isNull();
        assertThat(response.getCurrentEquipment()).isNull();
        assertThat(response.getRecentLotTransactions()).isEmpty();
        assertThat(response.getRecentEquipmentEvents()).isEmpty();
        verifyNoInteractions(routeStepMapper, operationMapper, equipmentMapper,
                equipmentHistoryMapper);
    }

    @Test
    void throwsDomainExceptionWhenLotDoesNotExist() {
        when(lotMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getDiagnosticContext("LOT-404"))
                .isInstanceOf(LotNotFoundException.class)
                .hasMessage("Lot not found: LOT-404");
    }
}
