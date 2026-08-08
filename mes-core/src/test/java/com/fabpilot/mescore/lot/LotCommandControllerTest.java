package com.fabpilot.mescore.lot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabpilot.mescore.common.trace.TraceIdProvider;
import com.fabpilot.mescore.lot.controller.LotCommandController;
import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.ReleaseHoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;
import com.fabpilot.mescore.lot.dto.ScrapLotRequestTO;
import com.fabpilot.mescore.lot.service.LotCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LotCommandController.class)
class LotCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LotCommandService lotCommandService;

    @MockitoBean
    private TraceIdProvider traceIdProvider;

    @Test
    void trackInReturnsUnifiedSuccessResponse() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-TRACK-IN");
        when(lotCommandService.trackIn(
                org.mockito.ArgumentMatchers.eq("LOT-014"),
                any(TrackInLotRequestTO.class)))
                .thenReturn(new LotCommandResultTO(
                        "LOT-014",
                        "TRACK_IN",
                        "RUNNING",
                        "RELEASED",
                        3L,
                        false));

        mockMvc.perform(post("/api/lots/LOT-014/track-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "idempotencyKey": "IDEMP-TRACK-IN-001",
                                  "operatorId": "OP-001",
                                  "equipmentCode": "ETCH-02"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.traceId").value("TRACE-TRACK-IN"))
                .andExpect(jsonPath("$.data.transactionType").value("TRACK_IN"))
                .andExpect(jsonPath("$.data.executionStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.idempotent").value(false));
    }

    @Test
    void trackInRejectsBlankEquipmentCodeAsBadRequest() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-VALIDATION");

        mockMvc.perform(post("/api/lots/LOT-014/track-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "idempotencyKey": "IDEMP-TRACK-IN-002",
                                  "operatorId": "OP-001",
                                  "equipmentCode": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("equipmentCode 不能为空"))
                .andExpect(jsonPath("$.traceId").value("TRACE-VALIDATION"));

        verifyNoInteractions(lotCommandService);
    }

    @Test
    void trackOutReturnsUnifiedSuccessResponse() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-TRACK-OUT");
        when(lotCommandService.trackOut(
                org.mockito.ArgumentMatchers.eq("LOT-016"),
                any(TrackOutLotRequestTO.class)))
                .thenReturn(new LotCommandResultTO(
                        "LOT-016",
                        "TRACK_OUT",
                        "READY",
                        "RELEASED",
                        2L,
                        false));

        mockMvc.perform(post("/api/lots/LOT-016/track-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "idempotencyKey": "IDEMP-TRACK-OUT-001",
                                  "operatorId": "OP-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.traceId").value("TRACE-TRACK-OUT"))
                .andExpect(jsonPath("$.data.transactionType").value("TRACK_OUT"))
                .andExpect(jsonPath("$.data.executionStatus").value("READY"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.idempotent").value(false));
    }

    @Test
    void holdReturnsUnifiedSuccessResponse() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-HOLD");
        when(lotCommandService.hold(
                org.mockito.ArgumentMatchers.eq("LOT-016"),
                any(HoldLotRequestTO.class)))
                .thenReturn(new LotCommandResultTO(
                        "LOT-016", "HOLD", "READY", "HELD", 3L, false));

        mockMvc.perform(post("/api/lots/LOT-016/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "idempotencyKey": "POSTMAN-LOT-016-HOLD-001",
                                  "operatorId": "POSTMAN-USER",
                                  "reasonCode": "QUALITY_CHECK",
                                  "reasonText": "检测工序前需要复核质量数据"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("TRACE-HOLD"))
                .andExpect(jsonPath("$.data.transactionType").value("HOLD"))
                .andExpect(jsonPath("$.data.executionStatus").value("READY"))
                .andExpect(jsonPath("$.data.holdStatus").value("HELD"))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void holdRejectsBlankReasonAsBadRequest() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-HOLD-VALIDATION");

        mockMvc.perform(post("/api/lots/LOT-016/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "idempotencyKey": "POSTMAN-LOT-016-HOLD-002",
                                  "operatorId": "POSTMAN-USER",
                                  "reasonCode": "",
                                  "reasonText": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").value("TRACE-HOLD-VALIDATION"));
    }

    @Test
    void releaseHoldReturnsUnifiedSuccessResponse() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-RELEASE-HOLD");
        when(lotCommandService.releaseHold(
                org.mockito.ArgumentMatchers.eq("LOT-016"), any(ReleaseHoldLotRequestTO.class)))
                .thenReturn(new LotCommandResultTO(
                        "LOT-016", "RELEASE_HOLD", "READY", "RELEASED", 4L, false));

        mockMvc.perform(post("/api/lots/LOT-016/release-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 3,
                                  "idempotencyKey": "POSTMAN-LOT-016-RELEASE-HOLD-001",
                                  "operatorId": "POSTMAN-USER",
                                  "reasonCode": "QUALITY_CHECK_RESOLVED",
                                  "reasonText": "质量数据复核完成，允许继续检测"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("TRACE-RELEASE-HOLD"))
                .andExpect(jsonPath("$.data.transactionType").value("RELEASE_HOLD"))
                .andExpect(jsonPath("$.data.executionStatus").value("READY"))
                .andExpect(jsonPath("$.data.holdStatus").value("RELEASED"))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    @Test
    void releaseHoldRejectsBlankReasonAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/lots/LOT-016/release-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 3,
                                  "idempotencyKey": "POSTMAN-LOT-016-RELEASE-HOLD-002",
                                  "operatorId": "POSTMAN-USER",
                                  "reasonCode": "",
                                  "reasonText": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lotCommandService);
    }
    @Test
    void scrapReturnsUnifiedSuccessResponse() throws Exception {
        when(traceIdProvider.currentTraceId()).thenReturn("TRACE-SCRAP");
        when(lotCommandService.scrap(
                org.mockito.ArgumentMatchers.eq("LOT-016"), any(ScrapLotRequestTO.class)))
                .thenReturn(new LotCommandResultTO(
                        "LOT-016", "SCRAP", "SCRAPPED", "RELEASED", 5L, false));
        mockMvc.perform(post("/api/lots/LOT-016/scrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 4,
                                  "idempotencyKey": "POSTMAN-LOT-016-SCRAP-001",
                                  "operatorId": "POSTMAN-USER",
                                  "reasonCode": "INSPECTION_REJECT",
                                  "reasonText": "检测结果超出规格，确认报废"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("TRACE-SCRAP"))
                .andExpect(jsonPath("$.data.transactionType").value("SCRAP"))
                .andExpect(jsonPath("$.data.executionStatus").value("SCRAPPED"))
                .andExpect(jsonPath("$.data.holdStatus").value("RELEASED"))
                .andExpect(jsonPath("$.data.version").value(5));
    }

    @Test
    void scrapRejectsBlankReasonAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/lots/LOT-016/scrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 4,
                                  "idempotencyKey": "POSTMAN-LOT-016-SCRAP-002",
                                  "operatorId": "POSTMAN-USER",
                                  "reasonCode": "",
                                  "reasonText": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lotCommandService);
    }}