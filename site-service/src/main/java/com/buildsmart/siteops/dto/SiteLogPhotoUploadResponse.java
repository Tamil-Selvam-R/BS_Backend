package com.buildsmart.siteops.dto;

import java.time.LocalDateTime;

public record SiteLogPhotoUploadResponse(
        String logId,
        String projectId,
        String photoUrl,
        LocalDateTime uploadedAt
) {
}

