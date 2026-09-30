package com.archery.range.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.archery.range.common.BizException;
import com.archery.range.domain.Course;
import com.archery.range.domain.Equipment;
import com.archery.range.domain.GuardianAuthorization;
import com.archery.range.domain.GuardianAuthorizationHistory;
import com.archery.range.domain.GuardianAuthorizationVersion;
import com.archery.range.domain.GuardianHandover;
import com.archery.range.domain.GuardianHandoverResource;
import com.archery.range.domain.GuardianHandoverTrail;
import com.archery.range.domain.Lane;
import com.archery.range.domain.Member;
import com.archery.range.dto.GuardianDtos;
import com.archery.range.repository.CourseRepository;
import com.archery.range.repository.EquipmentRepository;
import com.archery.range.repository.GuardianAuthorizationHistoryRepository;
import com.archery.range.repository.GuardianAuthorizationRepository;
import com.archery.range.repository.GuardianAuthorizationVersionRepository;
import com.archery.range.repository.GuardianHandoverRepository;
import com.archery.range.repository.GuardianHandoverResourceRepository;
import com.archery.range.repository.GuardianHandoverTrailRepository;
import com.archery.range.repository.LaneRepository;
import com.archery.range.repository.MemberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 监护授权 + 到馆交接的聚合服务。所有状态推进均在事务内锁定授权聚合和交接单，
 * 范围整组校验，历史先记录前后基线，再提交状态，避免半成品和重复轨迹。
 */
@Service
public class GuardianService {

