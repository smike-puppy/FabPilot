package com.fabpilot.mescore.commandvalidation.service.impl;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.service.CommandValidationService;
import com.fabpilot.mescore.commandvalidation.service.CommandValidator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 按命令类型选择领域 Validator；新增命令时无需修改现有 Validator。 */
@Service
public class CommandValidationServiceImpl implements CommandValidationService {
    @Autowired
    private List<CommandValidator> validators;

    @Override
    public CommandValidationResultTO validate(CommandValidationRequestTO request) {
        return validators.stream()
                .filter(validator -> validator.supportedCommandType() == request.getCommandType())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported command type: " + request.getCommandType()))
                .validate(request);
    }
}