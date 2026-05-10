package com.pms.order.global.logging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessLogPayload(
        String  traceId,
        String  path,
        String  queryString,
        String  method,
        String  ip,
        Integer status,
        Long    durationMs,
        Instant requestTimestamp,
        Instant responseTimestamp,
        Object  request,
        Object  response,
        Integer requestBytes,
        Integer responseBytes
) { }
