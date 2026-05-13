package com.buildsmart.analytics.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Resilience4j Circuit Breaker configuration for the Analytics service.
 *
 * Each downstream Feign client (Project, Safety, Finance, Resource, Vendor,
 * SiteEngineer, IAM) gets its own named circuit-breaker instance that:
 *  - Opens after 50 % failure rate over a 10-call sliding window
 *  - Waits 30 s in OPEN state before transitioning to HALF-OPEN
 *  - Allows 3 test calls in HALF-OPEN before deciding to close/stay open
 *  - Times out any single downstream call after 5 s (via TimeLimiter in properties)
 *
 * All state transitions and errors are logged at WARN level so they are
 * visible in the application log without adding noise at DEBUG level.
 */
@Configuration
public class CircuitBreakerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfiguration.class);

    /** Downstream service names — each gets its own circuit-breaker instance. */
    private static final List<String> SERVICE_NAMES = List.of(
            "ProjectServiceClient",
            "SafetyServiceClient",
            "FinanceServiceClient",
            "ResourceServiceClient",
            "VendorServiceClient",
            "SiteEngineerServiceClient",
            "IamServiceClient"
    );

    /**
     * Registers event listeners on every named circuit-breaker instance
     * so that state transitions and ignored/recorded errors are logged.
     *
     * Spring Cloud OpenFeign's circuit-breaker integration creates instances
     * lazily using the Feign client name, so we register listeners right after
     * the registry bean is available and also add a global creation listener to
     * catch any instance created afterwards.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {

        /* ── Shared circuit-breaker configuration ──────────────────────────── */
        CircuitBreakerConfig sharedConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                       // open after 50 % failures
                .slowCallRateThreshold(80)                      // open if 80 % calls are slow
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(Exception.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(sharedConfig);

        /* ── Pre-register named instances and attach event listeners ───────── */
        SERVICE_NAMES.forEach(name -> attachListeners(registry.circuitBreaker(name)));

        /* ── Catch any future instances created by Feign lazily ────────────── */
        registry.getEventPublisher()
                .onEntryAdded(event -> attachListeners(event.getAddedEntry()));

        return registry;
    }

    /* ── Helper: attach event listeners to a single CB instance ──────────── */
    private void attachListeners(CircuitBreaker cb) {
        String name = cb.getName();

        cb.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[CircuitBreaker][{}] State transition: {} → {}",
                        name,
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))

                .onFailureRateExceeded(event -> log.warn(
                        "[CircuitBreaker][{}] Failure rate exceeded: {}%",
                        name, String.format("%.1f", event.getFailureRate())))

                .onSlowCallRateExceeded(event -> log.warn(
                        "[CircuitBreaker][{}] Slow-call rate exceeded: {}%",
                        name, String.format("%.1f", event.getSlowCallRate())))

                .onCallNotPermitted(event -> log.warn(
                        "[CircuitBreaker][{}] Call NOT permitted — circuit is OPEN", name))

                .onError(event -> log.warn(
                        "[CircuitBreaker][{}] Call failed after {}ms: {}",
                        name,
                        event.getElapsedDuration().toMillis(),
                        event.getThrowable().getMessage()))

                .onSuccess(event -> log.debug(
                        "[CircuitBreaker][{}] Call succeeded in {}ms",
                        name, event.getElapsedDuration().toMillis()));
    }
}

