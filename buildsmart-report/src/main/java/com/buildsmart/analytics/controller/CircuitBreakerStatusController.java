package com.buildsmart.analytics.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoint exposing the live state of all Resilience4j circuit breakers.
 *
 * GET /api/circuit-breakers           → summary of all instances
 * GET /api/circuit-breakers/{name}    → detail for a specific instance
 * POST /api/circuit-breakers/{name}/reset → reset (transition to CLOSED)
 */
@RestController
@RequestMapping(path = "/api/circuit-breakers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Circuit Breaker", description = "Live circuit breaker status and management")
@PreAuthorize("hasRole('ADMIN')")
public class CircuitBreakerStatusController {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerStatusController(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "List all circuit breakers and their current state")
    public Map<String, Object> getAllCircuitBreakers() {
        List<Map<String, Object>> breakers = registry.getAllCircuitBreakers()
                .stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toSummary)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("totalInstances", breakers.size());
        response.put("circuitBreakers", breakers);
        return response;
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get detailed metrics for a single circuit breaker")
    public Map<String, Object> getCircuitBreaker(@PathVariable String name) {
        CircuitBreaker cb = registry.find(name)
                .orElseThrow(() -> new IllegalArgumentException("Circuit breaker not found: " + name));
        return toDetail(cb);
    }

    @PostMapping("/{name}/reset")
    @Operation(summary = "Force-reset a circuit breaker to CLOSED state")
    public Map<String, Object> resetCircuitBreaker(@PathVariable String name) {
        CircuitBreaker cb = registry.find(name)
                .orElseThrow(() -> new IllegalArgumentException("Circuit breaker not found: " + name));
        cb.transitionToClosedState();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("name", name);
        result.put("message", "Circuit breaker reset to CLOSED state");
        result.put("state", cb.getState().toString());
        return result;
    }

    /* ── Helpers ──────────────────────────────────────────────────────────── */

    private Map<String, Object> toSummary(CircuitBreaker cb) {
        CircuitBreaker.Metrics m = cb.getMetrics();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", cb.getName());
        map.put("state", cb.getState().toString());
        map.put("failureRatePercent",
                m.getFailureRate() < 0 ? "N/A" : String.format("%.1f%%", m.getFailureRate()));
        map.put("slowCallRatePercent",
                m.getSlowCallRate() < 0 ? "N/A" : String.format("%.1f%%", m.getSlowCallRate()));
        map.put("bufferedCalls", m.getNumberOfBufferedCalls());
        map.put("successfulCalls", m.getNumberOfSuccessfulCalls());
        map.put("failedCalls", m.getNumberOfFailedCalls());
        return map;
    }

    private Map<String, Object> toDetail(CircuitBreaker cb) {
        CircuitBreaker.Metrics m = cb.getMetrics();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", LocalDateTime.now().toString());
        map.put("name", cb.getName());
        map.put("state", cb.getState().toString());
        map.put("failureRateThreshold", cb.getCircuitBreakerConfig().getFailureRateThreshold() + "%");
        map.put("slowCallRateThreshold", cb.getCircuitBreakerConfig().getSlowCallRateThreshold() + "%");
        map.put("slidingWindowSize", cb.getCircuitBreakerConfig().getSlidingWindowSize());
        map.put("metrics", Map.of(
                "bufferedCalls",       m.getNumberOfBufferedCalls(),
                "failedCalls",         m.getNumberOfFailedCalls(),
                "successfulCalls",     m.getNumberOfSuccessfulCalls(),
                "notPermittedCalls",   m.getNumberOfNotPermittedCalls(),
                "slowCalls",           m.getNumberOfSlowCalls(),
                "slowFailedCalls",     m.getNumberOfSlowFailedCalls(),
                "failureRatePercent",  m.getFailureRate() < 0 ? "N/A" : String.format("%.1f%%", m.getFailureRate()),
                "slowCallRatePercent", m.getSlowCallRate() < 0 ? "N/A" : String.format("%.1f%%", m.getSlowCallRate())
        ));
        return map;
    }
}

