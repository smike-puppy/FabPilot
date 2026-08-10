package com.fabpilot.mescore.commandvalidation.service.impl;

import com.fabpilot.mescore.alarm.enums.AlarmAction;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;
import com.fabpilot.mescore.commandvalidation.service.CommandValidator;
import org.springframework.stereotype.Component;

/** ACKNOWLEDGED 告警关闭和设备恢复的只读预检查器。 */
@Component
public class CloseAlarmCommandValidator
        extends AbstractAlarmActionCommandValidator implements CommandValidator {
    @Override
    public CommandType supportedCommandType() {
        return CommandType.CLOSE_ALARM;
    }

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        return validateAction(request, AlarmAction.CLOSE);
    }
}