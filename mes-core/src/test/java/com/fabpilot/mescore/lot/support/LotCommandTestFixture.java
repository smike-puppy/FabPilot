package com.fabpilot.mescore.lot.support;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.fabpilot.mescore.equipment.model.Equipment;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.model.RouteStep;

/** Lot 命令测试共用的领域快照 Fixture，减少各测试重复声明相同 Mockito getter。 */
public final class LotCommandTestFixture {
    private LotCommandTestFixture() {
    }

    public static Lot lot(String code, String executionStatus, String holdStatus,
            Long routeStepId, Long equipmentId, long version) {
        Lot lot = mock(Lot.class);
        lenient().when(lot.getId()).thenReturn(16L);
        lenient().when(lot.getCode()).thenReturn(code);
        lenient().when(lot.getRouteId()).thenReturn(1L);
        lenient().when(lot.getExecutionStatus()).thenReturn(executionStatus);
        lenient().when(lot.getHoldStatus()).thenReturn(holdStatus);
        lenient().when(lot.getCurrentRouteStepId()).thenReturn(routeStepId);
        lenient().when(lot.getCurrentEquipmentId()).thenReturn(equipmentId);
        lenient().when(lot.getVersion()).thenReturn(version);
        return lot;
    }

    public static RouteStep routeStep(long id, long operationId) {
        RouteStep routeStep = mock(RouteStep.class);
        lenient().when(routeStep.getId()).thenReturn(id);
        lenient().when(routeStep.getRouteId()).thenReturn(1L);
        lenient().when(routeStep.getOperationId()).thenReturn(operationId);
        lenient().when(routeStep.getRequiredEquipmentGroupId()).thenReturn(13L);
        return routeStep;
    }

    public static Equipment equipment(long id, String upDownStatus,
            String primaryStatus, long version) {
        Equipment equipment = mock(Equipment.class);
        lenient().when(equipment.getId()).thenReturn(id);
        lenient().when(equipment.getUpDownStatus()).thenReturn(upDownStatus);
        lenient().when(equipment.getPrimaryStatus()).thenReturn(primaryStatus);
        lenient().when(equipment.getVersion()).thenReturn(version);
        return equipment;
    }
}