package com.buildsmart.projectmanager.feign.dto;

public record IamProfileResponse(
        boolean success,
        String message,
        IamUserProfile data
) {
}

