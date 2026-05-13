package com.buildsmart.projectmanager.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;


@FeignClient(
    name = "inventory-service"
)
public interface InventoryServiceClient {

    @GetMapping("/api/materials/project/{projectId}")
    List<Map<String, Object>> getMaterialsByProject(@PathVariable("projectId") Long projectId);

    @GetMapping("/api/materials/{id}/availability")
    Map<String, Object> checkMaterialAvailability(@PathVariable("id") Long id);
}
