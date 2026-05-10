package com.pms.order.global.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "app.access-log")
public record AccessLogProperties(
        boolean enabled,
        int maxBodyBytes,
        Set<String> redactKeys
) {
    public AccessLogProperties {
        if (maxBodyBytes <= 0) maxBodyBytes = 51_200;
        if (redactKeys == null) redactKeys = Set.of(
                "password", "accessToken", "refreshToken", "cardNumber", "cvc"
        );
    }
}
