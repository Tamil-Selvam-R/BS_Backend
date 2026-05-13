package com.buildsmart.projectmanager.feign;

import com.buildsmart.projectmanager.feign.dto.IamAllUsersResponse;
import com.buildsmart.projectmanager.feign.dto.IamProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "iam-service"
)
public interface IamServiceClient {

    @GetMapping("/users/profile")
    IamProfileResponse getCurrentUserProfile(@RequestHeader("Authorization") String authorization);

    @GetMapping("/users/all")
    IamAllUsersResponse getAllUsers(@RequestHeader("Authorization") String authorization);
}

