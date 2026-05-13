package com.buildsmart.siteops.enums;

/**
 * Lifecycle states for a site issue.
 * OPEN       - Newly reported, not yet assigned.
 * IN_PROGRESS - Being worked on.
 * RESOLVED   - Fix applied; pending verification.
 * CLOSED     - Verified and closed by PM or Site Engineer.
 * ESCALATED  - Escalated to Project Manager / Safety Officer.
 */
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    ESCALATED
}
