package com.fabpilot.mescore.alarm.dto;
import lombok.*;
@Data @AllArgsConstructor
public class AlarmActionResultTO {
    private Long alarmId;
    private String status;
    private Long version;
    private boolean idempotent;
}