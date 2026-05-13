package com.company.notification.enums;

/**
 * Generic event types. The notification service does NOT know the business
 * meaning — it only stores and routes. Add new types here without changing
 * any business logic in this service.
 *
 * Stored as a String in the DB (length 64) for forward compatibility:
 * a producer can send a new event type and old consumers won't break.
 */
public enum EventType {
    // ── Tasks ────────────────────────────────────────────────────
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_SUBMITTED,
    TASK_COMPLETED,
    TASK_REJECTED,
    WORK_SUBMITTED,

    // ── Approvals ────────────────────────────────────────────────
    APPROVAL_REQUIRED,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,

    // ── Finance ──────────────────────────────────────────────────
    BUDGET_SUBMITTED,
    BUDGET_APPROVED,
    BUDGET_REJECTED,
    EXPENSE_SUBMITTED,
    EXPENSE_APPROVED,
    EXPENSE_REJECTED,
    PAYMENT_CREATED,
    PAYMENT_RELEASED,

    // ── Vendor ───────────────────────────────────────────────────
    CONTRACT_UPLOADED,
    INVOICE_CREATED,
    INVOICE_SUBMITTED,
    INVOICE_APPROVED,
    INVOICE_REJECTED,
    DOCUMENT_SUBMITTED,
    DELIVERY_CREATED,
    DELIVERY_DISPATCHED,
    DELIVERY_CONFIRMED,

    // ── SiteOps ──────────────────────────────────────────────────
    SITE_LOG_SUBMITTED,
    ISSUE_REPORTED,
    ISSUE_UPDATED,

    // ── Safety ───────────────────────────────────────────────────
    INCIDENT_REPORTED,
    INCIDENT_STATUS_CHANGED,
    INSPECTION_SCHEDULED,
    INSPECTION_STATUS_CHANGED,

    // ── Resource Allocation ──────────────────────────────────────
    RESOURCE_NOTIFICATION,

    // ── Project Manager ──────────────────────────────────────────
    VENDOR_NOTIFICATION,

    // ── Fallback ─────────────────────────────────────────────────
    GENERIC
}