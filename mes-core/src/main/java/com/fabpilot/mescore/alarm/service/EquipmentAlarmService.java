package com.fabpilot.mescore.alarm.service; import com.fabpilot.mescore.alarm.dto.*;
public interface EquipmentAlarmService { AlarmActionResultTO executeAction(AlarmActionRequestTO request); }