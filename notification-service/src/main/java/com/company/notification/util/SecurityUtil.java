package com.company.notification.util;

import com.company.notification.config.security.AuthenticatedUser;
import com.company.notification.exception.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Single source of truth for "who is calling me".
 *
 * Service-layer code MUST call SecurityUtil.currentUser() to get the
 * recipient filter — it must NEVER take role/departmentId from the request
 * body or query string. That is the entire RBAC story.
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    public static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("No authenticated user in context");
        }
        return user;
    }
}
