package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 监护授权版本历史轨迹：记录每次动作前后状态、授权版本基线、操作人与原因。 */
@Entity
@Table(name = "guardian_authorization_history")
public class GuardianAuthorizationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 16)
    private String toStatus;

    @Column(name = "baseline_before_json", length = 4000)
    private String baselineBeforeJson;

    @Column(name = "baseline_after_json", length = 4000)
    private String baselineAfterJson;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    @Column(nullable = false, length = 500)
    private String reason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getBaselineBeforeJson() {
        return baselineBeforeJson;
    }

    public void setBaselineBeforeJson(String baselineBeforeJson) {
        this.baselineBeforeJson = baselineBeforeJson;
    }

    public String getBaselineAfterJson() {
        return baselineAfterJson;
    }

    public void setBaselineAfterJson(String baselineAfterJson) {
        this.baselineAfterJson = baselineAfterJson;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }

    public void setOperatedAt(LocalDateTime operatedAt) {
        this.operatedAt = operatedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
