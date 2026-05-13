# Notification Service

Generic, event-driven, RBAC-aware notification microservice.
**One service. One controller. Backend-driven RBAC. Strong fallbacks.**

---

## 1. Architecture at a glance

```
┌────────────────┐  Feign  ┌─────────────────────┐  JPA  ┌────────────┐
│ Producer svc   │────────▶│ Notification Svc    │──────▶│  MySQL/PG  │
│ (vendor, PM,   │  POST   │  • 1 controller     │       └────────────┘
│  finance, ...) │         │  • RBAC in queries  │
└────────────────┘         │  • Resilience4j     │
                           └─────────────────────┘
                                    ▲
                                    │ Bearer JWT
                                    │
                           ┌────────┴────────┐
                           │   Frontend      │
                           │   • Bell poll   │
                           │   • Dropdown    │
                           │   • Mark read   │
                           └─────────────────┘
```

### Core decisions

| Decision | Why |
| --- | --- |
| **One controller** | The notification domain is generic. Per-role controllers leak business context into a service that should know nothing about it. |
| **Producers send recipient role/dept** | The notification service does *not* do user lookup. It stores. Producers know who should see what. |
| **RBAC enforced in JPQL** | Even a service-layer bug cannot leak another user's notifications — the WHERE clause prevents it. |
| **JWT carries `userId, role, departmentId`** | Frontend sends only the token. Backend never trusts query/body for filtering. |
| **`eventType` stored as String, not enum column** | Adding a new event type does not require a DB migration on consumers. |
| **Single composite index `(toRole, toDepartmentId, isRead)`** | Bell-icon polling becomes an O(log n) index seek even with millions of rows. |
| **Resilience4j on every public method** | DB hiccups must not cascade into a broken UI or lost write. |

---

## 2. API

All endpoints are under **`/api/notifications`**. JWT is mandatory.

### 2.1 Create notification (internal, Feign)

`POST /api/notifications`

```json
{
  "eventType": "INVOICE_SUBMITTED",
  "message": "Vendor X submitted invoice #4521",
  "fromService": "vendor-service",
  "fromRole": "VENDOR",
  "fromDepartmentId": 42,
  "toRole": "PROJECT_MANAGER",
  "toDepartmentId": 7,
  "referenceId": "INV-4521",
  "payload": "{\"amount\":15000,\"currency\":\"INR\"}"
}
```
→ `201 Created` with the persisted notification.

### 2.2 Unread count (bell icon, polled every 15–30s)

`GET /api/notifications/unread-count`

```json
{
  "success": true,
  "data": { "count": 3, "degraded": false }
}
```
`degraded: true` ⇒ DB unavailable / circuit open. UI should render `—`.

### 2.3 List notifications (bell dropdown)

`GET /api/notifications?page=0&size=20&eventType=INVOICE_SUBMITTED&fromRole=VENDOR`

Returns a Spring `Page`. Page size is capped at 100 server-side.

### 2.4 Mark as read

`PUT /api/notifications/{id}/read`

Atomic, ownership-checked. Returns 404 (not 403) if the notification belongs to someone else, to avoid leaking existence.

---

## 3. Resilience4j strategy

Every public service method is wrapped with `@CircuitBreaker` + `@Retry`. Reads also use `@Bulkhead` so a slow query cannot starve write threads.

| Operation | Circuit | Fallback behavior |
| --- | --- | --- |
| `create` | `notificationCreate` | **503** with `Retry-After`. Writes never silently succeed. |
| `unreadCount` | `notificationDb` | Returns `{count:0, degraded:true}`. UI renders `—`. |
| `list` | `notificationDb` | Returns empty page. UI shows "Could not load, retry". |
| `markAsRead` | `notificationCreate` | **503**. UI keeps the item unread; retries on next click. |

### Tunable knobs (`application.yml`)

* `slidingWindowSize: 20` — last 20 calls evaluated
* `failureRateThreshold: 50%` — opens circuit at 50% failures
* `slowCallDurationThreshold: 2s` — slow-call detection
* `waitDurationInOpenState: 10s` — open → half-open delay
* Auth/validation errors are listed under `ignoreExceptions` — they don't trip the breaker.

### Observability

```
GET /api/actuator/health
GET /api/actuator/circuitbreakers
GET /api/actuator/circuitbreakerevents
GET /api/actuator/metrics/resilience4j.circuitbreaker.calls
```

The Spring Boot health endpoint shows each breaker's state (`CLOSED` / `OPEN` / `HALF_OPEN`).

---

## 4. Database

```sql
CREATE TABLE notifications (
  id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_type          VARCHAR(64)  NOT NULL,
  message             VARCHAR(1000) NOT NULL,
  from_service        VARCHAR(64),
  from_role           VARCHAR(64),
  from_department_id  BIGINT,
  to_role             VARCHAR(64)  NOT NULL,
  to_department_id    BIGINT,
  reference_id        VARCHAR(128),
  is_read             BOOLEAN      NOT NULL DEFAULT FALSE,
  payload             LONGTEXT,
  created_at          DATETIME(6)  NOT NULL
);

CREATE INDEX idx_recipient_unread  ON notifications (to_role, to_department_id, is_read);
CREATE INDEX idx_recipient_created ON notifications (to_role, to_department_id, created_at);
CREATE INDEX idx_recipient_event   ON notifications (to_role, to_department_id, event_type);
CREATE INDEX idx_reference         ON notifications (reference_id);
```

---

## 5. Producer-side example

```java
// In vendor-service
@FeignClient(name = "notification-service", path = "/api/notifications")
public interface NotificationClient {
    @PostMapping ApiResponse<NotificationResponse> create(@RequestBody NotificationRequest r);
}

@CircuitBreaker(name = "notify", fallbackMethod = "notifyFallback")
public void onInvoiceSubmitted(Invoice inv) {
    notificationClient.create(NotificationRequest.builder()
        .eventType("INVOICE_SUBMITTED")
        .message("Invoice " + inv.id() + " submitted")
        .fromService("vendor-service").fromRole("VENDOR")
        .toRole("PROJECT_MANAGER").toDepartmentId(inv.projectDeptId())
        .referenceId(inv.id())
        .build());
}

private void notifyFallback(Invoice inv, Throwable t) {
    log.warn("Notification dispatch failed; storing in outbox: {}", t.toString());
    outbox.save(new OutboxRow("INVOICE_SUBMITTED", inv));
}
```

The producer is responsible for its OWN circuit breaker — that's how a downed notification service is prevented from breaking invoice submission.

---

## 6. Migration paths (no API breakage)

| Future change | Where to add |
| --- | --- |
| Kafka publish on every create | Implement `NotificationEventPublisher` and call it from `create()`. The default `NoopNotificationEventPublisher` is replaced automatically. |
| WebSocket / SSE push | Same: another `NotificationEventPublisher` impl, opens a channel keyed by `(toRole, toDepartmentId)`. |
| Email / SMS | Add a sink that listens to the same internal event and dispatches. |
| Outbox for guaranteed delivery | Add an `outbox` table; in `createFallback()`, persist there instead of throwing. |
