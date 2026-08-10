package com.fabpilot.mescore.equipment.service.recording;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import com.fabpilot.mescore.equipment.model.Equipment;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 一条 EquipmentHistory 所需的业务差异上下文。
 * 公共映射由工厂维护，调用方只声明本次事件真正变化的状态、来源和原因。
 */
@Data
@Builder
public class EquipmentHistoryRecordTO {
    private Equipment equipment;
    private String eventCode;
    private String upDownStatusAfter;
    private String primaryStatusAfter;
    private String operatorType;
    private String operatorRole;
    private String reasonCode;
    private String reasonText;
    private VersionedCommandRequestTO request;
    private long nextVersion;
    private LocalDateTime occurredAt;
}