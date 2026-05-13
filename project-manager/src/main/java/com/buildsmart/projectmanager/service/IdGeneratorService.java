package com.buildsmart.projectmanager.service;

import com.buildsmart.projectmanager.entity.DepartmentCode;
import com.buildsmart.projectmanager.repository.ProjectRepository;
import com.buildsmart.projectmanager.repository.ProjectTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;


@Service
@RequiredArgsConstructor
public class IdGeneratorService {

    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository taskRepository;

    private final AtomicInteger projectCounter = new AtomicInteger(0);


    public String generateProjectId() {
        String yearSuffix = String.valueOf(Year.now().getValue()).substring(2);
        String prefix = "CHEBS" + yearSuffix;

        Integer maxNumber = projectRepository.findMaxProjectIdNumber(prefix);
        int nextNumber = (maxNumber != null) ? maxNumber + 1 : 1;

        return String.format("%s%03d", prefix, nextNumber);
    }


    public String generateTaskId(DepartmentCode departmentCode) {
        String prefix = switch (departmentCode) {
            case FINANCE_OFFICER -> "FIN";
            case VENDOR -> "VN";
            case SITE_ENGINEER -> "SE";
            case SAFETY_OFFICER -> "SO";
            case PROJECT_MANAGER -> "PM";
            case ADMIN -> "ADM";
        };

        Integer maxNumber = taskRepository.findMaxTaskIdNumber(prefix);
        int nextNumber = (maxNumber != null) ? maxNumber + 1 : 1;

        return String.format("%s%03d", prefix, nextNumber);
    }

    public String generateApprovalId() {
        return "APR" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    public String generateNotificationId() {
        return "NOT" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }
}
