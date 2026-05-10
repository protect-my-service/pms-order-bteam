package com.pms.order.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final String MDC_TRACE_ID = "traceId";
    private final AccessLogWriter writer;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        Instant start = Instant.now();
        String traceId = resolveTraceId(req);
        MDC.put(MDC_TRACE_ID, traceId);

        ContentCachingRequestWrapper  reqW = new ContentCachingRequestWrapper(req);
        ContentCachingResponseWrapper resW = new ContentCachingResponseWrapper(res);

        try {
            chain.doFilter(reqW, resW);
        } finally {
            Instant end = Instant.now();
            try {
                writeAccessLog(reqW, resW, traceId, start, end);
            } catch (Throwable t) {
                log.warn("access log write failed: {}", t.getMessage(), t);
            } finally {
                resW.copyBodyToResponse();
                MDC.remove(MDC_TRACE_ID);
            }
        }
    }

    private void writeAccessLog(ContentCachingRequestWrapper reqW,
                                ContentCachingResponseWrapper resW,
                                String traceId,
                                Instant start,
                                Instant end) {
        byte[] reqBytes = reqW.getContentAsByteArray();
        byte[] resBytes = resW.getContentAsByteArray();

        AccessLogPayload payload = new AccessLogPayload(
                traceId,
                reqW.getRequestURI(),
                reqW.getQueryString(),
                reqW.getMethod(),
                resolveIp(reqW),
                resW.getStatus(),
                Duration.between(start, end).toMillis(),
                start,
                end,
                writer.buildBodyField(reqBytes, reqW.getContentType()),
                writer.buildBodyField(resBytes, resW.getContentType()),
                reqBytes.length == 0 ? null : reqBytes.length,
                resBytes.length == 0 ? null : resBytes.length
        );

        writer.write(payload);
    }

    private static String resolveIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    private static String resolveTraceId(HttpServletRequest req) {
        String tp = req.getHeader("traceparent");
        if (tp != null) {
            String[] parts = tp.split("-");
            if (parts.length >= 2 && parts[1].length() == 32) return parts[1];
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
