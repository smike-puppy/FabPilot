package com.fabpilot.mescore.lot.service.recording;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import com.fabpilot.mescore.lot.enums.LotTransactionType;
import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.model.RouteStep;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 一条 LotTransaction 所需的业务差异上下文。
 * 公共审计字段由 LotTransactionFactory 统一填充，各命令只声明前后状态和工艺资源差异。
 */
@Data
@Builder
public class LotTransactionRecordTO {
    private Lot lot;
    private LotTransactionType transactionType;
    private RouteStep routeStep;
    private Long equipmentId;
    private String executionStatusAfter;
    private String holdStatusAfter;
    private String reasonCode;
    private String reasonText;
    private VersionedCommandRequestTO request;
    private long nextVersion;
    private LocalDateTime occurredAt;
}