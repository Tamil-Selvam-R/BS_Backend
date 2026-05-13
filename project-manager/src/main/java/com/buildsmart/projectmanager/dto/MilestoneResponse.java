package com.buildsmart.projectmanager.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilestoneResponse {

    private String milestoneId;
    private String projectId;
    private String name;
    private String description;
    private Integer order;
    private String status;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private Integer daysRemaining;
    private Boolean isOverdue;
}
