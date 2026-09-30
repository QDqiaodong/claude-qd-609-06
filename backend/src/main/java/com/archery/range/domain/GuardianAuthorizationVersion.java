package com.archery.range.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * 监护授权版本：草稿、待确认与所有历史版本均原样保留。
 * 范围、日期或接管人变化必须新建版本，已经确认的版本不可原地修改。
 */
@Entity
@Table(name = "guardian_authorization_version")
public class GuardianAuthorizationVersion {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "minor_id", nullable = false)
    private Long minorId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** DRAFT / PENDING / VALID / PAUSED / REVOKED；EXPIRED 仅用于读取时派生展示。 */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "guardian_name", nullable = false, length = 32)
    private String guardianName;

    @Column(name = "guardian_phone", nullable = false, length = 20)
    private String guardianPhone;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "termination_date", nullable = false)
    private LocalDate terminationDate;

    @Column(name = "emergency_contact_name", nullable = false, length = 32)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", nullable = false, length = 20)
    private String emergencyContactPhone;

    @Column(length = 1000)
    private String remarks;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "guardian_version_course", joinColumns = @JoinColumn(name = "version_id"))
    @Column(name = "course_id")
    private Set<Long> courseIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "guardian_version_lane", joinColumns = @JoinColumn(name = "version_id"))
    @Column(name = "lane_id")
    private Set<Long> laneIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "guardian_version_equipment", joinColumns = @JoinColumn(name = "version_id"))
    @Column(name = "equipment_id")
    private Set<Long> equipmentIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "guardian_version_caretaker", joinColumns = @JoinColumn(name = "version_id"))
    @Column(name = "caretaker_name", length = 32)
    private Set<String> caretakerNames = new HashSet<>();

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by", length = 32)
    private String statusChangedBy;

    /** DRAFT_PENDING / GUARDIAN_CONFIRMED / PAUSE / RESUME / REVOKE / SUPERSEDED */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

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

    public Long getMinorId() {
        return minorId;
    }

    public void setMinorId(Long minorId) {
        this.minorId = minorId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Set<Long> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(Set<Long> courseIds) {
        this.courseIds = courseIds;
    }

    public Set<Long> getLaneIds() {
        return laneIds;
    }

    public void setLaneIds(Set<Long> laneIds) {
        this.laneIds = laneIds;
    }

    public Set<Long> getEquipmentIds() {
        return equipmentIds;
    }

    public void setEquipmentIds(Set<Long> equipmentIds) {
        this.equipmentIds = equipmentIds;
    }

    public Set<String> getCaretakerNames() {
        return caretakerNames;
    }

    public void setCaretakerNames(Set<String> caretakerNames) {
        this.caretakerNames = caretakerNames;
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

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(LocalDateTime statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public String getStatusChangedBy() {
        return statusChangedBy;
    }

    public void setStatusChangedBy(String statusChangedBy) {
        this.statusChangedBy = statusChangedBy;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
}
