package com.fabpilot.mescore.commandvalidation.enums;

/** MES Core 当前支持执行前预检查的命令类型。 */
public enum CommandType {
    RELEASE,
    TRACK_IN,
    TRACK_OUT,
    HOLD,
    RELEASE_HOLD,
    SCRAP,
    EXECUTE_EQUIPMENT_EVENT,
    ACKNOWLEDGE_ALARM,
    CLOSE_ALARM
}