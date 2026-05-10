package com.pms.order.global.logging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class HttpAccessLogConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.access-log", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<HttpAccessLogFilter> httpAccessLogFilterRegistration(
            HttpAccessLogFilter filter) {
        FilterRegistrationBean<HttpAccessLogFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("httpAccessLogFilter");
        return reg;
    }
}
