package com.buildsmart.projectmanager.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateResponse {

    private String templateId;
    private String templateName;
    private String description;
    private Boolean isActive;
    private List<TemplateMilestoneResponse> milestones;
    private Integer totalMilestones;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemplateMilestoneResponse {
        private String milestoneId;
        private String name;
        private String description;
        private Integer order;
        private Integer estimatedDurationDays;
    }
}
