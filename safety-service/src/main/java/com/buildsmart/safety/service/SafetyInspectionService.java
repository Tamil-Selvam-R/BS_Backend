package com.buildsmart.safety.service;

import com.buildsmart.safety.domain.model.InspectionStatus;
import com.buildsmart.safety.web.dto.InspectionDtos.CreateInspectionRequest;
import com.buildsmart.safety.web.dto.InspectionDtos.InspectionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface SafetyInspectionService {
    InspectionResponse create(CreateInspectionRequest request);
    InspectionResponse get(String id);
    Page<InspectionResponse> search(Optional<String> projectId, Optional<InspectionStatus> status,
                                    Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo,
                                    Pageable pageable);
    InspectionResponse updateStatus(String id, InspectionStatus newStatus);
    void delete(String id);
}
