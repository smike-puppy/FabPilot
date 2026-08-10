package com.fabpilot.mescore.commandvalidation;

import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.equipment;
import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.lot;
import static com.fabpilot.mescore.lot.support.LotCommandTestFixture.routeStep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.dto.RuleCheckResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import com.fabpilot.mescore.commandvalidation.service.impl.TrackInCommandValidator;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.mapper.LotMapper;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.mapper.RouteStepMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackInCommandValidatorTest {
    @Mock
    private LotMapper lotMapper;

    @Mock
    private RouteStepMapper routeStepMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @InjectMocks
    private TrackInCommandValidator validator;

    private CommandValidationRequestTO request;

    @BeforeEach
    void setUp() {
        request = new CommandValidationRequestTO();
        request.setCommandType(CommandType.TRACK_IN);
        request.setTargetType(TargetType.LOT);
        request.setTargetCode("LOT-100");
        request.setExpectedVersion(3L);
        request.setEquipmentCode("ETCH-01");
    }

    @Test
    void shouldAllowWhenEveryTrackInRulePasses() {
        Lot lot = lot("LOT-100", "READY", "RELEASED", 30L, null, 3L);
        Equipment equipment = equipment(103L, "U", "IDLE", 5L);
        var currentRouteStep = routeStep(30L, 20L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(routeStepMapper.selectById(30L)).thenReturn(currentRouteStep);
        when(equipmentMapper.countGroupMembership(13L, 103L)).thenReturn(1);
        when(lotMapper.selectCount(any())).thenReturn(0L);

        CommandValidationResultTO result = validator.validate(request);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getObservedVersion()).isEqualTo(3L);
        assertThat(result.getChecks()).hasSize(13).allMatch(RuleCheckResultTO::isPassed);
    }

    @Test
    void shouldReturnAllFailuresInsteadOfStoppingAtFirstFailure() {
        Lot lot = lot("LOT-100", "READY", "HELD", 30L, null, 4L);
        Equipment equipment = equipment(103L, "D", "PROC", 5L);
        var currentRouteStep = routeStep(30L, 20L);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(routeStepMapper.selectById(30L)).thenReturn(currentRouteStep);
        when(equipmentMapper.countGroupMembership(13L, 103L)).thenReturn(0);
        when(lotMapper.selectCount(any())).thenReturn(1L);

        CommandValidationResultTO result = validator.validate(request);

        assertThat(result.isAllowed()).isFalse();
        assertThat(failedRuleCodes(result))
                .contains("LOT_VERSION_MATCH", "LOT_RELEASED", "EQUIPMENT_UP",
                        "EQUIPMENT_IDLE", "EQUIPMENT_CAPABILITY_MATCH",
                        "EQUIPMENT_NOT_OCCUPIED");
        assertThat(result.getChecks()).hasSize(13);
    }

    @Test
    void shouldMarkDependentRulesNotEvaluatedWhenObjectsDoNotExist() {
        when(lotMapper.selectOne(any())).thenReturn(null);
        when(equipmentMapper.selectOne(any())).thenReturn(null);

        CommandValidationResultTO result = validator.validate(request);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getChecks()).hasSize(13);
        assertThat(result.getChecks().stream().filter(check -> !check.isEvaluated()).count())
                .isEqualTo(9);
        verify(routeStepMapper, never()).selectById(any());
        verify(equipmentMapper, never()).countGroupMembership(any(), any());
        verify(lotMapper, never()).selectCount(any());
    }

    private java.util.List<String> failedRuleCodes(CommandValidationResultTO result) {
        return result.getChecks().stream()
                .filter(RuleCheckResultTO::isEvaluated)
                .filter(check -> !check.isPassed())
                .map(RuleCheckResultTO::getRuleCode)
                .toList();
    }
}