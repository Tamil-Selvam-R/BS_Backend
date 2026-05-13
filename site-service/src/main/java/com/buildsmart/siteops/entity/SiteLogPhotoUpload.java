package com.buildsmart.siteops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "site_log_photo_uploads")
public class SiteLogPhotoUpload {

    @Id
    @Column(name = "upload_token", nullable = false, updatable = false, length = 64)
    private String uploadToken;

    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    @Column(name = "uploaded_by", nullable = false, length = 20)
    private String uploadedBy;

    @Column(name = "stored_file_name", nullable = false, length = 500)
    private String storedFileName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "linked_log_id", length = 20)
    private String linkedLogId;
}

