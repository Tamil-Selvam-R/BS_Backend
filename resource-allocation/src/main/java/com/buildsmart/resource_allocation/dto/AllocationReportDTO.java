package com.buildsmart.resource_allocation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllocationReportDTO {

    private String allocationId;
    private String projectId;
    private ResourceDTO resource;
    private LocalDate assignedDate;
    private LocalDate releasedDate;
    private String status;
}

