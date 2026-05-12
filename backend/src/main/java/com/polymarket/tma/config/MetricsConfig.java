package com.polymarket.tma.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public MeterBinder upstreamErrors() {
        return registry -> {
            registry.counter("polymarket_upstream_errors_total", "upstream", "gamma");
            registry.counter("polymarket_upstream_errors_total", "upstream", "clob");
            registry.counter("polymarket_upstream_errors_total", "upstream", "data");
        };
    }
}
