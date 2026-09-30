package com.archery.range.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 未成年会员监护授权与现场交接 DTO。 */
public final class GuardianDtos {

    private GuardianDtos() {
    }

    public record ScopeReq(
            List<Long> courseIds,
            List<Long> laneIds,
            List<Long> equipmentIds,
            List<String> caretakerNames) {
    }

    public record AuthorizationSaveReq(
            @NotNull(message = "请选择未成年会员") Long minorId,
            @NotBlank(message = "监护人姓名不能为空") String guardianName,
            @NotBlank(message = "监护人手机号不能为空") String guardianPhone,
            @NotNull(message = "请选择生效日") LocalDate effectiveDate,
            @NotNull(message = "请选择终止日") LocalDate terminationDate,
            @NotBlank(message = "紧急联系人姓名不能为空") String emergencyContactName,
            @NotBlank(message = "紧急联系人手机号不能为空") String emergencyContactPhone,
            String remarks,
            @NotNull(message = "请选择授权课程范围") List<@NotNull Long> courseIds,
            @NotNull(message = "请选择授权箭道范围") List<@NotNull Long> laneIds,
            @NotNull(message = "请选择授权器材范围") List<@NotNull Long> equipmentIds,
            @NotNull(message = "请填写可现场接管人员") List<@NotBlank String> caretakerNames,
            @NotBlank(message = "操作人不能为空") String operator,
            String reason) {
    }

    public record RevisionReq(
            @NotNull(message = "缺少授权档案") Long authorizationId,
            @NotBlank String guardianName,
            @NotBlank String guardianPhone,
            @NotNull LocalDate effectiveDate,
            @NotNull LocalDate terminationDate,
            @NotBlank String emergencyContactName,
            @NotBlank String emergencyContactPhone,
            String remarks,
            @NotNull List<@NotNull Long> courseIds,
            @NotNull List<@NotNull Long> laneIds,
            @NotNull List<@NotNull Long> equipmentIds,
            @NotNull List<@NotBlank String> caretakerNames,
            @NotBlank String operator,
            @NotBlank(message = "修订原因不能为空") String reason) {
    }

    public record DraftUpdateReq(
            @NotNull(message = "缺少授权版本") Long versionId,
            @NotBlank String guardianName,
            @NotBlank String guardianPhone,
            @NotNull LocalDate effectiveDate,
            @NotNull LocalDate terminationDate,
            @NotBlank String emergencyContactName,
            @NotBlank String emergencyContactPhone,
            String remarks,
            @NotNull List<@NotNull Long> courseIds,
            @NotNull List<@NotNull Long> laneIds,
            @NotNull List<@NotNull Long> equipmentIds,
            @NotNull List<@NotBlank String> caretakerNames,
            @NotBlank String operator,
            String reason) {
    }

    public record OperatorReq(@NotBlank(message = "操作人不能为空") String operator) {
    }

    public record VersionReq(
            @NotNull(message = "缺少授权版本") Long versionId,
            @NotBlank(message = "操作人不能为空") String operator,
            String reason) {
    }

    public record HandoverCreateReq(
            @NotNull(message = "请选择未成年会员") Long minorId,
            @NotBlank(message = "操作人不能为空") String operator) {
    }

    public record TakeoverReq(
            @NotNull(message = "缺少交接记录") Long handoverId,
            @NotNull(message = "缺少所依据的授权版本") Long expectedVersionId,
            @NotBlank(message = "请填写实际接管人") String caretakerName,
            @NotBlank(message = "请核验证件号") String identityNo,
            @NotBlank(message = "接管工作人员不能为空") String operator) {
    }

    public record TrainingPrecheckReq(
            @NotNull(message = "缺少交接记录") Long handoverId,
            @NotNull(message = "请选择本次课程") Long courseId,
            @NotNull(message = "请选择本次箭道") Long laneId,
            @NotNull(message = "请选择本次器材") List<@NotNull Long> equipmentIds,
            @NotBlank(message = "缺少幂等请求号") String requestId,
            @NotBlank(message = "教练不能为空") String operator) {
    }

    public record RebaselineReq(
            @NotNull(message = "缺少交接记录") Long handoverId,
            @NotNull(message = "缺少当前授权版本") Long expectedVersionId,
            @NotBlank(message = "工作人员不能为空") String operator,
            @NotBlank(message = "重新核对说明不能为空") String reason) {
    }

    public record FinishTrainingReq(
            @NotNull(message = "缺少交接记录") Long handoverId,
            @NotBlank(message = "操作人不能为空") String operator,
            String reason) {
    }

    public record ReturnReq(
            @NotNull(message = "缺少交接记录") Long handoverId,
            @NotBlank(message = "请填写交还接管人") String caretakerName,
            @NotBlank(message = "请核验证件号") String identityNo,
            @NotBlank(message = "交还确认人不能为空") String operator,
            String reason) {
    }

    public record NameId(Long id, String name) {
    }

    public record ScopeView(
            List<NameId> courses,
            List<NameId> lanes,
            List<NameId> equipment,
            List<String> caretakerNames) {
    }

    public record VersionView(
            Long id,
            Long authorizationId,
            Integer versionNo,
            String status,
            String statusName,
            String guardianName,
            String guardianPhone,
            LocalDate effectiveDate,
            LocalDate terminationDate,
            String emergencyContactName,
            String emergencyContactPhone,
            String remarks,
            ScopeView scope,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            LocalDateTime confirmedAt,
            String confirmedBy,
            String changeReason,
            boolean current) {
    }

    public record HistoryView(
            Long id,
            Long versionId,
            Integer versionNo,
            String action,
            String fromStatus,
            String toStatus,
            String baselineBeforeJson,
            String baselineAfterJson,
            String operator,
            LocalDateTime operatedAt,
            String reason) {
    }

    public record AuthorizationDetail(
            Long id,
            Long minorId,
            String minorName,
            Long currentVersionId,
            String createdBy,
            LocalDateTime createdAt,
            List<VersionView> versions,
            List<HistoryView> history) {
    }

    public record ResourceView(
            Long id,
            String resourceType,
            Long resourceId,
            String resourceName,
            String status,
            String requestId,
            LocalDateTime clearedAt,
            LocalDateTime usedAt) {
    }

    public record HandoverTrailView(
            Long id,
            String action,
            String fromStage,
            String toStage,
            Long versionId,
            Integer versionNo,
            String baselineBeforeJson,
            String baselineAfterJson,
            String blockedStage,
            String blockReason,
            String operator,
            LocalDateTime operatedAt,
            String reason) {
    }

    public record HandoverView(
            Long id,
            String handoverNo,
            Long minorId,
            String minorName,
            Long authorizationId,
            Long authorizationVersionId,
            Integer versionNo,
            String stage,
            String stageName,
            String actualCaretaker,
            String caretakerIdentityNo,
            String takenOverBy,
            LocalDateTime takenOverAt,
            String trainingStartedBy,
            LocalDateTime trainingStartedAt,
            String waitingReturnBy,
            LocalDateTime waitingReturnAt,
            String returnedCaretaker,
            String returnedBy,
            LocalDateTime returnedAt,
            boolean baselineChangedAfterTakeover,
            boolean frozen,
            String freezeReason,
            LocalDateTime frozenAt,
            String createdBy,
            LocalDateTime createdAt,
            String baselineJson,
            List<ResourceView> resources,
            List<HandoverTrailView> trails) {
    }

    public record MinorOption(Long id, String cardNo, String name, LocalDate birthDate) {
    }
}
