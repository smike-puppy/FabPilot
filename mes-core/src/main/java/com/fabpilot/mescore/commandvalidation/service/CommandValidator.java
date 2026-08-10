package com.fabpilot.mescore.commandvalidation.service;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.enums.CommandType;

/** 单个领域命令的只读校验器；公共服务只按 commandType 路由，不保存具体业务规则。 */
public interface CommandValidator {
    CommandType supportedCommandType();

    CommandValidationResultTO validate(CommandValidationRequestTO request);
}