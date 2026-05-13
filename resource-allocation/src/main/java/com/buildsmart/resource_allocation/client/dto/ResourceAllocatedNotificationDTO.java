package com.buildsmart.resource_allocation.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceAllocatedNotificationDTO {

    private String allocationId;
    private String projectId;
    private String resourceId;
    private String resourceType;
    private LocalDate assignedDate;
    private LocalDate releasedDate;
    private String allocatedBy;
}
