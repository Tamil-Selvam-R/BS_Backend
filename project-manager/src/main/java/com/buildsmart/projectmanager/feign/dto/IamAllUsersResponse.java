package com.buildsmart.projectmanager.feign.dto;

import java.util.List;

public record IamAllUsersResponse(
        boolean success,
        String message,
        List<IamUserProfile> data
) {
}

