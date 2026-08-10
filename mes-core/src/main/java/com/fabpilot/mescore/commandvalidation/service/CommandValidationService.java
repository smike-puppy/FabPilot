package com.fabpilot.mescore.commandvalidation.service;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;

/** 统一命令预检查入口。 */
public interface CommandValidationService {
    CommandValidationResultTO validate(CommandValidationRequestTO request);
}