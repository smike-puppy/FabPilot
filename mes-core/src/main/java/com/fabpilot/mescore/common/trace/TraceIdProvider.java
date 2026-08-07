package com.fabpilot.mescore.common.trace;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** 为响应、日志和后续审计提供同一个请求追踪标识。 */
@Component
public class TraceIdProvider {
    public static final String MDC_KEY = "traceId";

    public String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString()
                : traceId;
    }
}
