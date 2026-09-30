package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 未成年会员监护授权聚合根：一个未成年会员只对应一份可追溯授权档案，
 * 具体授权内容按不可变版本保存。
 */
@Entity
@Table(name = "guardian_authorization")
public class GuardianAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minor_id", nullable = false, unique = true)
    private Long minorId;

    @Column(name = "minor_name", nullable = false, length = 32)
    private String minorName;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMinorId() {
        return minorId;
    }

    public void setMinorId(Long minorId) {
        this.minorId = minorId;
    }

    public String getMinorName() {
        return minorName;
    }

    public void setMinorName(String minorName) {
        this.minorName = minorName;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