    private static final DateTimeFormatter HANDOVER_NO = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MemberRepository memberRepository;
    private final GuardianAuthorizationRepository authorizationRepository;
    private final GuardianAuthorizationVersionRepository versionRepository;
    private final GuardianAuthorizationHistoryRepository authHistoryRepository;
    private final GuardianHandoverRepository handoverRepository;
    private final GuardianHandoverResourceRepository handoverResourceRepository;
    private final GuardianHandoverTrailRepository handoverTrailRepository;
    private final CourseRepository courseRepository;
    private final LaneRepository laneRepository;
    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public GuardianService(MemberRepository memberRepository,
            GuardianAuthorizationRepository authorizationRepository,
            GuardianAuthorizationVersionRepository versionRepository,
            GuardianAuthorizationHistoryRepository authHistoryRepository,
            GuardianHandoverRepository handoverRepository,
            GuardianHandoverResourceRepository handoverResourceRepository,
            GuardianHandoverTrailRepository handoverTrailRepository,
            CourseRepository courseRepository,
            LaneRepository laneRepository,
            EquipmentRepository equipmentRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.memberRepository = memberRepository;
        this.authorizationRepository = authorizationRepository;
        this.versionRepository = versionRepository;
        this.authHistoryRepository = authHistoryRepository;
        this.handoverRepository = handoverRepository;
        this.handoverResourceRepository = handoverResourceRepository;
        this.handoverTrailRepository = handoverTrailRepository;
        this.courseRepository = courseRepository;
        this.laneRepository = laneRepository;
        this.equipmentRepository = equipmentRepository;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<GuardianDtos.MinorOption> minorOptions() {
        return memberRepository.findByStatusOrderByIdAsc(Member.STATUS_ACTIVE).stream()
                .filter(MemberService::isMinor)
                .map(m -> new GuardianDtos.MinorOption(m.getId(), m.getCardNo(), m.getName(), m.getBirthDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GuardianDtos.AuthorizationDetail detailByMinor(Long minorId) {
        requireMinor(minorId);
        GuardianAuthorization auth = authorizationRepository.findByMinorId(minorId)
                .orElseThrow(() -> new BizException("该未成年会员尚未建立监护授权档案"));
        return toAuthDetail(auth);
    }

    @Transactional(readOnly = true)
    public GuardianDtos.AuthorizationDetail detail(Long authorizationId) {
        return toAuthDetail(requireAuth(authorizationId));
    }

    @Transactional(readOnly = true)
    public List<GuardianDtos.HandoverView> listHandovers(Long minorId) {
        if (minorId != null) {
            requireMinor(minorId);
            return handoverRepository.findByMinorIdOrderByIdDesc(minorId).stream().map(this::toHandoverView).toList();
        }
        return handoverRepository.findAll().stream()
                .sorted(Comparator.comparing(GuardianHandover::getId).reversed())
                .map(this::toHandoverView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuardianDtos.HandoverView> openHandovers() {
        return handoverRepository.findByStageNotOrderByIdDesc(GuardianHandover.STAGE_RETURNED).stream()
                .map(this::toHandoverView).toList();
    }

    @Transactional(readOnly = true)
    public GuardianDtos.HandoverView handoverDetail(Long id) {
        return toHandoverView(requireHandover(id));
    }

    // ==================== 授权版本生命周期 ====================

    @Transactional
    public GuardianDtos.AuthorizationDetail createDraft(GuardianDtos.AuthorizationSaveReq req) {
        Member minor = requireMinor(req.minorId());
        GuardianAuthorization auth = authorizationRepository.findByMinorIdForUpdate(req.minorId()).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            GuardianAuthorization created = new GuardianAuthorization();
            created.setMinorId(minor.getId());
            created.setMinorName(minor.getName());
            created.setCreatedBy(req.operator());
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            return authorizationRepository.save(created);
        });
        if (hasOpenVersion(auth.getId())) {
            throw new BizException("已存在草稿或待监护人确认版本：请先提交并由监护人确认，不能并行建版本");
        }
        int nextNo = nextVersionNo(auth.getId());
        GuardianAuthorizationVersion version = new GuardianAuthorizationVersion();
        version.setAuthorizationId(auth.getId());
        version.setMinorId(minor.getId());
        version.setVersionNo(nextNo);
        version.setStatus(GuardianAuthorizationVersion.STATUS_DRAFT);
        version.setCreatedBy(req.operator());
        version.setCreatedAt(LocalDateTime.now());
        applyVersionContent(version, req.guardianName(), req.guardianPhone(), req.effectiveDate(),
                req.terminationDate(), req.emergencyContactName(), req.emergencyContactPhone(), req.remarks(),
                req.courseIds(), req.laneIds(), req.equipmentIds(), req.caretakerNames());
        version = versionRepository.save(version);
        addAuthHistory(auth, version, "DRAFT_CREATED", null, version.getStatus(),
                null, versionBaseline(version), req.operator(),
                req.reason() == null || req.reason().isBlank() ? "建立监护授权草稿" : req.reason());
        touch(auth);
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail updateDraft(GuardianDtos.DraftUpdateReq req) {
        LockedVersionAuth locked = lockVersionWithAuth(req.versionId());
        GuardianAuthorizationVersion version = locked.version();
        GuardianAuthorization auth = locked.auth();
        ensureStatus(version, GuardianAuthorizationVersion.STATUS_DRAFT, "修改草稿");
        String beforeJson = versionBaseline(version);
        applyVersionContent(version, req.guardianName(), req.guardianPhone(), req.effectiveDate(),
                req.terminationDate(), req.emergencyContactName(), req.emergencyContactPhone(), req.remarks(),
                req.courseIds(), req.laneIds(), req.equipmentIds(), req.caretakerNames());
        version.setStatusChangedBy(req.operator());
        version.setStatusChangedAt(LocalDateTime.now());
        version.setChangeReason(req.reason());
        version = versionRepository.save(version);
        addAuthHistory(auth, version, "DRAFT_UPDATED", GuardianAuthorizationVersion.STATUS_DRAFT,
                GuardianAuthorizationVersion.STATUS_DRAFT, beforeJson, versionBaseline(version), req.operator(),
                req.reason() == null || req.reason().isBlank() ? "修改草稿内容" : req.reason());
        touch(auth);
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail submitForConfirmation(Long versionId, String operator, String reason) {
        LockedVersionAuth locked = lockVersionWithAuth(versionId);
        GuardianAuthorizationVersion version = locked.version();
        GuardianAuthorization auth = locked.auth();
        ensureStatus(version, GuardianAuthorizationVersion.STATUS_DRAFT, "提交监护人确认");
        LocalDateTime now = LocalDateTime.now();
        String from = version.getStatus();
        String beforeJson = versionBaseline(version);
        version.setStatus(GuardianAuthorizationVersion.STATUS_PENDING);
        version.setSubmittedAt(now);
        version.setStatusChangedAt(now);
        version.setStatusChangedBy(operator);
        version.setChangeReason(reason);
        versionRepository.save(version);
        addAuthHistory(auth, version, "SUBMIT_CONFIRMATION", from,
                GuardianAuthorizationVersion.STATUS_PENDING, beforeJson, versionBaseline(version), operator,
                blank(reason, "提交监护人确认"));
        touch(auth);
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail confirm(Long versionId, String guardianConfirmer, String reason) {
        LockedVersionAuth locked = lockVersionWithAuth(versionId);
        GuardianAuthorizationVersion version = locked.version();
        GuardianAuthorization auth = locked.auth();
        if (!GuardianAuthorizationVersion.STATUS_PENDING.equals(version.getStatus())) {
            throw new BizException("只有待监护人确认的版本可确认；当前 v" + version.getVersionNo()
                    + " 为「" + authStatusName(version.getStatus()) + "」，拒绝重复或过期确认");
        }
        LocalDate today = LocalDate.now();
        if (version.getTerminationDate().isBefore(today)) {
            throw new BizException("授权终止日已过，不能确认生效，请修订日期后重新提交");
        }

        String from = version.getStatus();
        String beforeJson = versionBaseline(version);
        LocalDateTime now = LocalDateTime.now();
        // 新版本确认是整体切换：旧有效 / 暂停版本只保留查询，不再作为放行基线。
        Long oldCurrentId = auth.getCurrentVersionId();
        GuardianAuthorizationVersion oldCurrent = oldCurrentId == null ? null
                : versionRepository.findByIdForUpdate(oldCurrentId).orElse(null);
        String oldBeforeJson = oldCurrent == null ? null : versionBaseline(oldCurrent);
        String oldFromStatus = oldCurrent == null ? null : oldCurrent.getStatus();
        if (oldCurrent != null && isActiveStoredStatus(oldCurrent.getStatus())) {
            oldCurrent.setStatus("SUPERSEDED");
            oldCurrent.setStatusChangedAt(now);
            oldCurrent.setStatusChangedBy(guardianConfirmer);
            oldCurrent.setChangeReason("v" + version.getVersionNo() + " 经监护人确认后成为当前基线");
            versionRepository.save(oldCurrent);
        }

        version.setStatus(GuardianAuthorizationVersion.STATUS_VALID);
        version.setConfirmedAt(now);
        version.setConfirmedBy(guardianConfirmer);
        version.setStatusChangedAt(now);
        version.setStatusChangedBy(guardianConfirmer);
        version.setChangeReason(reason);
        versionRepository.save(version);
        auth.setCurrentVersionId(version.getId());
        touch(auth);

        if (oldCurrent != null) {
            addAuthHistory(auth, oldCurrent, "SUPERSEDE_OLD", oldFromStatus, "SUPERSEDED",
                    oldBeforeJson, versionBaseline(oldCurrent), guardianConfirmer,
                    blank(reason, "监护人确认授权，旧版本停止放行"));
        }
        addAuthHistory(auth, version, "GUARDIAN_CONFIRM", from,
                GuardianAuthorizationVersion.STATUS_VALID, beforeJson, versionBaseline(version), guardianConfirmer,
                blank(reason, "监护人确认授权"));

        // 待接管单仅在版本变化时变为陈旧；接管后的现场单立即冻结未开始动作。
        for (GuardianHandover handover : openHandoversForUpdate(minorId(version))) {
            boolean afterTakeover = !GuardianHandover.STAGE_WAITING_TAKEOVER.equals(handover.getStage());
            String freezeReason = "监护人确认 v" + version.getVersionNo() + "，现场基线已变化，需按新版本重新核对";
            if (afterTakeover && !handover.getAuthorizationVersionId().equals(version.getId())) {
                markFrozen(handover, version, "BASELINE_SUPERSEDED", freezeReason, guardianConfirmer, reason);
            } else if (!afterTakeover && !handover.getAuthorizationVersionId().equals(version.getId())) {
                // 接管尚未发生，没有现场事实；待接管单直接切到监护人刚确认的新基线并留痕。
                Integer oldNo = handover.getVersionNo();
                String before = handoverBaseline(handover);
                handover.setAuthorizationVersionId(version.getId());
                handover.setVersionNo(version.getVersionNo());
                handover.setBaselineJson(versionBaseline(version));
                addHandoverTrail(handover, version, "BASELINE_REFRESHED",
                        handover.getStage(), handover.getStage(), before, handoverBaseline(handover),
                        guardianConfirmer, "接管前授权更新为 v" + version.getVersionNo()
                                + "，原待接管单 v" + oldNo + " 未产生现场事实");
            }
        }
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail revise(Long authorizationId, GuardianDtos.RevisionReq req) {
        GuardianAuthorization auth = lockAuth(authorizationId);
        GuardianAuthorizationVersion current = currentVersion(auth);
        if (!GuardianAuthorizationVersion.STATUS_VALID.equals(current.getStatus())
                && !GuardianAuthorizationVersion.STATUS_PAUSED.equals(current.getStatus())) {
            throw new BizException("只有有效或暂停授权可发起修订；撤销 / 过期授权需重新建档");
        }
        if (hasOpenVersion(auth.getId())) {
            throw new BizException("已存在草稿或待监护人确认版本，不能再发起修订");
        }
        GuardianAuthorizationVersion draft = copyVersion(auth, current, req.operator());
        applyVersionContent(draft, req.guardianName(), req.guardianPhone(), req.effectiveDate(),
                req.terminationDate(), req.emergencyContactName(), req.emergencyContactPhone(), req.remarks(),
                req.courseIds(), req.laneIds(), req.equipmentIds(), req.caretakerNames());
        draft.setChangeReason(req.reason());
        String beforeJson = versionBaseline(current);
        draft = versionRepository.save(draft);
        addAuthHistory(auth, draft, "REVISE_DRAFT", current.getStatus(),
                GuardianAuthorizationVersion.STATUS_DRAFT, beforeJson, versionBaseline(draft), req.operator(), req.reason());
        touch(auth);
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail pause(Long versionId, String operator, String reason) {
        LockedVersionAuth locked = lockVersionWithAuth(versionId);
        GuardianAuthorizationVersion version = locked.version();
        GuardianAuthorization auth = locked.auth();
        ensureCurrent(auth, version);
        ensureStatus(version, GuardianAuthorizationVersion.STATUS_VALID, "暂停授权");
        String from = version.getStatus();
        String beforeJson = versionBaseline(version);
        version.setStatus(GuardianAuthorizationVersion.STATUS_PAUSED);
        version.setStatusChangedAt(LocalDateTime.now());
        version.setStatusChangedBy(operator);
        version.setChangeReason(reason);
        versionRepository.save(version);
        addAuthHistory(auth, version, "PAUSE", from, GuardianAuthorizationVersion.STATUS_PAUSED,
                beforeJson, versionBaseline(version), operator, blank(reason, "暂停监护授权"));
        freezeOpenHandovers(version, "AUTHORIZATION_PAUSED", "当前授权已暂停，未开始动作立即冻结", operator, reason);
        touch(auth);
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail resume(Long versionId, String guardianConfirmer, String reason) {
        LockedVersionAuth locked = lockVersionWithAuth(versionId);
        GuardianAuthorizationVersion version = locked.version();
        GuardianAuthorization auth = locked.auth();
        ensureCurrent(auth, version);
        ensureStatus(version, GuardianAuthorizationVersion.STATUS_PAUSED, "恢复授权");
        if (version.getTerminationDate().isBefore(LocalDate.now())) {
            throw new BizException("授权终止日已过，不能恢复，请修订并重新确认");
        }
        String from = version.getStatus();
        String beforeJson = versionBaseline(version);
        version.setStatus(GuardianAuthorizationVersion.STATUS_VALID);
        version.setStatusChangedAt(LocalDateTime.now());
        version.setStatusChangedBy(guardianConfirmer);
        version.setChangeReason(reason);
        versionRepository.save(version);
        addAuthHistory(auth, version, "RESUME", from, GuardianAuthorizationVersion.STATUS_VALID,
                beforeJson, versionBaseline(version), guardianConfirmer, blank(reason, "监护人确认恢复授权"));
        for (GuardianHandover handover : openHandoversForUpdate(minorId(version))) {
            if (handover.getAuthorizationVersionId().equals(version.getId()) && handover.isFrozen()) {
                String before = handoverBaseline(handover);
                handover.setFrozen(false);
                handover.setFreezeReason(null);
                handover.setFrozenAt(null);
                addHandoverTrail(handover, version, "FREEZE_CLEARED", handover.getStage(), handover.getStage(),
                        before, handoverBaseline(handover), guardianConfirmer,
                        blank(reason, "授权恢复，工作人员重新核对后可继续未开始动作"));
            }
        }
        touch(auth);
        return toAuthDetail(auth);
    }

    @Transactional
    public GuardianDtos.AuthorizationDetail revoke(Long versionId, String operator, String reason) {
        LockedVersionAuth locked = lockVersionWithAuth(versionId);
        GuardianAuthorizationVersion version = locked.version();
        GuardianAuthorization auth = locked.auth();
        ensureCurrent(auth, version);
        if (!GuardianAuthorizationVersion.STATUS_VALID.equals(version.getStatus())
                && !GuardianAuthorizationVersion.STATUS_PAUSED.equals(version.getStatus())) {
            throw new BizException("只有有效或暂停授权可撤销");
        }
        String from = version.getStatus();
        String beforeJson = versionBaseline(version);
        version.setStatus(GuardianAuthorizationVersion.STATUS_REVOKED);
        version.setStatusChangedAt(LocalDateTime.now());
        version.setStatusChangedBy(operator);
        version.setChangeReason(reason);
        versionRepository.save(version);
        addAuthHistory(auth, version, "REVOKE", from, GuardianAuthorizationVersion.STATUS_REVOKED,
                beforeJson, versionBaseline(version), operator, blank(reason, "撤销监护授权"));
        freezeOpenHandovers(version, "AUTHORIZATION_REVOKED", "当前授权已撤销，只能完成安全交还", operator, reason);
        touch(auth);
        return toAuthDetail(auth);
    }

    // ==================== 现场交接 ====================

    @Transactional
    public GuardianDtos.HandoverView createHandover(GuardianDtos.HandoverCreateReq req) {
        Member minor = requireMinor(req.minorId());
        GuardianAuthorization auth = lockAuthForMinor(minor.getId());
        GuardianAuthorizationVersion version = requireCurrentValid(auth);
        List<GuardianHandover> open = handoverRepository.findOpenByMinorIdForUpdate(minor.getId());
        if (!open.isEmpty()) {
            GuardianHandover last = open.get(0);
            throw new BizException("未成年会员 [" + minor.getName() + "] 存在未完成交接单 " + last.getHandoverNo()
                    + "（" + handoverStageName(last.getStage()) + "），不能静默关闭，也不能开启下一次到馆交接");
        }

        LocalDateTime now = LocalDateTime.now();
        GuardianHandover handover = new GuardianHandover();
        handover.setHandoverNo(nextHandoverNo());
        handover.setMinorId(minor.getId());
        handover.setMinorName(minor.getName());
        handover.setAuthorizationId(auth.getId());
        handover.setAuthorizationVersionId(version.getId());
        handover.setVersionNo(version.getVersionNo());
        handover.setStage(GuardianHandover.STAGE_WAITING_TAKEOVER);
        handover.setBaselineJson(versionBaseline(version));
        handover.setCreatedBy(req.operator());
        handover.setCreatedAt(now);
        handover = handoverRepository.save(handover);
        addHandoverTrail(handover, version, "ARRIVAL_CREATE", null,
                GuardianHandover.STAGE_WAITING_TAKEOVER, null, handover.getBaselineJson(),
                req.operator(), "建立到馆交接单，等待合格接管人现场接管");
        return toHandoverView(handoverRepository.save(handover));
    }

    @Transactional
    public GuardianDtos.HandoverView takeover(GuardianDtos.TakeoverReq req) {
        GuardianHandover handover = lockAuthThenHandover(req.handoverId());
        GuardianAuthorization auth = requireAuthEntity(handover.getAuthorizationId());
        GuardianAuthorizationVersion current = requireCurrentValid(auth);
        if (!req.expectedVersionId().equals(handover.getAuthorizationVersionId())) {
            throw new BizException("接管确认依据的版本 id=" + req.expectedVersionId() + " 已过期；交接单当前基线为 v"
                    + versionNo(handover) + "，请刷新后按当前版本重新核对。交接单=" + handover.getHandoverNo());
        }
        if (!handover.getAuthorizationVersionId().equals(current.getId())) {
            throw blocked(handover, GuardianHandover.STAGE_WAITING_TAKEOVER,
                    "交接单基于 v" + versionNo(handover) + "，当前有效基线为 v" + current.getVersionNo()
                            + "，请先按新版本重新核对后再接管");
        }
        if (!GuardianHandover.STAGE_WAITING_TAKEOVER.equals(handover.getStage())) {
            throw new BizException("交接单 " + handover.getHandoverNo() + " 已处于「"
                    + handoverStageName(handover.getStage()) + "」，重复接管确认被拒绝；依据版本 v"
                    + versionNo(handover));
        }
        String caretaker = req.caretakerName().trim();
        if (!current.getCaretakerNames().contains(caretaker)) {
            throw blocked(handover, GuardianHandover.STAGE_WAITING_TAKEOVER,
                    "实际接管人 [" + caretaker + "] 不在 v" + current.getVersionNo() + " 授权接管名单内");
        }

        String before = handoverBaseline(handover);
        LocalDateTime now = LocalDateTime.now();
        handover.setActualCaretaker(caretaker);
        handover.setCaretakerIdentityNo(req.identityNo().trim());
        handover.setTakenOverBy(req.operator());
        handover.setTakenOverAt(now);
        handover.setStage(GuardianHandover.STAGE_TAKEN_OVER);
        handover.setBaselineJson(versionBaseline(current));
        addHandoverTrail(handover, current, "TAKEOVER",
                GuardianHandover.STAGE_WAITING_TAKEOVER, GuardianHandover.STAGE_TAKEN_OVER,
                before, handoverBaseline(handover), req.operator(),
                "已核对当前有效授权、接管人身份与授权范围；接管人=" + caretaker);
        return toHandoverView(handoverRepository.save(handover));
    }

    @Transactional
    public GuardianDtos.HandoverView rebaseline(GuardianDtos.RebaselineReq req) {
        GuardianHandover handover = lockAuthThenHandover(req.handoverId());
        GuardianAuthorization auth = requireAuthEntity(handover.getAuthorizationId());
        GuardianAuthorizationVersion current = requireCurrentValid(auth);
        if (!req.expectedVersionId().equals(current.getId())) {
            throw new BizException("重新核对必须依据当前有效版本 v" + current.getVersionNo()
                    + "，请求携带的版本已过期或不是当前版本");
        }
        if (GuardianHandover.STAGE_WAITING_RETURN.equals(handover.getStage())
                || GuardianHandover.STAGE_RETURNED.equals(handover.getStage())) {
            throw new BizException("已进入交还阶段，不能再重绑授权基线，只能完成安全交还");
        }
        if (handover.getAuthorizationVersionId().equals(current.getId()) && !handover.isFrozen()) {
            throw new BizException("交接单已经依据当前版本 v" + current.getVersionNo() + "，重复重新核对被拒绝");
        }
        String before = handoverBaseline(handover);
        Integer oldNo = handover.getVersionNo();
        handover.setAuthorizationVersionId(current.getId());
        handover.setVersionNo(current.getVersionNo());
        handover.setBaselineJson(versionBaseline(current));
        handover.setBaselineChangedAfterTakeover(true);
        handover.setFrozen(false);
        handover.setFreezeReason(null);
        handover.setFrozenAt(null);
        addHandoverTrail(handover, current, "REBASELINE", handover.getStage(), handover.getStage(),
                before, handoverBaseline(handover), req.operator(),
                "由 v" + oldNo + " 按新基线 v" + current.getVersionNo() + " 重新核对；" + req.reason());
        return toHandoverView(handoverRepository.save(handover));
    }

    @Transactional
    public GuardianDtos.HandoverView precheckTraining(GuardianDtos.TrainingPrecheckReq req) {
        GuardianHandover handover = lockAuthThenHandover(req.handoverId());
        GuardianAuthorizationVersion version = currentVersionOfHandover(handover);
        if (!GuardianHandover.STAGE_TAKEN_OVER.equals(handover.getStage())) {
            throw blocked(handover, "TRAINING_PRECHECK",
                    "只有已接管且尚未开始训练的交接单可做训练前校验；当前为「"
                            + handoverStageName(handover.getStage()) + "」");
        }
        if (handover.isFrozen()) {
            throw blocked(handover, "TRAINING_PRECHECK", handover.getFreezeReason());
        }
        List<Long> equipmentIds = distinct(req.equipmentIds(), "器材");
        if (equipmentIds.isEmpty()) {
            throw new BizException("训练前必须至少选择一项器材");
        }
        // 三项范围整组校验：任一不存在或未覆盖，不写入任何放行项。
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new BizException("课程不存在：id=" + req.courseId()));
        Lane lane = laneRepository.findById(req.laneId())
                .orElseThrow(() -> new BizException("箭道不存在：id=" + req.laneId()));
        List<Equipment> equipments = equipmentIds.stream()
                .map(id -> equipmentRepository.findById(id)
                        .orElseThrow(() -> new BizException("器材不存在：id=" + id)))
                .toList();
        requireCovered(version, "COURSE", course.getId(), course.getCourseName());
        requireCovered(version, "LANE", lane.getId(), lane.getLaneNo());
        for (Equipment equipment : equipments) {
            requireCovered(version, "EQUIPMENT", equipment.getId(), equipment.getEquipCode());
        }

        Optional<GuardianHandoverResource> duplicateRequest = handover.getResources().stream()
                .filter(r -> req.requestId().equals(r.getRequestId()))
                .findFirst();
        if (duplicateRequest.isPresent()) {
            // 网络重试：原整组结果原样返回，不再新增资源行或轨迹。
            return toHandoverView(handover);
        }
        if (handoverResourceRepository.existsByRequestId(req.requestId())) {
            throw new BizException("幂等请求号 [" + req.requestId() + "] 已用于其他交接或放行请求，拒绝重复放行");
        }
        boolean hasUsed = handover.getResources().stream().anyMatch(r -> "USED".equals(r.getStatus()));
        if (hasUsed) {
            throw new BizException("已有资源实际领用，不能用另一请求号整组替换；请按异常重核流程处理");
        }

        String before = handoverBaseline(handover);
        handover.getResources().clear();
        LocalDateTime now = LocalDateTime.now();
        handover.getResources().add(resource(handover, "COURSE", course.getId(), course.getCourseName(), req.requestId(), now));
        handover.getResources().add(resource(handover, "LANE", lane.getId(), lane.getLaneNo(), req.requestId(), now));
        for (Equipment equipment : equipments) {
            handover.getResources().add(resource(handover, "EQUIPMENT", equipment.getId(),
                    equipment.getEquipCode(), req.requestId(), now));
        }
        addHandoverTrail(handover, version, "TRAINING_PRECHECK",
                GuardianHandover.STAGE_TAKEN_OVER, GuardianHandover.STAGE_TAKEN_OVER,
                before, handoverBaseline(handover), req.operator(),
                "课程、箭道、器材均由 v" + version.getVersionNo() + " 覆盖，整组放行待实际使用");
        return toHandoverView(handoverRepository.save(handover));
    }

    @Transactional
    public GuardianDtos.HandoverView startTraining(Long handoverId, String operator, String reason) {
        GuardianHandover handover = lockAuthThenHandover(handoverId);
        GuardianAuthorizationVersion version = currentVersionOfHandover(handover);
        if (!GuardianHandover.STAGE_TAKEN_OVER.equals(handover.getStage())) {
            throw blocked(handover, "TRAINING_START", "当前阶段不能开始训练：" + handoverStageName(handover.getStage()));
        }
        if (handover.isFrozen()) {
            throw blocked(handover, "TRAINING_START", handover.getFreezeReason());
        }
        List<GuardianHandoverResource> resources = handover.getResources();
        boolean hasCourse = resources.stream().anyMatch(r -> "COURSE".equals(r.getResourceType()));
        boolean hasLane = resources.stream().anyMatch(r -> "LANE".equals(r.getResourceType()));
        boolean hasEquipment = resources.stream().anyMatch(r -> "EQUIPMENT".equals(r.getResourceType()));
        if (!hasCourse || !hasLane || !hasEquipment) {
            throw blocked(handover, "TRAINING_START", "课程、箭道、器材未完成整组训练前校验");
        }
        List<GuardianHandoverResource> notUsed = resources.stream()
                .filter(r -> !"USED".equals(r.getStatus()))
                .toList();
        if (!notUsed.isEmpty()) {
            String names = notUsed.stream().map(GuardianHandoverResource::getResourceName)
                    .collect(Collectors.joining("、"));
            throw blocked(handover, "TRAINING_START", "以下资源已授权但尚未完成实际入场/领用：" + names);
        }
        String before = handoverBaseline(handover);
        handover.setStage(GuardianHandover.STAGE_TRAINING);
        handover.setTrainingStartedBy(operator);
        handover.setTrainingStartedAt(LocalDateTime.now());
        addHandoverTrail(handover, version, "TRAINING_START",
                GuardianHandover.STAGE_TAKEN_OVER, GuardianHandover.STAGE_TRAINING,
                before, handoverBaseline(handover), operator, blank(reason, "训练开始，全部资源复核通过"));
        return toHandoverView(handoverRepository.save(handover));
    }

    @Transactional
    public GuardianDtos.HandoverView finishTraining(Long handoverId, String operator, String reason) {
        GuardianHandover handover = lockAuthThenHandover(handoverId);
        GuardianAuthorizationVersion version = versionRepository.findById(handover.getAuthorizationVersionId()).orElseThrow();
        if (!GuardianHandover.STAGE_TRAINING.equals(handover.getStage())) {
            throw new BizException("只有训练中的交接单可结束训练并等待交还；当前为「"
                    + handoverStageName(handover.getStage()) + "」");
        }
        String before = handoverBaseline(handover);
        handover.setStage(GuardianHandover.STAGE_WAITING_RETURN);
        handover.setWaitingReturnBy(operator);
        handover.setWaitingReturnAt(LocalDateTime.now());
        addHandoverTrail(handover, version, "FINISH_TRAINING",
                GuardianHandover.STAGE_TRAINING, GuardianHandover.STAGE_WAITING_RETURN,
                before, handoverBaseline(handover), operator, blank(reason, "训练结束，等待合格接管人交还确认"));
        return toHandoverView(handoverRepository.save(handover));
    }

    @Transactional
    public GuardianDtos.HandoverView returnMinor(GuardianDtos.ReturnReq req) {
        GuardianHandover handover = lockAuthThenHandover(req.handoverId());
        GuardianAuthorizationVersion baseline = versionRepository.findById(handover.getAuthorizationVersionId())
                .orElseThrow(() -> new BizException("交接单基线版本不存在，无法完成交还"));
        if (GuardianHandover.STAGE_RETURNED.equals(handover.getStage())) {
            throw new BizException("交接单 " + handover.getHandoverNo() + " 已交还，重复交还被拒绝；依据版本 v"
                    + versionNo(handover));
        }
        if (!GuardianHandover.STAGE_TRAINING.equals(handover.getStage())
                && !GuardianHandover.STAGE_WAITING_RETURN.equals(handover.getStage())) {
            throw blocked(handover, "RETURN", "尚未接管或未进入训练/交还阶段，不能交还");
        }
        String caretaker = req.caretakerName().trim();
        // 交还首先按接管时固定的名单核人；当前基线撤销 / 暂停也不能阻止安全交还。
        if (!baseline.getCaretakerNames().contains(caretaker)) {
            throw blocked(handover, "RETURN",
                    "交还人 [" + caretaker + "] 不在接管基线 v" + baseline.getVersionNo() + " 的合格接管名单内");
        }

        String before = handoverBaseline(handover);
        LocalDateTime now = LocalDateTime.now();
        String previousStage = handover.getStage();
        handover.setStage(GuardianHandover.STAGE_RETURNED);
        handover.setReturnedCaretaker(caretaker);
        handover.setReturnedBy(req.operator());
        handover.setReturnedAt(now);
        if (GuardianHandover.STAGE_TRAINING.equals(previousStage)) {
            handover.setWaitingReturnBy(req.operator());
            handover.setWaitingReturnAt(now);
            addHandoverTrail(handover, baseline, "FINISH_TRAINING",
                    GuardianHandover.STAGE_TRAINING, GuardianHandover.STAGE_WAITING_RETURN,
                    before, before, req.operator(), "交还确认时同步结束训练");
        }
        addHandoverTrail(handover, baseline, "RETURNED",
                GuardianHandover.STAGE_WAITING_RETURN, GuardianHandover.STAGE_RETURNED,
                before, handoverBaseline(handover), req.operator(),
                blank(req.reason(), "合格接管人完成离馆交还确认"));
        if (handover.isFrozen() || handover.isBaselineChangedAfterTakeover()) {
            addHandoverTrail(handover, baseline, "EXCEPTION_CLOSED",
                    GuardianHandover.STAGE_RETURNED, GuardianHandover.STAGE_RETURNED,
                    before, handoverBaseline(handover), req.operator(),
                    "授权基线曾在接管后变化，已保留异常处置轨迹并完成安全交还");
        }
        return toHandoverView(handoverRepository.save(handover));
    }

    // ==================== 下游业务门禁 ====================

    /** 课程报名 / 箭道开台 / 器材租借：未成年必须已有整组放行项，成功业务动作时置为 USED。 */
    public void gateResource(Member member, String resourceType, Long resourceId, String action) {
        if (!MemberService.isMinor(member)) {
            return;
        }
        GuardianHandover handover = activeHandoverForGate(member.getId(), action);
        GuardianAuthorizationVersion version = currentVersionOfHandover(handover);
        if (handover.isFrozen()) {
            throw blocked(handover, resourceType + "_RELEASE", handover.getFreezeReason());
        }
        if (!GuardianHandover.STAGE_TAKEN_OVER.equals(handover.getStage())
                && !GuardianHandover.STAGE_TRAINING.equals(handover.getStage())) {
            throw blocked(handover, resourceType + "_RELEASE",
                    "现场交接处于「" + handoverStageName(handover.getStage()) + "」，不能" + action);
        }
        GuardianHandoverResource resource = handover.getResources().stream()
                .filter(item -> resourceType.equals(item.getResourceType()) && resourceId.equals(item.getResourceId()))
                .findFirst()
                .orElseThrow(() -> blocked(handover, resourceType + "_RELEASE",
                        resourceTypeName(resourceType) + "未纳入 v" + version.getVersionNo()
                                + " 本次训练前整组放行清单，不能" + action));
        if (!"CLEARED".equals(resource.getStatus())) {
            throw blocked(handover, resourceType + "_RELEASE", resourceTypeName(resourceType)
                    + "已在本次交接中使用，不能重复" + action);
        }
        resource.setStatus("USED");
        resource.setUsedAt(LocalDateTime.now());
        handoverRepository.save(handover);
    }

    /** 开始计分回合时，训练必须已经开始，课程/箭道/器材都已实际使用，且弓种与领用器材一致。 */
    public void gateRoundStart(Member member, Long laneId, String bowType) {
        if (!MemberService.isMinor(member)) {
            return;
        }
        GuardianHandover handover = activeHandoverForGate(member.getId(), "开始计分训练");
        currentVersionOfHandover(handover);
        if (handover.isFrozen()) {
            throw blocked(handover, "ROUND_START", handover.getFreezeReason());
        }
        if (!GuardianHandover.STAGE_TRAINING.equals(handover.getStage())) {
            throw blocked(handover, "ROUND_START",
                    "训练签到尚未完成（当前：" + handoverStageName(handover.getStage()) + "），不能开始计分回合");
        }
        boolean laneUsed = handover.getResources().stream()
                .anyMatch(r -> "LANE".equals(r.getResourceType()) && laneId.equals(r.getResourceId())
                        && "USED".equals(r.getStatus()));
        boolean courseUsed = handover.getResources().stream()
                .anyMatch(r -> "COURSE".equals(r.getResourceType()) && "USED".equals(r.getStatus()));
        boolean equipmentUsed = handover.getResources().stream()
                .anyMatch(r -> "EQUIPMENT".equals(r.getResourceType()) && "USED".equals(r.getStatus()));
        boolean bowMatched = handover.getResources().stream()
                .filter(r -> "EQUIPMENT".equals(r.getResourceType()) && "USED".equals(r.getStatus()))
                .map(r -> equipmentRepository.findById(r.getResourceId()).map(Equipment::getType).orElse(null))
                .anyMatch(bowType::equals);
        if (!laneUsed || !courseUsed || !equipmentUsed) {
            throw blocked(handover, "ROUND_START", "课程、箭道或器材尚未全部完成实际入场/领用，不能开始计分");
        }
        if (!bowMatched) {
            throw blocked(handover, "ROUND_START", "已领用器材中没有弓种为 " + bowType + " 的器材，不能开始计分");
        }
    }

    // ==================== 内部校验与轨迹 ====================

    private GuardianHandover activeHandoverForGate(Long minorId, String action) {
        GuardianAuthorization auth = authorizationRepository.findByMinorIdForUpdate(minorId)
                .orElseThrow(() -> new BizException("未成年会员缺少监护授权，不能" + action));
        List<GuardianHandover> open = handoverRepository.findOpenByMinorIdForUpdate(minorId);
        if (open.isEmpty()) {
            throw new BizException("未成年会员当前没有进行中的现场交接单，不能" + action
                    + "；请先建立交接、接管并完成训练前校验");
        }
        GuardianHandover handover = open.get(0);
        GuardianAuthorizationVersion current = auth.getCurrentVersionId() == null ? null
                : versionRepository.findByIdForUpdate(auth.getCurrentVersionId()).orElse(null);
        if (current == null || !isCurrentlyValid(current)) {
            markFrozenIfAfterTakeover(handover, current, "AUTHORIZATION_INVALID",
                    "授权当前不是有效状态，未开始动作已冻结，需完成安全交还", "system", action);
            throw blocked(handover, "AUTHORIZATION_BASELINE",
                    "当前授权无效、暂停、撤销或已过期，不能" + action);
        }
        if (!current.getId().equals(handover.getAuthorizationVersionId())) {
            markFrozenIfAfterTakeover(handover, current, "BASELINE_SUPERSEDED",
                    "当前有效授权已切换到 v" + current.getVersionNo() + "，需按新基线重新核对", "system", action);
            throw blocked(handover, "AUTHORIZATION_BASELINE",
                    "现场交接基于 v" + handover.getVersionNo() + "，当前授权为 v" + current.getVersionNo()
                            + "，不能" + action);
        }
        return handover;
    }

    private void freezeOpenHandovers(GuardianAuthorizationVersion version, String action, String reason,
            String operator, String inputReason) {
        for (GuardianHandover handover : openHandoversForUpdate(minorId(version))) {
            if (!GuardianHandover.STAGE_WAITING_TAKEOVER.equals(handover.getStage())) {
                markFrozen(handover, version, action, reason, operator, inputReason);
            }
        }
    }

    private void markFrozenIfAfterTakeover(GuardianHandover handover, GuardianAuthorizationVersion version,
            String action, String reason, String operator, String inputReason) {
        if (!GuardianHandover.STAGE_WAITING_TAKEOVER.equals(handover.getStage()) && !handover.isFrozen()) {
            markFrozen(handover, version, action, reason, operator, inputReason);
        }
    }

    private void markFrozen(GuardianHandover handover, GuardianAuthorizationVersion version, String action,
            String reason, String operator, String inputReason) {
        String before = handoverBaseline(handover);
        handover.setFrozen(true);
        handover.setBaselineChangedAfterTakeover(true);
        handover.setFreezeReason(reason);
        handover.setFrozenAt(LocalDateTime.now());
        addHandoverTrail(handover, version, action, handover.getStage(), handover.getStage(),
                before, handoverBaseline(handover), operator, reason + "；触发动作：" + blank(inputReason, "-"));
    }

    private void addAuthHistory(GuardianAuthorization auth, GuardianAuthorizationVersion version, String action,
            String from, String to, String operator, String reason) {
        addAuthHistory(auth, version, action, from, to, versionBaseline(version), versionBaseline(version), operator, reason);
    }

    private void addAuthHistory(GuardianAuthorization auth, GuardianAuthorizationVersion version, String action,
            String from, String to, String beforeJson, String afterJson, String operator, String reason) {
        GuardianAuthorizationHistory history = new GuardianAuthorizationHistory();
        history.setAuthorizationId(auth.getId());
        history.setVersionId(version.getId());
        history.setVersionNo(version.getVersionNo());
        history.setAction(action);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setBaselineBeforeJson(beforeJson);
        history.setBaselineAfterJson(afterJson);
        history.setOperator(operator);
        history.setOperatedAt(LocalDateTime.now());
        history.setReason(reason);
        authHistoryRepository.save(history);
    }

    private void addHandoverTrail(GuardianHandover handover, GuardianAuthorizationVersion version, String action,
            String fromStage, String toStage, String before, String after, String operator, String reason) {
        GuardianHandoverTrail trail = new GuardianHandoverTrail();
        trail.setHandoverId(handover.getId());
        trail.setAction(action);
        trail.setFromStage(fromStage);
        trail.setToStage(toStage);
        trail.setVersionId(version.getId());
        trail.setVersionNo(version.getVersionNo());
        trail.setBaselineBeforeJson(before);
        trail.setBaselineAfterJson(after);
        trail.setOperator(operator);
        trail.setOperatedAt(LocalDateTime.now());
        trail.setReason(reason);
        handover.getTrails().add(trail);
        if (handover.getId() != null) {
            handoverRepository.save(handover);
        }
    }

    private GuardianHandoverResource resource(GuardianHandover handover, String type, Long id, String name,
            String requestId, LocalDateTime now) {
        GuardianHandoverResource item = new GuardianHandoverResource();
        item.setHandoverId(handover.getId());
        item.setResourceType(type);
        item.setResourceId(id);
        item.setResourceName(name);
        item.setStatus("CLEARED");
        item.setRequestId(requestId);
        item.setClearedAt(now);
        return item;
    }

    private void applyVersionContent(GuardianAuthorizationVersion v, String guardianName, String guardianPhone,
            LocalDate effectiveDate, LocalDate terminationDate, String emergencyName, String emergencyPhone,
            String remarks, List<Long> courses, List<Long> lanes, List<Long> equipments, List<String> caretakers) {
        if (effectiveDate == null || terminationDate == null || effectiveDate.isAfter(terminationDate)) {
            throw new BizException("生效日不能晚于终止日");
        }
        Set<Long> courseIds = distinct(courses, "课程");
        Set<Long> laneIds = distinct(lanes, "箭道");
        Set<Long> equipmentIds = distinct(equipments, "器材");
        Set<String> names = caretakers == null ? Set.of() : caretakers.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (courseIds.isEmpty() || laneIds.isEmpty() || equipmentIds.isEmpty() || names.isEmpty()) {
            throw new BizException("课程、箭道、器材范围和现场接管人员均不能为空");
        }
        courseIds.forEach(id -> courseRepository.findById(id).orElseThrow(() -> new BizException("课程不存在：id=" + id)));
        laneIds.forEach(id -> laneRepository.findById(id).orElseThrow(() -> new BizException("箭道不存在：id=" + id)));
        equipmentIds.forEach(id -> equipmentRepository.findById(id)
                .orElseThrow(() -> new BizException("器材不存在：id=" + id)));

        v.setGuardianName(requireText(guardianName, "监护人姓名"));
        v.setGuardianPhone(requireText(guardianPhone, "监护人手机号"));
        v.setEffectiveDate(effectiveDate);
        v.setTerminationDate(terminationDate);
        v.setEmergencyContactName(requireText(emergencyName, "紧急联系人姓名"));
        v.setEmergencyContactPhone(requireText(emergencyPhone, "紧急联系人手机号"));
        v.setRemarks(remarks == null ? null : remarks.trim());
        v.setCourseIds(courseIds);
        v.setLaneIds(laneIds);
        v.setEquipmentIds(equipmentIds);
        v.setCaretakerNames(names);
    }

    private GuardianAuthorizationVersion copyVersion(GuardianAuthorization auth,
            GuardianAuthorizationVersion source, String operator) {
        GuardianAuthorizationVersion copy = new GuardianAuthorizationVersion();
        copy.setAuthorizationId(auth.getId());
        copy.setMinorId(source.getMinorId());
        copy.setVersionNo(nextVersionNo(auth.getId()));
        copy.setStatus(GuardianAuthorizationVersion.STATUS_DRAFT);
        copy.setGuardianName(source.getGuardianName());
        copy.setGuardianPhone(source.getGuardianPhone());
        copy.setEffectiveDate(source.getEffectiveDate());
        copy.setTerminationDate(source.getTerminationDate());
        copy.setEmergencyContactName(source.getEmergencyContactName());
        copy.setEmergencyContactPhone(source.getEmergencyContactPhone());
        copy.setRemarks(source.getRemarks());
        copy.setCourseIds(new LinkedHashSet<>(source.getCourseIds()));
        copy.setLaneIds(new LinkedHashSet<>(source.getLaneIds()));
        copy.setEquipmentIds(new LinkedHashSet<>(source.getEquipmentIds()));
        copy.setCaretakerNames(new LinkedHashSet<>(source.getCaretakerNames()));
        copy.setCreatedBy(operator);
        copy.setCreatedAt(LocalDateTime.now());
        return copy;
    }

    private void requireCovered(GuardianAuthorizationVersion version, String type, Long id, String name) {
        boolean covered = switch (type) {
            case "COURSE" -> version.getCourseIds().contains(id);
            case "LANE" -> version.getLaneIds().contains(id);
            case "EQUIPMENT" -> version.getEquipmentIds().contains(id);
            default -> false;
        };
        if (!covered) {
            throw new BizException(resourceTypeName(type) + " [" + name + "] 不在授权 v"
                    + version.getVersionNo() + " 允许范围内，整组训练校验失败，未产生任何放行项");
        }
    }

    private GuardianHandover lockAuthThenHandover(Long handoverId) {
        GuardianHandover handover = requireHandover(handoverId);
        lockAuthForMinor(handover.getMinorId());
        return handoverRepository.findByIdForUpdate(handoverId).orElseThrow();
    }

    private List<GuardianHandover> openHandoversForUpdate(Long minorId) {
        return handoverRepository.findOpenByMinorIdForUpdate(minorId);
    }

    private Member requireMinor(Long minorId) {
        Member member = memberRepository.findById(minorId)
                .orElseThrow(() -> new BizException("会员不存在：id=" + minorId));
        memberServiceRejectMerged(member);
        if (!MemberService.isMinor(member)) {
            throw new BizException("会员 [" + member.getName() + "] 不是未成年会员，现有成年流程无需监护授权");
        }
        return member;
    }

    private void memberServiceRejectMerged(Member member) {
        if (Member.STATUS_MERGED.equals(member.getStatus())) {
            throw new BizException("会员卡 [" + member.getCardNo() + "] 已合并封存，不能办理监护授权");
        }
    }

    private GuardianAuthorization lockAuth(Long id) {
        return authorizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("监护授权档案不存在：id=" + id));
    }

    private GuardianAuthorization lockAuthForMinor(Long minorId) {
        return authorizationRepository.findByMinorIdForUpdate(minorId)
                .orElseThrow(() -> new BizException("该未成年会员尚未建立监护授权档案"));
    }

    private GuardianAuthorization requireAuthEntity(Long id) {
        return authorizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("监护授权档案不存在：id=" + id));
    }

    private GuardianAuthorization requireAuth(Long id) {
        return authorizationRepository.findById(id)
                .orElseThrow(() -> new BizException("监护授权档案不存在：id=" + id));
    }

    private GuardianAuthorizationVersion lockVersion(Long id) {
        return versionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("授权版本不存在：id=" + id));
    }

    private LockedVersionAuth lockVersionWithAuth(Long id) {
        GuardianAuthorizationVersion read = versionRepository.findById(id)
                .orElseThrow(() -> new BizException("授权版本不存在：id=" + id));
        GuardianAuthorization auth = lockAuth(read.getAuthorizationId());
        GuardianAuthorizationVersion version = lockVersion(id);
        if (!version.getAuthorizationId().equals(auth.getId())) {
            throw new BizException("授权版本与授权档案不一致，请刷新后重试");
        }
        return new LockedVersionAuth(auth, version);
    }

    private record LockedVersionAuth(GuardianAuthorization auth, GuardianAuthorizationVersion version) {
    }

    private GuardianHandover requireHandover(Long id) {
        return handoverRepository.findById(id)
                .orElseThrow(() -> new BizException("现场交接单不存在：id=" + id));
    }

    private GuardianAuthorizationVersion currentVersion(GuardianAuthorization auth) {
        if (auth.getCurrentVersionId() == null) {
            throw new BizException("尚无监护人确认生效的授权版本");
        }
        return versionRepository.findByIdForUpdate(auth.getCurrentVersionId())
                .orElseThrow(() -> new BizException("当前授权版本不存在"));
    }

    private GuardianAuthorizationVersion requireCurrentValid(GuardianAuthorization auth) {
        GuardianAuthorizationVersion version = currentVersion(auth);
        if (!isCurrentlyValid(version)) {
            throw new BizException("当前授权 v" + version.getVersionNo() + " 为「"
                    + authStatusName(effectiveStatus(version)) + "」，不能办理现场交接");
        }
        return version;
    }

    private GuardianAuthorizationVersion currentVersionOfHandover(GuardianHandover handover) {
        GuardianAuthorization auth = requireAuthEntity(handover.getAuthorizationId());
        GuardianAuthorizationVersion current = currentVersion(auth);
        if (!isCurrentlyValid(current)) {
            markFrozenIfAfterTakeover(handover, current, "AUTHORIZATION_INVALID",
                    "授权已暂停、撤销或过期，未开始动作冻结", "system", "业务门禁");
            throw blocked(handover, "AUTHORIZATION_BASELINE",
                    "授权 v" + current.getVersionNo() + " 当前为「" + authStatusName(effectiveStatus(current))
                            + "」，已拦截后续放行");
        }
        if (!current.getId().equals(handover.getAuthorizationVersionId())) {
            markFrozenIfAfterTakeover(handover, current, "BASELINE_SUPERSEDED",
                    "授权基线已切换到 v" + current.getVersionNo() + "，需重新核对", "system", "业务门禁");
            throw blocked(handover, "AUTHORIZATION_BASELINE",
                    "现场基线 v" + handover.getVersionNo() + " 已过期，当前为 v" + current.getVersionNo());
        }
        return current;
    }

    private boolean isCurrentlyValid(GuardianAuthorizationVersion version) {
        return GuardianAuthorizationVersion.STATUS_VALID.equals(version.getStatus())
                && !version.getEffectiveDate().isAfter(LocalDate.now())
                && !version.getTerminationDate().isBefore(LocalDate.now());
    }

    private boolean isActiveStoredStatus(String status) {
        return GuardianAuthorizationVersion.STATUS_VALID.equals(status)
                || GuardianAuthorizationVersion.STATUS_PAUSED.equals(status);
    }

    private void ensureStatus(GuardianAuthorizationVersion version, String expected, String action) {
        if (!expected.equals(version.getStatus())) {
            throw new BizException("v" + version.getVersionNo() + " 当前为「"
                    + authStatusName(version.getStatus()) + "」，不能" + action);
        }
    }

    private void ensureCurrent(GuardianAuthorization auth, GuardianAuthorizationVersion version) {
        if (!Objects.equals(auth.getCurrentVersionId(), version.getId())) {
            throw new BizException("v" + version.getVersionNo() + " 不是当前授权版本，旧版本只能查询，不能继续放行或变更");
        }
    }

    private boolean hasOpenVersion(Long authId) {
        return versionRepository.findByAuthorizationIdOrderByVersionNoDescIdDesc(authId).stream()
                .anyMatch(v -> GuardianAuthorizationVersion.STATUS_DRAFT.equals(v.getStatus())
                        || GuardianAuthorizationVersion.STATUS_PENDING.equals(v.getStatus()));
    }

    private int nextVersionNo(Long authId) {
        return versionRepository.findByAuthorizationIdOrderByVersionNoDescIdDesc(authId).stream()
                .findFirst().map(v -> v.getVersionNo() + 1).orElse(1);
    }

    private String nextHandoverNo() {
        String prefix = "H" + LocalDateTime.now().format(HANDOVER_NO);
        String no = prefix;
        int seq = 1;
        while (handoverRepository.existsByHandoverNo(no)) {
            no = prefix + "-" + seq;
            seq++;
        }
        return no;
    }

    private void touch(GuardianAuthorization auth) {
        auth.setUpdatedAt(LocalDateTime.now());
        authorizationRepository.save(auth);
    }

    private <T> Set<T> distinct(List<T> values, String label) {
        if (values == null) {
            throw new BizException(label + "范围不能为空");
        }
        Set<T> set = values.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (set.size() != values.size()) {
            throw new BizException(label + "范围存在重复项，授权范围必须去重后整体提交");
        }
        return set;
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BizException(label + "不能为空");
        }
        return value.trim();
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private BizException blocked(GuardianHandover handover, String stage, String reason) {
        recordBlock(handover, stage, reason);
        return new BizException("已拦截 [" + stage + "]：" + reason + "；交接单=" + handover.getHandoverNo()
                + "，授权基线=v" + versionNo(handover));
    }

    private void recordBlock(GuardianHandover handover, String stage, String reason) {
        if (handover == null || handover.getId() == null) {
            return;
        }
        GuardianHandoverTrail trail = new GuardianHandoverTrail();
        trail.setAction("BLOCKED");
        trail.setFromStage(handover.getStage());
        trail.setToStage(handover.getStage());
        trail.setVersionId(handover.getAuthorizationVersionId());
        trail.setVersionNo(handover.getVersionNo());
        trail.setBaselineBeforeJson(handoverBaseline(handover));
        trail.setBaselineAfterJson(handoverBaseline(handover));
        trail.setBlockedStage(stage);
        trail.setBlockReason(reason);
        trail.setOperator("SYSTEM");
        trail.setOperatedAt(LocalDateTime.now());
        trail.setReason(reason);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> handoverTrailRepository.save(trail));
    }

    private Long minorId(GuardianAuthorizationVersion version) {
        return version.getMinorId();
    }

    private int versionNo(GuardianHandover handover) {
        return handover.getVersionNo() == null ? 0 : handover.getVersionNo();
    }

    private String versionBaseline(GuardianAuthorizationVersion v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("versionId", v.getId());
        map.put("versionNo", v.getVersionNo());
        map.put("status", effectiveStatus(v));
        map.put("guardian", v.getGuardianName() + " " + v.getGuardianPhone());
        map.put("effectiveDate", v.getEffectiveDate());
        map.put("terminationDate", v.getTerminationDate());
        map.put("courseIds", sorted(v.getCourseIds()));
        map.put("laneIds", sorted(v.getLaneIds()));
        map.put("equipmentIds", sorted(v.getEquipmentIds()));
        map.put("caretakerNames", v.getCaretakerNames().stream().sorted().toList());
        map.put("emergencyContact", v.getEmergencyContactName() + " " + v.getEmergencyContactPhone());
        return json(map);
    }

    private String handoverBaseline(GuardianHandover h) {
        if (h.getBaselineJson() != null) {
            return h.getBaselineJson();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("versionId", h.getAuthorizationVersionId());
        map.put("versionNo", h.getVersionNo());
        map.put("stage", h.getStage());
        return json(map);
    }

    private List<Long> sorted(Set<Long> values) {
        return values.stream().sorted().toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("授权基线快照生成失败：" + e.getMessage());
        }
    }

    private String effectiveStatus(GuardianAuthorizationVersion v) {
        if (GuardianAuthorizationVersion.STATUS_VALID.equals(v.getStatus())
                && (v.getEffectiveDate().isAfter(LocalDate.now()) || v.getTerminationDate().isBefore(LocalDate.now()))) {
            return GuardianAuthorizationVersion.STATUS_EXPIRED;
        }
        return v.getStatus();
    }

    private String authStatusName(String status) {
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "PENDING" -> "待监护人确认";
            case "VALID" -> "有效";
            case "PAUSED" -> "暂停";
            case "REVOKED" -> "撤销";
            case "EXPIRED" -> "已过期";
            case "SUPERSEDED" -> "已被新版本取代";
            default -> status;
        };
    }

    private String handoverStageName(String stage) {
        return switch (stage) {
            case GuardianHandover.STAGE_WAITING_TAKEOVER -> "待接管";
            case GuardianHandover.STAGE_TAKEN_OVER -> "已接管/待训练";
            case GuardianHandover.STAGE_TRAINING -> "训练中";
            case GuardianHandover.STAGE_WAITING_RETURN -> "待交还";
            case GuardianHandover.STAGE_RETURNED -> "已交还";
            default -> stage;
        };
    }

    private String resourceTypeName(String type) {
        return switch (type) {
            case "COURSE" -> "课程";
            case "LANE" -> "箭道";
            case "EQUIPMENT" -> "器材";
            default -> type;
        };
    }

    // ==================== DTO ====================

    private GuardianDtos.AuthorizationDetail toAuthDetail(GuardianAuthorization auth) {
        List<GuardianAuthorizationVersion> versions =
                versionRepository.findByAuthorizationIdOrderByVersionNoDescIdDesc(auth.getId());
        List<GuardianDtos.VersionView> versionViews = versions.stream()
                .map(v -> toVersionView(v, v.getId().equals(auth.getCurrentVersionId())))
                .toList();
        List<GuardianDtos.HistoryView> history =
                authHistoryRepository.findByAuthorizationIdOrderByIdAsc(auth.getId()).stream()
                        .map(this::toHistoryView).toList();
        Member minor = memberRepository.findById(auth.getMinorId()).orElse(null);
        return new GuardianDtos.AuthorizationDetail(
                auth.getId(), auth.getMinorId(), minor == null ? auth.getMinorName() : minor.getName(),
                auth.getCurrentVersionId(), auth.getCreatedBy(), auth.getCreatedAt(), versionViews, history);
    }

    private GuardianDtos.VersionView toVersionView(GuardianAuthorizationVersion v, boolean current) {
        List<GuardianDtos.NameId> courses = v.getCourseIds().stream().sorted()
                .map(id -> new GuardianDtos.NameId(id, courseRepository.findById(id)
                        .map(Course::getCourseName).orElse("课程#" + id))).toList();
        List<GuardianDtos.NameId> lanes = v.getLaneIds().stream().sorted()
                .map(id -> new GuardianDtos.NameId(id, laneRepository.findById(id)
                        .map(Lane::getLaneNo).orElse("箭道#" + id))).toList();
        List<GuardianDtos.NameId> equipment = v.getEquipmentIds().stream().sorted()
                .map(id -> new GuardianDtos.NameId(id, equipmentRepository.findById(id)
                        .map(Equipment::getEquipCode).orElse("器材#" + id))).toList();
        String status = effectiveStatus(v);
        return new GuardianDtos.VersionView(
                v.getId(), v.getAuthorizationId(), v.getVersionNo(), status, authStatusName(status),
                v.getGuardianName(), v.getGuardianPhone(), v.getEffectiveDate(), v.getTerminationDate(),
                v.getEmergencyContactName(), v.getEmergencyContactPhone(), v.getRemarks(),
                new GuardianDtos.ScopeView(courses, lanes, equipment, new ArrayList<>(v.getCaretakerNames())),
                v.getCreatedBy(), v.getCreatedAt(), v.getSubmittedAt(), v.getConfirmedAt(), v.getConfirmedBy(),
                v.getChangeReason(), current);
    }

    private GuardianDtos.HistoryView toHistoryView(GuardianAuthorizationHistory h) {
        return new GuardianDtos.HistoryView(h.getId(), h.getVersionId(), h.getVersionNo(), h.getAction(),
                h.getFromStatus(), h.getToStatus(), h.getBaselineBeforeJson(), h.getBaselineAfterJson(),
                h.getOperator(), h.getOperatedAt(), h.getReason());
    }

    private GuardianDtos.HandoverView toHandoverView(GuardianHandover h) {
        List<GuardianDtos.ResourceView> resources = h.getResources().stream()
                .map(r -> new GuardianDtos.ResourceView(r.getId(), r.getResourceType(), r.getResourceId(),
                        r.getResourceName(), r.getStatus(), r.getRequestId(), r.getClearedAt(), r.getUsedAt()))
                .toList();
        List<GuardianDtos.HandoverTrailView> trails;
        if (h.getTrails() == null || h.getTrails().isEmpty()) {
            trails = handoverTrailRepository.findByHandoverIdOrderByIdAsc(h.getId()).stream()
                    .map(this::toTrailView).toList();
        } else {
            trails = h.getTrails().stream().map(this::toTrailView).toList();
        }
        return new GuardianDtos.HandoverView(
                h.getId(), h.getHandoverNo(), h.getMinorId(), h.getMinorName(), h.getAuthorizationId(),
                h.getAuthorizationVersionId(), h.getVersionNo(), h.getStage(), handoverStageName(h.getStage()),
                h.getActualCaretaker(), h.getCaretakerIdentityNo(), h.getTakenOverBy(), h.getTakenOverAt(),
                h.getTrainingStartedBy(), h.getTrainingStartedAt(), h.getWaitingReturnBy(), h.getWaitingReturnAt(),
                h.getReturnedCaretaker(), h.getReturnedBy(), h.getReturnedAt(),
                h.isBaselineChangedAfterTakeover(), h.isFrozen(), h.getFreezeReason(), h.getFrozenAt(),
                h.getCreatedBy(), h.getCreatedAt(), h.getBaselineJson(), resources, trails);
    }

    private GuardianDtos.HandoverTrailView toTrailView(GuardianHandoverTrail t) {
        return new GuardianDtos.HandoverTrailView(t.getId(), t.getAction(), t.getFromStage(), t.getToStage(),
                t.getVersionId(), t.getVersionNo(), t.getBaselineBeforeJson(), t.getBaselineAfterJson(),
                t.getBlockedStage(), t.getBlockReason(), t.getOperator(), t.getOperatedAt(), t.getReason());
    }
}
