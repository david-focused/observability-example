package com.example.inventoryservice.config

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.GlobalOpenTelemetry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenTelemetryConfig {

    @Bean
    fun tracer(): Tracer {
        return GlobalOpenTelemetry.get().getTracer("inventory-service")
    }

} 