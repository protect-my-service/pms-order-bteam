package com.pms.order.global.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AccessLogWriter {

    private static final Logger ACCESS = LoggerFactory.getLogger("http.access");
    private final ObjectMapper objectMapper;
    private final AccessLogProperties props;

    public void write(AccessLogPayload payload) {
        Map<String, Object> map = toMap(payload);
        ACCESS.info("", StructuredArguments.entries(map));
    }

    public Object buildBodyField(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) return null;

        if (isBinary(contentType)) {
            return "<binary omitted, " + bytes.length + " bytes>";
        }

        boolean truncated = bytes.length > props.maxBodyBytes();
        byte[] effective = truncated
                ? java.util.Arrays.copyOf(bytes, props.maxBodyBytes())
                : bytes;
        String text = new String(effective, StandardCharsets.UTF_8);

        if (isJson(contentType)) {
            try {
                JsonNode node = objectMapper.readTree(text);
                redact(node, props.redactKeys());
                return truncated ? wrapTruncated(node, bytes.length) : node;
            } catch (Exception ignore) {
                // JSON 파싱 실패 시 raw 문자열로 fallback
            }
        }
        return truncated ? text + "<truncated, original=" + bytes.length + "bytes>" : text;
    }

    private Object wrapTruncated(JsonNode node, int original) {
        ObjectNode wrap = objectMapper.createObjectNode();
        wrap.set("_truncated", node);
        wrap.put("_originalBytes", original);
        return wrap;
    }

    private static boolean isJson(String ct) {
        if (ct == null) return false;
        String lower = ct.toLowerCase();
        return lower.startsWith("application/json")
                || (lower.startsWith("application/") && lower.contains("+json"));
    }

    private static boolean isBinary(String ct) {
        if (ct == null) return false;
        String lower = ct.toLowerCase();
        return lower.startsWith("multipart/")
                || lower.startsWith("image/")
                || lower.startsWith("audio/")
                || lower.startsWith("video/")
                || lower.startsWith("application/octet-stream")
                || lower.startsWith("application/pdf");
    }

    private static void redact(JsonNode node, Set<String> keys) {
        if (node == null || node.isValueNode()) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> names = obj.fieldNames();
            java.util.List<String> snapshot = new java.util.ArrayList<>();
            while (names.hasNext()) snapshot.add(names.next());
            for (String name : snapshot) {
                if (keys.contains(name)) {
                    obj.put(name, "***");
                } else {
                    redact(obj.get(name), keys);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> redact(child, keys));
        }
    }

    private Map<String, Object> toMap(AccessLogPayload p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId",           p.traceId());
        m.put("path",              p.path());
        m.put("queryString",       p.queryString());
        m.put("method",            p.method());
        m.put("ip",                p.ip());
        m.put("status",            p.status());
        m.put("durationMs",        p.durationMs());
        m.put("requestTimestamp",  p.requestTimestamp() == null ? null : p.requestTimestamp().toString());
        m.put("responseTimestamp", p.responseTimestamp() == null ? null : p.responseTimestamp().toString());
        m.put("request",           p.request());
        m.put("response",          p.response());
        m.put("requestBytes",      p.requestBytes());
        m.put("responseBytes",     p.responseBytes());
        Map<String, Object> clean = new HashMap<>();
        m.forEach((k, v) -> { if (v != null) clean.put(k, v); });
        return clean;
    }
}
