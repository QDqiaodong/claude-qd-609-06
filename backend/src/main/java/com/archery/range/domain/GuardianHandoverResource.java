package com.archery.range.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 训练开始前整组校验通过的课程 / 箭道 / 器材放行项。 */
@Entity
@Table(name = "guardian_handover_resource")
public class GuardianHandoverResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    /** COURSE / LANE / EQUIPMENT */
    @Column(nullable = false, length = 16)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "resource_name", nullable = false, length = 64)
    private String resourceName;

    /** CLEARED 已放行 / USED 已实际使用 */
    @Column(nullable = false, length = 16)
    private String status = "CLEARED";

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "cleared_at")
    private java.time.LocalDateTime clearedAt;

    @Column(name = "used_at")
    private java.time.LocalDateTime usedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHandoverId() {
        return handoverId;
    }

    public void setHandoverId(Long handoverId) {
        this.handoverId = handoverId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public java.time.LocalDateTime getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(java.time.LocalDateTime clearedAt) {
        this.clearedAt = clearedAt;
    }

    public java.time.LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(java.time.LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
