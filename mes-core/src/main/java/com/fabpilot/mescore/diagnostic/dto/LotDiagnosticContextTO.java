package com.fabpilot.mescore.diagnostic.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 诊断模块对外提供的强类型业务数据。
 *
 * <p>该 DTO 只描述诊断内容；success、code、message 和 traceId 等通用字段由
 * {@code ApiResponse} 统一包装。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LotDiagnosticContextTO {
    private LotSnapshot lot;
    private WorkOrderSnapshot workOrder;
    private StepSnapshot currentStep;
    private EquipmentSnapshot currentEquipment;
    private List<LotHistoryItem> recentLotTransactions;
    private List<EquipmentHistoryItem> recentEquipmentEvents;
    private List<AlarmSnapshot> activeAlarms;

    /** Lot 当前状态快照。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LotSnapshot {
        private String code;
        private int quantity;
        private String executionStatus;
        private String holdStatus;
        private String lastTransactionCode;
        private LocalDateTime lastTransactionAt;
        private Long version;
    }

    /** 工单摘要快照。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkOrderSnapshot {
        private String code;
        private String status;
        private int planQuantity;
        private LocalDateTime dueAt;
    }

    /** Lot 当前工步与工序快照。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepSnapshot {
        private String stepCode;
        private String name;
        private int sequenceNo;
        private String operationCode;
        private String operationName;
        private Long requiredEquipmentGroupId;
    }

    /** Lot 当前设备状态快照。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentSnapshot {
        private String code;
        private String name;
        private String equipmentType;
        private String upDownStatus;
        private String primaryStatus;
        private String lastEventCode;
        private LocalDateTime lastEventAt;
        private Long version;
    }

    /** Lot 最近一条操作履历的对外表示。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LotHistoryItem {
        private String transactionType;
        private String executionStatusBefore;
        private String executionStatusAfter;
        private String holdStatusBefore;
        private String holdStatusAfter;
        private String operatorId;
        private String reasonCode;
        private String reasonText;
        private LocalDateTime occurredAt;
    }

    /** 设备最近一条事件履历的对外表示。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentHistoryItem {
        private String eventCode;
        private String upDownStatusBefore;
        private String upDownStatusAfter;
        private String primaryStatusBefore;
        private String primaryStatusAfter;
        private String operatorId;
        private String reasonCode;
        private String reasonText;
        private LocalDateTime occurredAt;
    }

    /**
     * 当前设备尚未关闭的告警快照。
     *
     * <p>持续时间由查询时刻减去开启时间得到，帮助 Agent 判断异常是否已经长时间未处理。</p>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmSnapshot {
        private Long id;
        private String alarmCode;
        private String severity;
        private String status;
        private String sourceEventCode;
        private String message;
        private LocalDateTime openedAt;
        private String acknowledgedBy;
        private LocalDateTime acknowledgedAt;
        private long openDurationSeconds;
        private Long version;
    }}
