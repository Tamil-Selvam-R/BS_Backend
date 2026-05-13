package com.buildsmart.siteops.security;

import com.buildsmart.siteops.client.IAMServiceClient;
import com.buildsmart.siteops.client.dto.IAMApiResponse;
import com.buildsmart.siteops.client.dto.UserDto;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates that a JWT token holder still exists and is ACTIVE in IAM.
 *
 * WHY THIS EXISTS:
 * ─────────────────
 * A JWT is self-contained and cryptographically signed. Once issued, the
 * signature remains valid until expiry (24 hours). If an admin deletes a user
 * from the IAM database, the deleted user's token is still valid by signature
 * alone — they could keep accessing siteops endpoints for up to 24 hours.
 *
 * This service solves that by contacting IAM on each request to verify the
 * user is still present and ACTIVE.
 *
 * HOW THE CACHE WORKS:
 * ──────────────────────
 * Calling IAM on every request adds latency. Instead, after a successful
 * IAM validation, we store userId → expiry timestamp in memory for
 * `app.security.user-validation-cache-minutes` (default 5 minutes).
 * Within those 5 minutes, subsequent requests skip the IAM call.
 *
 * This means a deleted user can still get through for up to 5 minutes — an
 * acceptable tradeoff. The window can be reduced to 1 minute for stricter
 * environments.
 *
 * FAIL-OPEN ON IAM UNAVAILABILITY:
 * ─────────────────────────────────
 * If the IAM service is temporarily down, we let the request through (fail open)
 * so a network hiccup does not take down siteops. The JWT signature still
 * proves who the user claims to be — we just cannot confirm deletion status.
 * A WARN log is emitted so this is visible in monitoring.
 */
@Service
@Slf4j
public class UserValidationService {

    private final IAMServiceClient iamServiceClient;

    /**
     * Cache: userId → absolute expiry timestamp (epoch ms).
     * Entry is present only for users confirmed ACTIVE by IAM.
     */
    private final ConcurrentHashMap<String, Long> activeUserCache = new ConcurrentHashMap<>();

    @Value("${app.security.user-validation-cache-minutes:5}")
    private long cacheMinutes;

    public UserValidationService(IAMServiceClient iamServiceClient) {
        this.iamServiceClient = iamServiceClient;
    }

    /**
     * Returns true if the user is confirmed active in IAM (either from cache or live check).
     * Returns false if the user was deleted or suspended.
     *
     * @param userId     The userId extracted from the JWT (e.g. "USRBS26001")
     * @param rawToken   The raw JWT string (without "Bearer " prefix)
     */
    public boolean isUserActive(String userId, String rawToken) {
        // 1. Check in-memory cache first
        Long cachedUntil = activeUserCache.get(userId);
        if (cachedUntil != null && System.currentTimeMillis() < cachedUntil) {
            log.debug("User {} confirmed active from cache", userId);
            return true;
        }

        // 2. Cache miss or expired — call IAM for live check
        try {
            IAMApiResponse<UserDto> response = iamServiceClient.getUserProfile("Bearer " + rawToken);

            if (response == null || !response.success() || response.data() == null) {
                log.warn("IAM returned unsuccessful response for userId={}, blocking access", userId);
                evict(userId);
                return false;
            }

            UserDto user = response.data();
            String status = user.status();

            if (!"ACTIVE".equalsIgnoreCase(status)) {
                log.warn("User {} is not ACTIVE in IAM (status={}), blocking access", userId, status);
                evict(userId);
                return false;
            }

            // 3. Confirmed active — cache the result
            long expiryMs = System.currentTimeMillis() + (cacheMinutes * 60_000L);
            activeUserCache.put(userId, expiryMs);
            log.debug("User {} validated ACTIVE by IAM, cached for {} minutes", userId, cacheMinutes);
            return true;

        } catch (FeignException.NotFound e) {
            // User was deleted — IAM returned 404
            log.warn("User {} not found in IAM (404) — token is stale, blocking access", userId);
            evict(userId);
            return false;

        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            // Token is rejected by IAM itself (blacklisted, invalid, etc.)
            log.warn("IAM rejected token for userId={} ({}), blocking access", userId, e.status());
            evict(userId);
            return false;

        } catch (Exception e) {
            // IAM is unreachable — FAIL OPEN so siteops stays operational
            log.warn("IAM unreachable during user validation for userId={} ({}). " +
                     "Failing open — JWT signature still valid but deletion status unknown.", userId, e.getMessage());
            return true;
        }
    }

    /**
     * Immediately removes a userId from the active cache.
     * Call this when you know a user has been deleted or deactivated.
     */
    public void evict(String userId) {
        activeUserCache.remove(userId);
        log.debug("Evicted userId={} from active user cache", userId);
    }
}

