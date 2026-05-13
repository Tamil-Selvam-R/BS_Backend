package com.buildsmart.vendor.entity;

import com.buildsmart.vendor.enums.EntityType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "revised_update")
public class RevisedUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(name = "original_entity_id", nullable = false)
    private String originalEntityId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
    
    @Column(name = "previous_data", columnDefinition = "TEXT")
    private String previousData;

    @Column(name = "updated_data", columnDefinition = "TEXT")
    private String updatedData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public RevisedUpdate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public String getOriginalEntityId() {
        return originalEntityId;
    }

    public void setOriginalEntityId(String originalEntityId) {
        this.originalEntityId = originalEntityId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getPreviousData() {
        return previousData;
    }

    public void setPreviousData(String previousData) {
        this.previousData = previousData;
    }

    public String getUpdatedData() {
        return updatedData;
    }

    public void setUpdatedData(String updatedData) {
        this.updatedData = updatedData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
