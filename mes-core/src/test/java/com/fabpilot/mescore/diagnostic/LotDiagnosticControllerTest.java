package com.fabpilot.mescore.diagnostic;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.diagnostic.controller.LotDiagnosticController;
import com.fabpilot.mescore.diagnostic.dto.LotDiagnosticContextTO;
import com.fabpilot.mescore.diagnostic.exception.LotNotFoundException;
import com.fabpilot.mescore.diagnostic.service.LotDiagnosticService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LotDiagnosticController.class)
class LotDiagnosticControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LotDiagnosticService lotDiagnosticService;

    @MockitoBean
    private TraceIdProvider traceIdProvider;

    @Test
    void returnsUnifiedSuccessResponseWithDiagnosticData() throws Exception {
        var context = new LotDiagnosticContextTO(
                new LotDiagnosticContextTO.LotSnapshot(
                        "LOT-013", 20, "RUNNING", "HELD", "HOLD", null, 4L),
                new LotDiagnosticContextTO.WorkOrderSnapshot(
                        "WO-2026-008", "IN_PROGRESS", 100, null),
                null, null, List.of(), List.of(), List.of());
        when(lotDiagnosticService.getDiagnosticContext("LOT-013"))
                .thenReturn(context);
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-TEST");

        mockMvc.perform(get("/api/lots/LOT-013/diagnostic-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.traceId").value("TRACE-TEST"))
                .andExpect(jsonPath("$.data.lot.code").value("LOT-013"))
                .andExpect(jsonPath("$.data.lot.holdStatus").value("HELD"))
                .andExpect(jsonPath("$.data.workOrder.code").value("WO-2026-008"));
    }

    @Test
    void returnsUnifiedFailureResponseWhenLotDoesNotExist() throws Exception {
        when(lotDiagnosticService.getDiagnosticContext("LOT-404"))
                .thenThrow(new LotNotFoundException("LOT-404"));
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-ERROR");

        mockMvc.perform(get("/api/lots/LOT-404/diagnostic-context"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lot not found: LOT-404"))
                .andExpect(jsonPath("$.traceId").value("TRACE-ERROR"));
    }
}
