package com.fabpilot.mescore.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个 HTTP 请求建立 traceId，并同时写入 MDC 和响应头。
 *
 * <p>调用方可以传入合法的 X-Trace-Id 贯穿多服务；非法或缺失时由 MES Core 生成。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_TRACE_ID =
            Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
        MDC.put(TraceIdProvider.MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Web 容器会复用线程，请求结束必须清理，防止 traceId 串到下一次请求。
            MDC.remove(TraceIdProvider.MDC_KEY);
        }
    }

    private String resolveTraceId(String candidate) {
        return candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}
