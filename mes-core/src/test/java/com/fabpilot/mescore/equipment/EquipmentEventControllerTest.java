package com.fabpilot.mescore.equipment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.equipment.controller.EquipmentEventController;
import com.fabpilot.mescore.equipment.dto.EquipmentEventResultTO;
import com.fabpilot.mescore.equipment.dto.ExecuteEquipmentEventRequestTO;
import com.fabpilot.mescore.equipment.service.EquipmentEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EquipmentEventController.class)
class EquipmentEventControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private EquipmentEventService equipmentEventService;
    @MockitoBean private TraceIdProvider traceIdProvider;

    @Test
    void executeEventReturnsUnifiedSuccessResponse() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-EQP-FAULT");
        when(equipmentEventService.executeEvent(any(ExecuteEquipmentEventRequestTO.class)))
                .thenReturn(new EquipmentEventResultTO(
                        "ETCH-02", "VACUUM_LOW", "D", "DOWN", 2L, false));

        mockMvc.perform(post("/api/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentCode": "ETCH-02",
                                  "eventCode": "VACUUM_LOW",
                                  "expectedVersion": 1,
                                  "idempotencyKey": "IDEMP-EQP-FAULT-001",
                                  "operatorId": "EQP-GATEWAY",
                                  "operatorType": "SYSTEM",
                                  "reasonCode": "VACUUM_LOW",
                                  "reasonText": "真空值低于生产下限"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("TRACE-EQP-FAULT"))
                .andExpect(jsonPath("$.data.equipmentCode").value("ETCH-02"))
                .andExpect(jsonPath("$.data.eventCode").value("VACUUM_LOW"))
                .andExpect(jsonPath("$.data.upDownStatus").value("D"))
                .andExpect(jsonPath("$.data.primaryStatus").value("DOWN"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.idempotent").value(false));
    }

    @Test
    void executeEventRejectsBlankBusinessFields() throws Exception {
        mockMvc.perform(post("/api/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentCode": "",
                                  "eventCode": "",
                                  "expectedVersion": 1,
                                  "idempotencyKey": "IDEMP-EQP-FAULT-002",
                                  "operatorId": "EQP-GATEWAY",
                                  "operatorType": "SYSTEM",
                                  "reasonCode": "",
                                  "reasonText": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(equipmentEventService);
    }
}