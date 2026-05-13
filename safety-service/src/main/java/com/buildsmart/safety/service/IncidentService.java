package com.buildsmart.safety.service;

import com.buildsmart.safety.domain.model.IncidentSeverity;
import com.buildsmart.safety.domain.model.IncidentStatus;
import com.buildsmart.safety.web.dto.IncidentDtos.CreateIncidentRequest;
import com.buildsmart.safety.web.dto.IncidentDtos.IncidentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface IncidentService {
    IncidentResponse create(CreateIncidentRequest request);
    IncidentResponse get(String id);
    Page<IncidentResponse> search(Optional<String> projectId, Optional<IncidentStatus> status,
                                  Optional<IncidentSeverity> severity,
                                  Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo,
                                  Pageable pageable);
    IncidentResponse updateStatus(String id, IncidentStatus newStatus);
    void delete(String id);
}
