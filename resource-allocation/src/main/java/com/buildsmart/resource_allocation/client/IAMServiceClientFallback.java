package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.IAMApiResponse;
import com.buildsmart.resource_allocation.client.dto.RoleCheckResponseDTO;
import com.buildsmart.resource_allocation.client.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IAMServiceClientFallback implements IAMServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IAMServiceClientFallback.class);

    @Override
    public IAMApiResponse<UserDTO> getUserProfile(String authorization) {
        log.warn("[Fallback][IAMService] getUserProfile() - IAM service is unavailable. Returning empty response.");
        return new IAMApiResponse<>(false, "IAM service is currently unavailable. Please try again later.", null);
    }

    @Override
    public IAMApiResponse<RoleCheckResponseDTO> checkUserRole(String role, String authorization) {
        log.warn("[Fallback][IAMService] checkUserRole({}) - IAM service is unavailable. Returning false by default.", role);
        RoleCheckResponseDTO fallbackRole = new RoleCheckResponseDTO("UNKNOWN", false);
        return new IAMApiResponse<>(false, "IAM service is currently unavailable. Role check could not be performed.", fallbackRole);
    }

    @Override
    public UserDTO getUserById(String userId, String authorization) {
        log.warn("[Fallback][IAMService] getUserById({}) - IAM service is unavailable. Returning null.", userId);
        return null;
    }

    @Override
    public UserDTO getUserByEmail(String email, String authorization) {
        log.warn("[Fallback][IAMService] getUserByEmail({}) - IAM service is unavailable. Returning null.", email);
        return null;
    }
}

