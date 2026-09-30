package com.archery.range.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 每次到馆一条独立现场交接单：待接管 → 已接管/待训练 → 训练中 → 待交还 → 已交还。
 * 接管时固定授权版本基线；基线后来变化只冻结新动作，不回写已发生事实。
 */
@Entity
@Table(name = "guardian_handover")
public class GuardianHandover {

    public static final String STAGE_WAITING_TAKEOVER = "WAITING_TAKEOVER";
    public static final String STAGE_TAKEN_OVER = "TAKEN_OVER";
    public static final String STAGE_TRAINING = "TRAINING";
    public static final String STAGE_WAITING_RETURN = "WAITING_RETURN";
    public static final String STAGE_RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_no", nullable = false, unique = true, length = 24)
    private String handoverNo;

    @Column(name = "minor_id", nullable = false)
    private Long minorId;

    @Column(name = "minor_name", nullable = false, length = 32)
    private String minorName;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "authorization_version_id", nullable = false)
    private Long authorizationVersionId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 24)
    private String stage = STAGE_WAITING_TAKEOVER;

    @Column(name = "baseline_json", nullable = false, length = 4000)
    private String baselineJson;

    @Column(name = "actual_caretaker", length = 32)
    private String actualCaretaker;

    @Column(name = "caretaker_identity_no", length = 40)
    private String caretakerIdentityNo;

    @Column(name = "taken_over_by", length = 32)
    private String takenOverBy;

    @Column(name = "taken_over_at")
    private LocalDateTime takenOverAt;

    @Column(name = "training_started_by", length = 32)
    private String trainingStartedBy;

    @Column(name = "training_started_at")
    private LocalDateTime trainingStartedAt;

    @Column(name = "waiting_return_at")
    private LocalDateTime waitingReturnAt;

    @Column(name = "waiting_return_by", length = 32)
    private String waitingReturnBy;

    @Column(name = "returned_caretaker", length = 32)
    private String returnedCaretaker;

    @Column(name = "returned_by", length = 32)
    private String returnedBy;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "baseline_changed_after_takeover", nullable = false)
    private boolean baselineChangedAfterTakeover = false;

    @Column(name = "frozen", nullable = false)
    private boolean frozen = false;

    @Column(name = "freeze_reason", length = 500)
    private String freezeReason;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "handoverId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GuardianHandoverResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "handoverId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GuardianHandoverTrail> trails = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHandoverNo() {
        return handoverNo;
    }

    public void setHandoverNo(String handoverNo) {
        this.handoverNo = handoverNo;
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

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public Long getAuthorizationVersionId() {
        return authorizationVersionId;
    }

    public void setAuthorizationVersionId(Long authorizationVersionId) {
        this.authorizationVersionId = authorizationVersionId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getBaselineJson() {
        return baselineJson;
    }

    public void setBaselineJson(String baselineJson) {
        this.baselineJson = baselineJson;
    }

    public String getActualCaretaker() {
        return actualCaretaker;
    }

    public void setActualCaretaker(String actualCaretaker) {
        this.actualCaretaker = actualCaretaker;
    }

    public String getCaretakerIdentityNo() {
        return caretakerIdentityNo;
    }

    public void setCaretakerIdentityNo(String caretakerIdentityNo) {
        this.caretakerIdentityNo = caretakerIdentityNo;
    }

    public String getTakenOverBy() {
        return takenOverBy;
    }

    public void setTakenOverBy(String takenOverBy) {
        this.takenOverBy = takenOverBy;
    }

    public LocalDateTime getTakenOverAt() {
        return takenOverAt;
    }

    public void setTakenOverAt(LocalDateTime takenOverAt) {
        this.takenOverAt = takenOverAt;
    }

    public String getTrainingStartedBy() {
        return trainingStartedBy;
    }

    public void setTrainingStartedBy(String trainingStartedBy) {
        this.trainingStartedBy = trainingStartedBy;
    }

    public LocalDateTime getTrainingStartedAt() {
        return trainingStartedAt;
    }

    public void setTrainingStartedAt(LocalDateTime trainingStartedAt) {
        this.trainingStartedAt = trainingStartedAt;
    }

    public LocalDateTime getWaitingReturnAt() {
        return waitingReturnAt;
    }

    public void setWaitingReturnAt(LocalDateTime waitingReturnAt) {
        this.waitingReturnAt = waitingReturnAt;
    }

    public String getWaitingReturnBy() {
        return waitingReturnBy;
    }

    public void setWaitingReturnBy(String waitingReturnBy) {
        this.waitingReturnBy = waitingReturnBy;
    }

    public String getReturnedCaretaker() {
        return returnedCaretaker;
    }

    public void setReturnedCaretaker(String returnedCaretaker) {
        this.returnedCaretaker = returnedCaretaker;
    }

    public String getReturnedBy() {
        return returnedBy;
    }

    public void setReturnedBy(String returnedBy) {
        this.returnedBy = returnedBy;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public boolean isBaselineChangedAfterTakeover() {
        return baselineChangedAfterTakeover;
    }

    public void setBaselineChangedAfterTakeover(boolean baselineChangedAfterTakeover) {
        this.baselineChangedAfterTakeover = baselineChangedAfterTakeover;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public LocalDateTime getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(LocalDateTime frozenAt) {
        this.frozenAt = frozenAt;
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

    public List<GuardianHandoverResource> getResources() {
        return resources;
    }

    public void setResources(List<GuardianHandoverResource> resources) {
        this.resources = resources;
    }

    public List<GuardianHandoverTrail> getTrails() {
        return trails;
    }

    public void setTrails(List<GuardianHandoverTrail> trails) {
        this.trails = trails;
    }
}
