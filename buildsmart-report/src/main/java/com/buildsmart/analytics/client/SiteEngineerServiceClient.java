package com.buildsmart.analytics.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client to communicate with the Site Engineer microservice.
 */
@FeignClient(
        name = "siteops-service",
        fallback = SiteEngineerServiceFallback.class,
        url = "${site.engineer.service.url}"
)
public interface SiteEngineerServiceClient {

    @GetMapping("/api/issues")
    List<SiteIssueDTO> getIssues(
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "reportedBy", required = false) String reportedBy
    );

    @GetMapping("/api/sitelogs")
    List<SiteLogDTO> getSiteLogs(
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to
    );
}
