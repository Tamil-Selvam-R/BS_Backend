package com.buildsmart.vendor.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDTO(
        String projectId,
        String projectName,
        String status,
        String projectManager,
        LocalDate startDate,
        LocalDate endDate
) {}

