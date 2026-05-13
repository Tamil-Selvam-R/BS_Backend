package com.company.notification.client;

import com.company.notification.dto.ApiResponse;
import com.company.notification.dto.NotificationRequest;
import com.company.notification.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client other microservices use to PRODUCE notifications.
 *
 * Place this interface (or an equivalent) in each producer service —
 * Project Manager, Vendor, Finance, Site Engineer, etc. The url is
 * resolved via service discovery (Eureka / Consul / k8s DNS) using the
 * service name "notification-service".
 *
 * <h3>Producer-side usage example</h3>
 * <pre>
 *   notificationClient.create(NotificationRequest.builder()
 *       .eventType("INVOICE_SUBMITTED")
 *       .message("Invoice #4521 submitted by Vendor X")
 *       .fromService("vendor-service")
 *       .fromRole("VENDOR")
 *       .fromDepartmentId(42L)
 *       .toRole("PROJECT_MANAGER")
 *       .toDepartmentId(7L)
 *       .referenceId("INV-4521")
 *       .build());
 * </pre>
 *
 * <h3>Resilience on the producer side</h3>
 * The producer should also wrap this call with @CircuitBreaker so the
 * notification service being down does NOT propagate failure into the
 * producer's own business flow. A producer-side fallback should at minimum
 * log + (ideally) push the failed request to a local outbox.
 */
@FeignClient(
        name = "notification-service",
        path = "/api/notifications",
        contextId = "notificationFeignClient"
)
public interface NotificationFeignClient {

    @PostMapping
    ApiResponse<NotificationResponse> create(
            @RequestBody NotificationRequest request);
}
