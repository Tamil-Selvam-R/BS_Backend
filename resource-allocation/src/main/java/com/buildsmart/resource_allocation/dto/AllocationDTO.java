package com.buildsmart.resource_allocation.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllocationDTO {

    private String allocationId;
    private String projectId;
    private String resourceId;
    private LocalDate assignedDate;
    private LocalDate releasedDate;
    private String status;
}

