package com.buildsmart.projectmanager.service;

import com.buildsmart.projectmanager.dto.TemplateResponse;
import com.buildsmart.projectmanager.entity.*;
import com.buildsmart.projectmanager.exception.InvalidTemplateException;
import com.buildsmart.projectmanager.repository.ProjectTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService {

    private final ProjectTemplateRepository templateRepository;

    public List<TemplateResponse> getAllActiveTemplates() {
        return templateRepository.findByIsActiveTrue()
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public TemplateResponse getTemplateById(String templateId) {
        ProjectTemplate template = templateRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new InvalidTemplateException(templateId));
        return mapToResponse(template);
    }

    public ProjectTemplate getTemplateEntityById(String templateId) {
        ProjectTemplate template = templateRepository.findByTemplateIdWithMilestones(templateId)
            .orElseThrow(() -> new InvalidTemplateException(templateId));
        return template;
    }

    public boolean isValidTemplateId(String templateId) {
        return templateRepository.existsByTemplateId(templateId);
    }

    private TemplateResponse mapToResponse(ProjectTemplate template) {
        List<TemplateResponse.TemplateMilestoneResponse> milestones = template.getMilestones()
            .stream()
            .map(m -> TemplateResponse.TemplateMilestoneResponse.builder()
                .milestoneId(m.getMilestoneId())
                .name(m.getName())
                .description(m.getDescription())
                .order(m.getOrderNumber())
                .estimatedDurationDays(m.getEstimatedDurationDays())
                .build())
            .collect(Collectors.toList());

        return TemplateResponse.builder()
            .templateId(template.getTemplateId())
            .templateName(template.getTemplateName())
            .description(template.getDescription())
            .isActive(template.getIsActive())
            .milestones(milestones)
            .totalMilestones(milestones.size())
            .build();
    }
}
