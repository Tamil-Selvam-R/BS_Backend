package com.buildsmart.projectmanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "template_milestones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String milestoneId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer orderNumber;

    @Column(nullable = false)
    private Integer estimatedDurationDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ProjectTemplate template;
}
