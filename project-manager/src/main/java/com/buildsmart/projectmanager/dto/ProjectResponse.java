package com.buildsmart.projectmanager.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private String projectId;
    private String projectName;
    private String description;
    private String templateId;
    private String templateName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Double budget;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MilestoneResponse> milestones;
    private Integer totalMilestones;
    private Integer completedMilestones;
    private Integer totalTasks;
    private Integer completedTasks;
}
