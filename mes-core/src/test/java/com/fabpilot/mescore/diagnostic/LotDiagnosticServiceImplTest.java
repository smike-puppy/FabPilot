package com.fabpilot.mescore.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.diagnostic.dto.LotDiagnosticContextTO;
import com.fabpilot.mescore.diagnostic.exception.LotNotFoundException;
import com.fabpilot.mescore.diagnostic.service.impl.LotDiagnosticServiceImpl;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.mapper.LotTransactionMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.mapper.OperationMapper;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.workorder.mapper.WorkOrderMapper;
import com.fabpilot.mescore.workorder.model.WorkOrder;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotDiagnosticServiceImplTest {
    /** 纯 Mockito 测试没有 Spring 启动过程，因此显式初始化 Lambda 查询需要的表元数据。 */
    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "diagnostic-test");
        TableInfoHelper.initTableInfo(assistant, EquipmentAlarm.class);
    }
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

    @Mock
    private EquipmentAlarmMapper equipmentAlarmMapper;

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
                equipmentHistoryMapper, equipmentAlarmMapper);
    }

    @Test
    void throwsDomainExceptionWhenLotDoesNotExist() {
        when(lotMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getDiagnosticContext("LOT-404"))
                .isInstanceOf(LotNotFoundException.class)
                .hasMessage("Lot not found: LOT-404");
    }

    @Test
    void returnsOpenAlarmsForCurrentEquipment() {
        Lot lot = mock(Lot.class);
        WorkOrder workOrder = mock(WorkOrder.class);
        Equipment equipment = mock(Equipment.class);
        EquipmentAlarm alarm = mock(EquipmentAlarm.class);
        LocalDateTime openedAt = LocalDateTime.now().minusSeconds(120);

        when(lot.getId()).thenReturn(13L);
        when(lot.getCode()).thenReturn("LOT-013");
        when(lot.getWorkOrderId()).thenReturn(8L);
        when(lot.getCurrentRouteStepId()).thenReturn(null);
        when(lot.getCurrentEquipmentId()).thenReturn(3L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(workOrderMapper.selectById(8L)).thenReturn(workOrder);
        when(equipmentMapper.selectById(3L)).thenReturn(equipment);
        when(equipment.getId()).thenReturn(3L);
        when(lotTransactionMapper.selectList(any())).thenReturn(List.of());
        when(equipmentHistoryMapper.selectList(any())).thenReturn(List.of());
        when(equipmentAlarmMapper.selectList(any())).thenReturn(List.of(alarm));

        when(alarm.getId()).thenReturn(21L);
        when(alarm.getAlarmCode()).thenReturn("VACUUM_LOW");
        when(alarm.getSeverity()).thenReturn("HIGH");
        when(alarm.getStatus()).thenReturn("ACTIVE");
        when(alarm.getSourceEventCode()).thenReturn("EQUIPMENT_DOWN");
        when(alarm.getMessage()).thenReturn("Vacuum is below threshold");
        when(alarm.getOpenedAt()).thenReturn(openedAt);
        when(alarm.getVersion()).thenReturn(0L);

        LotDiagnosticContextTO response = service.getDiagnosticContext("LOT-013");

        assertThat(response.getActiveAlarms()).hasSize(1);
        LotDiagnosticContextTO.AlarmSnapshot result = response.getActiveAlarms().get(0);
        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.getAlarmCode()).isEqualTo("VACUUM_LOW");
        assertThat(result.getSeverity()).isEqualTo("HIGH");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getOpenDurationSeconds()).isGreaterThanOrEqualTo(120L);
    }}
