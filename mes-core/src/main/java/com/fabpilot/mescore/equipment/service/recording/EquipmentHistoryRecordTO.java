package com.fabpilot.mescore.equipment.service.recording;

import com.fabpilot.mescore.common.command.dto.VersionedCommandRequestTO;
import com.fabpilot.mescore.equipment.model.Equipment;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/** 一条 EquipmentHistory 的事件差异上下文。 */
@Data
@Builder
public class EquipmentHistoryRecordTO {
    private Equipment equipment;
    private String eventCode;
    private String primaryStatusAfter;
    private VersionedCommandRequestTO request;
    private long nextVersion;
    private LocalDateTime occurredAt;
}