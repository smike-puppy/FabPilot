package com.fabpilot.mescore.commandvalidation;

import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.equipment;
import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.lot;
import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.routeStep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabpilot.mescore.alarm.enums.AlarmStatus;
import com.fabpilot.mescore.alarm.mapper.EquipmentAlarmMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import com.fabpilot.mescore.commandvalidation.service.impl.AcknowledgeAlarmCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.CloseAlarmCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.EquipmentEventCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.HoldCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.ReleaseCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.ReleaseHoldCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.ScrapCommandValidator;
import com.fabpilot.mescore.commandvalidation.service.impl.TrackOutCommandValidator;
import com.fabpilot.mescore.equipment.mapper.EquipmentEventDefinitionMapper;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.equipment.model.EquipmentEventDefinition;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import com.fabpilot.mescore.process.model.RouteStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemainingCommandValidatorTest {
    @Mock
    private LotMapper lotMapper;

    @Mock
    private RouteStepMapper routeStepMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private EquipmentEventDefinitionMapper eventDefinitionMapper;

    @Mock
    private EquipmentAlarmMapper alarmMapper;

    @InjectMocks
    private ReleaseCommandValidator releaseValidator;

    @InjectMocks
    private TrackOutCommandValidator trackOutValidator;

    @InjectMocks
    private HoldCommandValidator holdValidator;

    @InjectMocks
    private ReleaseHoldCommandValidator releaseHoldValidator;

    @InjectMocks
    private ScrapCommandValidator scrapValidator;

    @InjectMocks
    private EquipmentEventCommandValidator equipmentEventValidator;

    @InjectMocks
    private AcknowledgeAlarmCommandValidator acknowledgeAlarmValidator;

    @InjectMocks
    private CloseAlarmCommandValidator closeAlarmValidator;

    @Test
    void releaseShouldAllowCreatedReleasedLotWithResolvableStep() {
        Lot lot = lot("LOT-CREATED", "CREATED", "RELEASED", 30L, null, 0L);
        when(lotMapper.selectOne(any())).thenReturn(lot);

        assertAllowed(releaseValidator.validate(request(
                CommandType.RELEASE, TargetType.LOT, "LOT-CREATED", 0L)));
    }

    @Test
    void trackOutShouldAllowRunningLotOnUpProcessingEquipment() {
        Lot lot = lot("LOT-RUN", "RUNNING", "RELEASED", 30L, 103L, 2L);
        RouteStep step = routeStep(30L, 20L);
        Equipment equipment = equipment(103L, "U", "PROC", 4L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeStepMapper.selectById(30L)).thenReturn(step);
        when(equipmentMapper.selectById(103L)).thenReturn(equipment);

        assertAllowed(trackOutValidator.validate(request(
                CommandType.TRACK_OUT, TargetType.LOT, "LOT-RUN", 2L)));
    }

    @Test
    void holdShouldAllowActiveReleasedLotWithValidStep() {
        Lot lot = lot("LOT-READY", "READY", "RELEASED", 30L, null, 2L);
        RouteStep step = routeStep(30L, 20L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeStepMapper.selectById(30L)).thenReturn(step);

        assertAllowed(holdValidator.validate(request(
                CommandType.HOLD, TargetType.LOT, "LOT-READY", 2L)));
    }

    @Test
    void releaseHoldShouldAllowActiveHeldLotWithValidStep() {
        Lot lot = lot("LOT-HELD", "RUNNING", "HELD", 30L, 103L, 4L);
        RouteStep step = routeStep(30L, 20L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeStepMapper.selectById(30L)).thenReturn(step);

        assertAllowed(releaseHoldValidator.validate(request(
                CommandType.RELEASE_HOLD, TargetType.LOT, "LOT-HELD", 4L)));
    }

    @Test
    void scrapShouldAllowNonTerminalLotAndResolveOptionalReferences() {
        Lot lot = lot("LOT-READY", "READY", "RELEASED", 30L, null, 2L);
        RouteStep step = routeStep(30L, 20L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeStepMapper.selectById(30L)).thenReturn(step);

        assertAllowed(scrapValidator.validate(request(
                CommandType.SCRAP, TargetType.LOT, "LOT-READY", 2L)));
    }

    @Test
    void equipmentEventShouldAllowMatchingActiveDefinition() {
        Equipment equipment = equipment(103L, "U", "IDLE", 2L);
        EquipmentEventDefinition definition = mock(EquipmentEventDefinition.class);
        when(definition.getRequiresReason()).thenReturn(false);
        when(definition.getFromUpDownStatus()).thenReturn("U");
        when(definition.getFromPrimaryStatus()).thenReturn("IDLE");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(eventDefinitionMapper.selectOne(any())).thenReturn(definition);
        CommandValidationRequestTO request = request(
                CommandType.EXECUTE_EQUIPMENT_EVENT,
                TargetType.EQUIPMENT,
                "EQP-STATE-TEST-01",
                2L);
        request.setEventCode("START_MAINTENANCE");

        assertAllowed(equipmentEventValidator.validate(request));
    }

    @Test
    void acknowledgeAlarmShouldAllowActiveAlarm() {
        EquipmentAlarm alarm = alarm(10L, AlarmStatus.ACTIVE.databaseValue(), 0L, 103L);
        when(alarmMapper.selectById(10L)).thenReturn(alarm);
        CommandValidationRequestTO request = alarmRequest(
                CommandType.ACKNOWLEDGE_ALARM, 10L, 0L);

        assertAllowed(acknowledgeAlarmValidator.validate(request));
    }

    @Test
    void closeAlarmShouldRequireAcknowledgedAlarmAndRecoveredEquipment() {
        EquipmentAlarm alarm = alarm(10L, AlarmStatus.ACKNOWLEDGED.databaseValue(), 1L, 103L);
        Equipment equipment = equipment(103L, "U", "IDLE", 3L);
        when(alarmMapper.selectById(10L)).thenReturn(alarm);
        when(equipmentMapper.selectById(103L)).thenReturn(equipment);
        CommandValidationRequestTO request = alarmRequest(CommandType.CLOSE_ALARM, 10L, 1L);

        assertAllowed(closeAlarmValidator.validate(request));
    }

    @Test
    void closeAlarmShouldReturnAllIndependentFailures() {
        EquipmentAlarm alarm = alarm(10L, AlarmStatus.ACTIVE.databaseValue(), 2L, 103L);
        Equipment equipment = equipment(103L, "D", "DOWN", 3L);
        when(alarmMapper.selectById(10L)).thenReturn(alarm);
        when(equipmentMapper.selectById(103L)).thenReturn(equipment);
        CommandValidationRequestTO request = alarmRequest(CommandType.CLOSE_ALARM, 10L, 1L);

        CommandValidationResultTO result = closeAlarmValidator.validate(request);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getChecks()).filteredOn(check -> !check.isPassed())
                .extracting("ruleCode")
                .contains("ALARM_VERSION_MATCH", "ALARM_STATUS_ALLOWED",
                        "ALARM_EQUIPMENT_RECOVERED");
    }

    private CommandValidationRequestTO request(
            CommandType commandType,
            TargetType targetType,
            String targetCode,
            long expectedVersion) {
        CommandValidationRequestTO request = new CommandValidationRequestTO();
        request.setCommandType(commandType);
        request.setTargetType(targetType);
        request.setTargetCode(targetCode);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    private CommandValidationRequestTO alarmRequest(
            CommandType commandType,
            long alarmId,
            long expectedVersion) {
        CommandValidationRequestTO request = request(
                commandType, TargetType.ALARM, String.valueOf(alarmId), expectedVersion);
        request.setAlarmId(alarmId);
        return request;
    }

    private EquipmentAlarm alarm(long id, String status, long version, long equipmentId) {
        EquipmentAlarm alarm = mock(EquipmentAlarm.class);
        lenient().when(alarm.getId()).thenReturn(id);
        lenient().when(alarm.getStatus()).thenReturn(status);
        lenient().when(alarm.getVersion()).thenReturn(version);
        lenient().when(alarm.getEquipmentId()).thenReturn(equipmentId);
        return alarm;
    }

    private void assertAllowed(CommandValidationResultTO result) {
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getChecks()).allMatch(check -> check.isEvaluated() && check.isPassed());
    }
}