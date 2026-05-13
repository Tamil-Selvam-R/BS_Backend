package com.company.notification.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadCountResponse {
    private long count;
    /**
     * Indicates if this came from a fallback (circuit open / DB down).
     * Frontend can show "—" instead of "0" when degraded == true so users
     * are not misled into thinking they have no notifications.
     */
    private boolean degraded;
}
