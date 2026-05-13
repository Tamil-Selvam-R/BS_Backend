package com.buildsmart.resource_allocation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectDTO {

    private String projectId;
    private String name;
    private String startDate;
    private String endDate;
    private Double budget;
    private String status;

    /**
     * UserId of the PM who created/owns this project. Sourced from PM's
     * Project.createdBy. Used to target central notifications at the right
     * PM rather than broadcasting to all PMs.
     */
    private String createdBy;
}