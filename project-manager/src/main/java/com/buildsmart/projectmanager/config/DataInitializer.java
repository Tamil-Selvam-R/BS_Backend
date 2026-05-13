package com.buildsmart.projectmanager.config;

import com.buildsmart.projectmanager.entity.*;
import com.buildsmart.projectmanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProjectTemplateRepository templateRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initializeTemplates();
        log.info("✅ Sample data initialized successfully");
    }

    private void initializeTemplates() {
        if (templateRepository.count() > 0) return;

        createCommercialTemplate();
        createIndustrialTemplate();
        createResidentialTemplate();
        createRuralTemplate();

        log.info("✅ Project templates created");
    }

    private void createCommercialTemplate() {
        ProjectTemplate template = ProjectTemplate.builder()
            .templateId("TEMPBS01")
            .templateName("Commercial Project Template")
            .description("Standard template for commercial building projects including offices, retail spaces, and mixed-use developments.")
            .isActive(true)
            .build();

        template.addMilestone(createMilestone("MS01", "Project Initiation", "Project kickoff, stakeholder identification, and initial planning", 1, 14));
        template.addMilestone(createMilestone("MS02", "Design & Approvals", "Architectural design completion and regulatory approvals", 2, 45));
        template.addMilestone(createMilestone("MS03", "Procurement", "Vendor selection, material procurement, and contract finalization", 3, 30));
        template.addMilestone(createMilestone("MS04", "Foundation & Structure", "Foundation laying and structural framework completion", 4, 60));
        template.addMilestone(createMilestone("MS05", "MEP Installation", "Mechanical, Electrical, and Plumbing systems installation", 5, 45));
        template.addMilestone(createMilestone("MS06", "Interior & Finishing", "Interior works, finishing, and quality checks", 6, 40));
        template.addMilestone(createMilestone("MS07", "Testing & Commissioning", "System testing, safety inspections, and commissioning", 7, 21));
        template.addMilestone(createMilestone("MS08", "Handover", "Final inspection, documentation, and project handover", 8, 14));

        templateRepository.save(template);
    }

    private void createIndustrialTemplate() {
        ProjectTemplate template = ProjectTemplate.builder()
            .templateId("TEMPBS02")
            .templateName("Industrial Project Template")
            .description("Template for industrial facilities including factories, warehouses, and manufacturing plants.")
            .isActive(true)
            .build();

        template.addMilestone(createMilestone("MS01", "Project Planning", "Industrial requirements analysis and project planning", 1, 21));
        template.addMilestone(createMilestone("MS02", "Environmental Clearance", "Environmental impact assessment and clearance", 2, 60));
        template.addMilestone(createMilestone("MS03", "Heavy Equipment Procurement", "Procurement of industrial machinery and equipment", 3, 45));
        template.addMilestone(createMilestone("MS04", "Civil Construction", "Foundation and civil structure construction", 4, 90));
        template.addMilestone(createMilestone("MS05", "Equipment Installation", "Industrial equipment installation and setup", 5, 45));
        template.addMilestone(createMilestone("MS06", "Safety Compliance", "Safety systems installation and compliance verification", 6, 30));
        template.addMilestone(createMilestone("MS07", "Trial Run", "Equipment trial run and process validation", 7, 21));

        templateRepository.save(template);
    }

    private void createResidentialTemplate() {
        ProjectTemplate template = ProjectTemplate.builder()
            .templateId("TEMPBS03")
            .templateName("Residential Project Template")
            .description("Template for residential developments including apartments, villas, and housing complexes.")
            .isActive(true)
            .build();

        template.addMilestone(createMilestone("MS01", "Land Acquisition & Planning", "Land verification, acquisition, and project planning", 1, 30));
        template.addMilestone(createMilestone("MS02", "Regulatory Approvals", "Building permits and regulatory clearances", 2, 45));
        template.addMilestone(createMilestone("MS03", "Foundation Phase", "Foundation and basement construction", 3, 45));
        template.addMilestone(createMilestone("MS04", "Superstructure", "Multi-floor structure construction", 4, 120));
        template.addMilestone(createMilestone("MS05", "Finishing & Amenities", "Interior finishing and amenities development", 5, 60));
        template.addMilestone(createMilestone("MS06", "Occupancy Certificate", "Final inspections and occupancy certificate", 6, 30));

        templateRepository.save(template);
    }

    private void createRuralTemplate() {
        ProjectTemplate template = ProjectTemplate.builder()
            .templateId("TEMPBS04")
            .templateName("Rural Project Template")
            .description("Template for rural infrastructure including community centers, schools, and healthcare facilities.")
            .isActive(true)
            .build();

        template.addMilestone(createMilestone("MS01", "Community Assessment", "Assess community needs and project requirements", 1, 14));
        template.addMilestone(createMilestone("MS02", "Government Approvals", "Obtain necessary government approvals and permits", 2, 30));
        template.addMilestone(createMilestone("MS03", "Local Procurement", "Procure materials with focus on local sourcing", 3, 21));
        template.addMilestone(createMilestone("MS04", "Construction Phase", "Main construction activities", 4, 60));
        template.addMilestone(createMilestone("MS05", "Community Handover", "Project completion and community handover", 5, 14));

        templateRepository.save(template);
    }

    private TemplateMilestone createMilestone(String id, String name, String description, int order, int days) {
        return TemplateMilestone.builder()
            .milestoneId(id)
            .name(name)
            .description(description)
            .orderNumber(order)
            .estimatedDurationDays(days)
            .build();
    }
}
