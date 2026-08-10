package com.fabpilot.mescore.commandvalidation.controller;

import com.fabpilot.mescore.commandvalidation.dto.CommandValidationRequestTO;
import com.fabpilot.mescore.commandvalidation.dto.CommandValidationResultTO;
import com.fabpilot.mescore.commandvalidation.service.CommandValidationService;
import com.fabpilot.mescore.common.api.ApiResponse;
import com.fabpilot.mescore.common.trace.TraceIdProvider;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 页面和 Agent 共用的命令执行前只读预检查入口。 */
@RestController
@RequestMapping("/api/command-validations")
public class CommandValidationController {
    @Autowired
    private CommandValidationService commandValidationService;

    @Autowired
    private TraceIdProvider traceIdProvider;

    /** 只返回当前快照下的规则结论；不会执行命令，也不能代替正式命令的再次校验。 */
    @PostMapping
    public ApiResponse<CommandValidationResultTO> validate(
            @Valid @RequestBody CommandValidationRequestTO request) {
        return ApiResponse.success(
                commandValidationService.validate(request),
                traceIdProvider.currentTraceId());
    }
}