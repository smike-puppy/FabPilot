package com.fabpilot.mescore.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.common.command.CommandExecutionSupport;
import com.fabpilot.mescore.equipment.dto.EquipmentEventResultTO;
import com.fabpilot.mescore.equipment.dto.ExecuteEquipmentEventRequestTO;
import com.fabpilot.mescore.equipment.exception.EquipmentCommandErrorCode;
import com.fabpilot.mescore.equipment.exception.EquipmentCommandException;
import com.fabpilot.mescore.equipment.mapper.EquipmentEventDefinitionMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentHistoryMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentEventDefinition;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import com.fabpilot.mescore.equipment.service.impl.EquipmentEventServiceImpl;
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
class EquipmentEventServiceImplTest {
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentAlarmMapper equipmentAlarmMapper;
    @Mock private EquipmentEventDefinitionMapper eventDefinitionMapper;
    @Mock private EquipmentHistoryMapper equipmentHistoryMapper;
    @Mock private CommandExecutionSupport commandExecutionSupport;
    @InjectMocks private EquipmentEventServiceImpl service;

    private Equipment equipment;
    private EquipmentEventDefinition definition;
    private ExecuteEquipmentEventRequestTO request;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "equipment-event-test");
        TableInfoHelper.initTableInfo(assistant, Equipment.class);
        TableInfoHelper.initTableInfo(assistant, EquipmentEventDefinition.class);
        TableInfoHelper.initTableInfo(assistant, EquipmentHistory.class);
    }

    @BeforeEach
    void setUp() {
        equipment = org.mockito.Mockito.mock(Equipment.class);
        definition = org.mockito.Mockito.mock(EquipmentEventDefinition.class);
        request = new ExecuteEquipmentEventRequestTO(1L, "IDEMP-EQP-FAULT-001",
                "EQP-GATEWAY", "ETCH-02", "VACUUM_LOW", "SYSTEM",
                "VACUUM_LOW", "真空值低于生产下限");
    }

    @Test
    void executeEventMovesProcessingEquipmentDownAndAppendsHistory() {
        stubProcessingEquipmentAndFaultDefinition();
        when(commandExecutionSupport.nextVersion(1L)).thenReturn(2L);
        when(equipmentMapper.update(any(), any())).thenReturn(1);

        EquipmentEventResultTO result = service.executeEvent(request);

        assertThat(result.getEquipmentCode()).isEqualTo("ETCH-02");
        assertThat(result.getEventCode()).isEqualTo("VACUUM_LOW");
        assertThat(result.getUpDownStatus()).isEqualTo("D");
        assertThat(result.getPrimaryStatus()).isEqualTo("DOWN");
        assertThat(result.getVersion()).isEqualTo(2L);
        assertThat(result.isIdempotent()).isFalse();
        verify(equipmentMapper).update(any(), any());

        ArgumentCaptor<EquipmentHistory> captor =
                ArgumentCaptor.forClass(EquipmentHistory.class);
        verify(equipmentHistoryMapper).insert(captor.capture());
        verify(equipmentAlarmMapper).insert(any(EquipmentAlarm.class));
        EquipmentHistory history = captor.getValue();
        assertThat(history.getEquipmentId()).isEqualTo(103L);
        assertThat(history.getEventCode()).isEqualTo("VACUUM_LOW");
        assertThat(history.getUpDownStatusBefore()).isEqualTo("U");
        assertThat(history.getUpDownStatusAfter()).isEqualTo("D");
        assertThat(history.getPrimaryStatusBefore()).isEqualTo("PROC");
        assertThat(history.getPrimaryStatusAfter()).isEqualTo("DOWN");
        assertThat(history.getOperatorType()).isEqualTo("SYSTEM");
        assertThat(history.getOperatorRole()).isEqualTo("ENGINEERING");
        assertThat(history.getReasonCode()).isEqualTo("VACUUM_LOW");
        assertThat(history.getReasonText()).isEqualTo("真空值低于生产下限");
        assertThat(history.getEquipmentVersionBefore()).isEqualTo(1L);
        assertThat(history.getEquipmentVersionAfter()).isEqualTo(2L);
    }

    @Test
    void executeEventReturnsIdempotentResultBeforeVersionAndStateValidation() {
        stubEquipment();
        EquipmentHistory previous = org.mockito.Mockito.mock(EquipmentHistory.class);
        when(previous.getEquipmentId()).thenReturn(103L);
        when(previous.getEventCode()).thenReturn("VACUUM_LOW");
        when(previous.getOperatorType()).thenReturn("SYSTEM");
        when(previous.getReasonCode()).thenReturn("VACUUM_LOW");
        when(previous.getReasonText()).thenReturn("真空值低于生产下限");
        when(equipmentHistoryMapper.selectOne(any())).thenReturn(previous);
        when(equipment.getUpDownStatus()).thenReturn("D");
        when(equipment.getPrimaryStatus()).thenReturn("DOWN");
        when(equipment.getVersion()).thenReturn(2L);

        EquipmentEventResultTO result = service.executeEvent(request);

        assertThat(result.isIdempotent()).isTrue();
        assertThat(result.getVersion()).isEqualTo(2L);
        verifyNoInteractions(eventDefinitionMapper, commandExecutionSupport);
        verify(equipmentMapper, never()).update(any(), any());
        verify(equipmentHistoryMapper, never()).insert(any(EquipmentHistory.class));
    }

    @Test
    void executeEventAcceptsMaintenanceTransitionDefinedByStateMachine() {
        stubEquipment();
        when(equipment.getUpDownStatus()).thenReturn("D");
        when(equipment.getPrimaryStatus()).thenReturn("DOWN");
        when(equipmentHistoryMapper.selectOne(any())).thenReturn(null);
        when(eventDefinitionMapper.selectOne(any())).thenReturn(definition);
        when(definition.getEventCode()).thenReturn("START_MAINTENANCE");
        when(definition.getEventCategory()).thenReturn("ENGINEERING");
        when(definition.getFromUpDownStatus()).thenReturn("D");
        when(definition.getFromPrimaryStatus()).thenReturn("DOWN");
        when(definition.getToUpDownStatus()).thenReturn("D");
        when(definition.getToPrimaryStatus()).thenReturn("MAINTENANCE");
        when(commandExecutionSupport.nextVersion(1L)).thenReturn(2L);
        when(equipmentMapper.update(any(), any())).thenReturn(1);
        request.setEventCode("START_MAINTENANCE");
        request.setOperatorType("USER");

        EquipmentEventResultTO result = service.executeEvent(request);

        assertThat(result.getPrimaryStatus()).isEqualTo("MAINTENANCE");
        assertThat(result.getVersion()).isEqualTo(2L);
        verify(equipmentHistoryMapper).insert(any(EquipmentHistory.class));
    }
    @Test
    void executeEventRejectsEquipmentWhoseSourceStateDoesNotMatchDefinition() {
        stubEquipment();
        when(equipment.getPrimaryStatus()).thenReturn("IDLE");
        when(equipmentHistoryMapper.selectOne(any())).thenReturn(null);
        stubFaultDefinition();

        assertThatThrownBy(() -> service.executeEvent(request))
                .isInstanceOfSatisfying(EquipmentCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EquipmentCommandErrorCode.STATE_INVALID));
        verify(equipmentMapper, never()).update(any(), any());
    }

    @Test
    void executeEventRejectsConcurrentSnapshotChangeWithoutAppendingHistory() {
        stubProcessingEquipmentAndFaultDefinition();
        when(commandExecutionSupport.nextVersion(1L)).thenReturn(2L);
        when(equipmentMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.executeEvent(request))
                .isInstanceOfSatisfying(EquipmentCommandException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EquipmentCommandErrorCode.VERSION_CONFLICT));
        verify(equipmentHistoryMapper, never()).insert(any(EquipmentHistory.class));
    }

    private void stubProcessingEquipmentAndFaultDefinition() {
        stubEquipment();
        when(equipmentHistoryMapper.selectOne(any())).thenReturn(null);
        stubFaultDefinition();
    }

    private void stubEquipment() {
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        org.mockito.Mockito.lenient().when(equipment.getId()).thenReturn(103L);
        org.mockito.Mockito.lenient().when(equipment.getCode()).thenReturn("ETCH-02");
        org.mockito.Mockito.lenient().when(equipment.getUpDownStatus()).thenReturn("U");
        org.mockito.Mockito.lenient().when(equipment.getPrimaryStatus()).thenReturn("PROC");
        org.mockito.Mockito.lenient().when(equipment.getVersion()).thenReturn(1L);
    }

    private void stubFaultDefinition() {
        when(eventDefinitionMapper.selectOne(any())).thenReturn(definition);
        org.mockito.Mockito.lenient().when(definition.getEventCode()).thenReturn("VACUUM_LOW");
        org.mockito.Mockito.lenient().when(definition.getEventCategory()).thenReturn("ENGINEERING");
        when(definition.getFromUpDownStatus()).thenReturn("U");
        when(definition.getFromPrimaryStatus()).thenReturn("PROC");
        org.mockito.Mockito.lenient().when(definition.getToUpDownStatus()).thenReturn("D");
        org.mockito.Mockito.lenient().when(definition.getToPrimaryStatus()).thenReturn("DOWN");
    }
}