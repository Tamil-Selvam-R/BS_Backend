package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.entity.RevisedUpdate;
import com.buildsmart.vendor.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RevisedUpdateRepository extends JpaRepository<RevisedUpdate, Long> {
    List<RevisedUpdate> findByOriginalEntityIdAndEntityTypeOrderByCreatedAtDesc(String originalEntityId, EntityType entityType);
}
