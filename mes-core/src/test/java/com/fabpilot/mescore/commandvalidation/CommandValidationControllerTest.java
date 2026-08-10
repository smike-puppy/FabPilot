package com.fabpilot.mescore.commandvalidation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabpilot.mescore.commandvalidation.controller.CommandValidationController;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.enums.TargetType;
import com.fabpilot.mescore.commandvalidation.service.CommandValidationService;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommandValidationController.class)
class CommandValidationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandValidationService commandValidationService;

    @MockitoBean
    private TraceIdProvider traceIdProvider;

    @Test
    void shouldReturnUnifiedValidationResult() throws Exception {
        CommandValidationResultTO result = CommandValidationResultTO.builder()
                .allowed(true)
                .commandType(CommandType.TRACK_IN)
                .targetType(TargetType.LOT)
                .targetCode("LOT-100")
                .observedVersion(3L)
                .observedAt(LocalDateTime.of(2026, 8, 10, 19, 0))
                .checks(List.of())
                .build();
        when(commandValidationService.validate(any())).thenReturn(result);
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-VALIDATE");

        mockMvc.perform(post("/api/command-validations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandType": "TRACK_IN",
                                  "targetType": "LOT",
                                  "targetCode": "LOT-100",
                                  "expectedVersion": 3,
                                  "equipmentCode": "ETCH-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("TRACE-VALIDATE"))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.targetCode").value("LOT-100"));
    }

    @Test
    void shouldRejectRequestWithoutTargetCode() throws Exception {
        mockMvc.perform(post("/api/command-validations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandType": "TRACK_IN",
                                  "targetType": "LOT",

                                  "expectedVersion": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}