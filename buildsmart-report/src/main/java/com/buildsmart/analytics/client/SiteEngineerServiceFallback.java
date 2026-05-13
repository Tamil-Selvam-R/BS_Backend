package com.buildsmart.analytics.client;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for SiteEngineerServiceClient when the service is unavailable.
 */
@Component
public class SiteEngineerServiceFallback implements SiteEngineerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SiteEngineerServiceFallback.class);

    @Override
    public List<SiteIssueDTO> getIssues(String projectId, String status, String severity, String reportedBy) {
        log.warn("Site Engineer service unavailable for issues (projectId={})", projectId);
        return List.of();
    }

    @Override
    public List<SiteLogDTO> getSiteLogs(String projectId, String from, String to) {
        log.warn("Site Engineer service unavailable for site logs (projectId={})", projectId);
        return List.of();
    }
}
