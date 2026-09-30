package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 现场交接轨迹：接管、训练放行、基线冻结、异常处置、待交还、交还连续留痕。 */
@Entity
@Table(name = "guardian_handover_history")
public class GuardianHandoverTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "from_stage", length = 24)
    private String fromStage;

    @Column(name = "to_stage", length = 24)
    private String toStage;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "baseline_before_json", length = 4000)
    private String baselineBeforeJson;

    @Column(name = "baseline_after_json", length = 4000)
    private String baselineAfterJson;

    @Column(name = "blocked_stage", length = 24)
    private String blockedStage;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    @Column(nullable = false, length = 32)
    private String operator;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    @Column(length = 500)
    private String reason;

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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFromStage() {
        return fromStage;
    }

    public void setFromStage(String fromStage) {
        this.fromStage = fromStage;
    }

    public String getToStage() {
        return toStage;
    }

    public void setToStage(String toStage) {
        this.toStage = toStage;
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

    public String getBlockedStage() {
        return blockedStage;
    }

    public void setBlockedStage(String blockedStage) {
        this.blockedStage = blockedStage;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
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
