package com.buildsmart.vendor.security;

import com.buildsmart.vendor.client.IAMServiceClient;
import com.buildsmart.vendor.client.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Central helper that resolves the currently-authenticated user via the JWT
 * filter (which puts the user's email in the SecurityContext) and the IAM
 * service (which exchanges the email for a full {@link UserDto}).
 *
 * Used by Item 4 (vendorId from JWT, not request body) and Item 8 (uploadedBy
 * from JWT, not query param). Centralising it keeps the three controllers from
 * each carrying their own copy of the same logic.
 *
 * IAM fallbacks return a {@code UserDto} with a {@code null} userId, so callers
 * MUST check {@link #isUsable(UserDto)} before trusting the result. When IAM is
 * down the resolver returns the fallback DTO unchanged — caller decides whether
 * that's a hard failure or a degrade.
 */
@Component
public class AuthenticatedUserResolver {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedUserResolver.class);

    private final IAMServiceClient iamServiceClient;

    public AuthenticatedUserResolver(IAMServiceClient iamServiceClient) {
        this.iamServiceClient = iamServiceClient;
    }

    /**
     * Look up the authenticated user via JWT email + IAM. Returns null when no
     * user is authenticated (e.g. permitAll endpoint hit anonymously).
     */
    public UserDto getCurrentUser(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()
                || "anonymousUser".equals(auth.getName())) {
            log.debug("No authenticated user in SecurityContext");
            return null;
        }
        String email = auth.getName();
        String token = request.getHeader("Authorization");
        try {
            UserDto user = iamServiceClient.getUserByEmail(email, token);
            if (user == null) {
                log.warn("IAM returned null UserDto for email={}", email);
            }
            return user;
        } catch (Exception e) {
            log.warn("IAM lookup failed for email={}: {}", email, e.getMessage());
            return null;
        }
    }

    /**
     * Convenience: returns the IAM userId (e.g. BSVMxxx) of the authenticated
     * user. Null when not resolvable — callers should treat this as auth failure.
     */
    public String getCurrentUserId(HttpServletRequest request) {
        UserDto user = getCurrentUser(request);
        return isUsable(user) ? user.userId() : null;
    }

    /**
     * Convenience: returns the IAM display name of the authenticated user.
     * Null when not resolvable.
     */
    public String getCurrentUserName(HttpServletRequest request) {
        UserDto user = getCurrentUser(request);
        return isUsable(user) ? user.name() : null;
    }

    /**
     * A UserDto is "usable" when it has a real userId — IAM fallbacks return
     * a DTO with userId=null which we must not treat as a real identity.
     */
    public boolean isUsable(UserDto user) {
        return user != null && user.userId() != null && !user.userId().isBlank();
    }
}
