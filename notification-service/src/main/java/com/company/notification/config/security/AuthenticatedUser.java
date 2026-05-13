package com.company.notification.config.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

/**
 * Lightweight principal extracted from the JWT.
 * Stored inside Spring Security's Authentication object.
 *
 * userId is String to accommodate BuildSmart's role-prefixed IDs (e.g. "BSPM001").
 */
@Getter
@RequiredArgsConstructor
public class AuthenticatedUser implements Serializable {
    private final String userId;   // Changed: IAM issues string IDs, not numeric
    private final String role;
    private final Long departmentId;
}
