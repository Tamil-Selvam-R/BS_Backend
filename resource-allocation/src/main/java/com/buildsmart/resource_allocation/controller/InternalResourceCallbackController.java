package com.buildsmart.resource_allocation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Finance callback endpoint — no longer in use.
 *
 * The resource-allocation module no longer depends on Finance for budget
 * approval. Resources and Allocations are created directly without any
 * Finance gate. This endpoint is kept as a stub so the URL does not return
 * 404, but it responds with 410 Gone to signal the feature was removed.
 */
@RestController
@RequestMapping("/api/internal/resources")
public class InternalResourceCallbackController {

    @PostMapping("/{resourceId}/budget-result")
    public ResponseEntity<Map<String, String>> budgetResult(
            @PathVariable("resourceId") String resourceId,
            @RequestBody(required = false) Map<String, String> payload) {

        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                        "message", "Finance budget-approval flow has been removed. " +
                                   "Resources and Allocations are now created independently.",
                        "resourceId", resourceId
                ));
    }
}
