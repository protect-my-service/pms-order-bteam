package com.pms.order.global.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "order.cancel")
@Validated
@Getter
@Setter
public class OrderCancelPolicyProperties {

    @Min(0)
    private long allowedHours = 1;

    private boolean allowPartial = true;

    @Min(1)
    @Max(200)
    private int maxItemsPerRequest = 50;

    public Duration window() {
        return Duration.ofHours(allowedHours);
    }
}
