package com.archery.range;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.archery.range.common.BizException;
import com.archery.range.domain.GuardianHandover;
import com.archery.range.domain.Member;
import com.archery.range.dto.GuardianDtos;
import com.archery.range.repository.GuardianHandoverRepository;
import com.archery.range.repository.MemberRepository;
import com.archery.range.service.GuardianService;

/**
 * 未成年监护授权与到馆交接端到端测试：
 * 覆盖监护人确认、版本修订、整组资源校验、接管后基线变化冻结、幂等重试、重复确认和安全交还。
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class GuardianFlowIntegrationTest {

    private Long minorId;
    private static final Long COURSE_ID = 9L;
    private static final Long LANE_ID = 1L;
    private static final Long GEAR_ID = 9L;
    private static final Long ARROW_ID = 11L;

    @Autowired
    private GuardianService guardianService;
    @Autowired
    private GuardianHandoverRepository handoverRepository;
    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void createIsolatedMinor() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String phoneDigits = UUID.randomUUID().toString().replaceAll("[^0-9]", "");
        String phone = "139" + ("00000000" + phoneDigits)
                .substring(Math.max(0, ("00000000" + phoneDigits).length() - 8));
        Member member = new Member();
        member.setCardNo("UT-" + suffix);
        member.setName("未成年" + suffix.substring(0, 4));
        member.setPhone(phone);
        member.setLevel("NORMAL");
        member.setBalance(new BigDecimal("5000.00"));
        member.setRegisterDate(LocalDate.now());
        member.setBirthDate(LocalDate.now().minusYears(12));
        member.setTotalSpend(BigDecimal.ZERO);
        minorId = memberRepository.save(member).getId();
    }

    private GuardianDtos.AuthorizationSaveReq createReq(String caretaker, List<Long> courses,
            List<Long> lanes, List<Long> equipment) {
        return new GuardianDtos.AuthorizationSaveReq(
                minorId, "唐女士", "13900000012", LocalDate.now().minusDays(1), LocalDate.now().plusDays(30),
                "唐先生", "13900000013", "仅限监护人指定人员接管", courses, lanes, equipment,
                List.of(caretaker), "前台小王", "建立青少年射箭授权");
    }

    private GuardianDtos.AuthorizationDetail createAndConfirm(String caretaker, List<Long> courses,
            List<Long> lanes, List<Long> equipment) {
        GuardianDtos.AuthorizationSaveReq req = createReq(caretaker, courses, lanes, equipment);
        GuardianDtos.AuthorizationDetail draft = guardianService.createDraft(req);
        Long versionId = draft.versions().get(0).id();
        guardianService.submitForConfirmation(versionId, req.operator(), "请监护人确认");
        GuardianDtos.AuthorizationDetail confirmed = guardianService.confirm(versionId,
                req.guardianName(), "监护人已核对");
        assertThat(confirmed.versions().get(0).status()).isEqualTo("VALID");
        return confirmed;
    }

    private GuardianDtos.HandoverView arriveTakeoverPrecheck() {
        createAndConfirm("唐先生", List.of(COURSE_ID), List.of(LANE_ID), List.of(GEAR_ID, ARROW_ID));
        GuardianDtos.HandoverView handover = guardianService.createHandover(
                new GuardianDtos.HandoverCreateReq(minorId, "前台小王"));
        handover = guardianService.takeover(new GuardianDtos.TakeoverReq(
                handover.id(), handover.authorizationVersionId(), "唐先生", "ID-001", "前台小王"));
        assertThat(handover.stage()).isEqualTo(GuardianHandover.STAGE_TAKEN_OVER);
        handover = guardianService.precheckTraining(new GuardianDtos.TrainingPrecheckReq(
                handover.id(), COURSE_ID, LANE_ID, List.of(GEAR_ID, ARROW_ID),
                "REQ-" + handover.id() + "-1", "教练苏禾"));
        assertThat(handover.resources()).hasSize(4);
        return handover;
    }

    @Test
    void draftRequiresGuardianConfirmationAndOldVersionCannotRelease() {
        GuardianDtos.AuthorizationDetail detail = guardianService.createDraft(createReq(
                "唐先生", List.of(COURSE_ID), List.of(LANE_ID), List.of(GEAR_ID, ARROW_ID)));
        Long draftId = detail.versions().get(0).id();
        assertThat(detail.versions().get(0).status()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> guardianService.createHandover(
                new GuardianDtos.HandoverCreateReq(minorId, "前台小王")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚无监护人确认生效");

        guardianService.submitForConfirmation(draftId, "前台小王", "提交");
        assertThat(guardianService.detail(detail.id()).versions().get(0).status()).isEqualTo("PENDING");
        assertThatThrownBy(() -> guardianService.confirm(draftId, "其他人", "代确认"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("监护人");

        GuardianDtos.AuthorizationDetail confirmed = guardianService.confirm(draftId, "唐女士", "本人确认");
        Long v1 = confirmed.currentVersionId();
        GuardianDtos.RevisionReq revision = new GuardianDtos.RevisionReq(
                confirmed.id(), "唐女士", "13900000012", LocalDate.now().minusDays(1), LocalDate.now().plusDays(60),
                "唐先生", "13900000013", "缩小器材范围", List.of(COURSE_ID), List.of(LANE_ID), List.of(GEAR_ID),
                List.of("唐先生"), "前台小王", "本次暂不领用箭支");
        GuardianDtos.AuthorizationDetail revised = guardianService.revise(confirmed.id(), revision);
        Long v2 = revised.versions().get(0).id();
        assertThat(v2).isNotEqualTo(v1);
        // v1 仍是上一当前版本，新版本未确认前不能拿旧版本继续放行。
        assertThat(guardianService.detail(confirmed.id()).currentVersionId()).isEqualTo(v1);
        assertThatThrownBy(() -> guardianService.createHandover(
                new GuardianDtos.HandoverCreateReq(minorId, "前台小王")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("草稿或待监护人确认");

        guardianService.submitForConfirmation(v2, "前台小王", "范围变化重新确认");
        GuardianDtos.AuthorizationDetail confirmedV2 = guardianService.confirm(v2, "唐女士", "确认缩小范围");
        assertThat(confirmedV2.currentVersionId()).isEqualTo(v2);
        GuardianDtos.VersionView old = confirmedV2.versions().stream()
                .filter(v -> v.id().equals(v1)).findFirst().orElseThrow();
        assertThat(old.status()).isEqualTo("SUPERSEDED");
    }

    @Test
    void fullHandoverRequiresTakeoverWholeScopeAndSafeReturn() {
        GuardianDtos.HandoverView handover = arriveTakeoverPrecheck();
        Long handoverId = handover.id();

        // 未完成实际入场/领用前不能训练签到。
        assertThatThrownBy(() -> guardianService.startTraining(handoverId,
                new GuardianDtos.VersionReq(handoverId, "教练苏禾", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未完成实际入场/领用");

        for (GuardianDtos.ResourceView resource : handover.resources()) {
            // 课程 / 箭道 / 器材业务入口均由各自 service 调 gateResource；这里模拟业务入口已完成并标记 USED。
            markResourceUsed(handoverId, resource.resourceType(), resource.resourceId());
        }
        GuardianDtos.HandoverView training = guardianService.handoverDetail(handoverId);
        assertThat(training.resources()).allMatch(r -> "USED".equals(r.status()));
        training = guardianService.startTraining(handoverId, new GuardianDtos.VersionReq(handoverId, "教练苏禾", null));
        assertThat(training.stage()).isEqualTo(GuardianHandover.STAGE_TRAINING);

        training = guardianService.finishTraining(handoverId, new GuardianDtos.VersionReq(handoverId, "教练苏禾", null));
        assertThat(training.stage()).isEqualTo(GuardianHandover.STAGE_WAITING_RETURN);
        assertThatThrownBy(() -> guardianService.returnMinor(new GuardianDtos.ReturnReq(
                handoverId, "陌生人", "ID-X", "前台小王", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("合格接管名单");
        GuardianDtos.HandoverView returned = guardianService.returnMinor(new GuardianDtos.ReturnReq(
                handoverId, "唐先生", "ID-001", "前台小王", "安全交还"));
        assertThat(returned.stage()).isEqualTo(GuardianHandover.STAGE_RETURNED);
        // 交还后允许开启下一次到馆。
        assertThat(guardianService.createHandover(
                new GuardianDtos.HandoverCreateReq(minorId, "前台小王")).stage())
                .isEqualTo(GuardianHandover.STAGE_WAITING_TAKEOVER);
    }

    @Test
    void failedScopeCheckLeavesNoPartialClearanceAndRetryIsIdempotent() {
        GuardianDtos.HandoverView handover = arriveTakeoverPrecheck();
        // 箭支 ARROW id=12 不在授权范围；课程、箭道、箭支整组失败，不新增任何放行项。
        assertThatThrownBy(() -> guardianService.precheckTraining(new GuardianDtos.TrainingPrecheckReq(
                handover.id(), COURSE_ID, LANE_ID, List.of(12L), "REQ-BAD", "教练苏禾")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("整组训练校验失败");
        GuardianDtos.HandoverView unchanged = guardianService.handoverDetail(handover.id());
        assertThat(unchanged.resources()).hasSize(4);

        String requestId = "REQ-IDEMPOTENT-" + handover.id();
        GuardianDtos.HandoverView first = guardianService.precheckTraining(new GuardianDtos.TrainingPrecheckReq(
                handover.id(), COURSE_ID, LANE_ID, List.of(GEAR_ID, ARROW_ID), requestId, "教练苏禾"));
        GuardianDtos.HandoverView retry = guardianService.precheckTraining(new GuardianDtos.TrainingPrecheckReq(
                handover.id(), COURSE_ID, LANE_ID, List.of(GEAR_ID, ARROW_ID), requestId, "教练苏禾"));
        assertThat(retry.resources()).hasSameSizeAs(first.resources());
        assertThat(retry.trails().stream().filter(t -> "TRAINING_PRECHECK".equals(t.action())).count())
                .isEqualTo(first.trails().stream().filter(t -> "TRAINING_PRECHECK".equals(t.action())).count());
    }

    @Test
    void baselineChangeAfterTakeoverFreezesFutureActionsButAllowsSafeReturn() {
        GuardianDtos.HandoverView handover = arriveTakeoverPrecheck();
        Long versionId = handover.authorizationVersionId();
        GuardianDtos.AuthorizationDetail auth = guardianService.detail(handover.authorizationId());

        guardianService.pause(versionId, "值班经理", "监护人临时要求暂停");
        GuardianDtos.HandoverView paused = guardianService.handoverDetail(handover.id());
        assertThat(paused.frozen()).isTrue();
        assertThatThrownBy(() -> guardianService.startTraining(handover.id(),
                new GuardianDtos.VersionReq(handover.id(), "教练苏禾", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("AUTHORIZATION_BASELINE");

        guardianService.resume(versionId, "唐女士", "确认恢复");
        GuardianDtos.HandoverView resumed = guardianService.handoverDetail(handover.id());
        assertThat(resumed.frozen()).isFalse();
        for (GuardianDtos.ResourceView resource : resumed.resources()) {
            markResourceUsed(handover.id(), resource.resourceType(), resource.resourceId());
        }
        guardianService.startTraining(handover.id(), new GuardianDtos.VersionReq(handover.id(), "教练苏禾", null));

        // 训练已经开始后撤销：不篡改训练事实；已开始训练可结束并进入安全交还，未开始的新资源动作保持冻结。
        guardianService.revoke(versionId, "值班经理", "训练中撤销授权");
        GuardianDtos.HandoverView waitingReturn = guardianService.finishTraining(handover.id(),
                new GuardianDtos.VersionReq(handover.id(), "教练苏禾", "授权撤销后结束训练，准备交还"));
        assertThat(waitingReturn.stage()).isEqualTo(GuardianHandover.STAGE_WAITING_RETURN);
        GuardianDtos.HandoverView returned = guardianService.returnMinor(new GuardianDtos.ReturnReq(
                handover.id(), "唐先生", "ID-001", "前台小王", "异常情况下安全交还"));
        assertThat(returned.stage()).isEqualTo(GuardianHandover.STAGE_RETURNED);
        assertThat(returned.trails()).anyMatch(t -> "AUTHORIZATION_REVOKED".equals(t.action()));
        assertThat(returned.trails()).anyMatch(t -> "EXCEPTION_CLOSED".equals(t.action()));
        assertThat(auth.id()).isNotNull();
    }

    @Test
    void concurrentTakeoverOnlyOneQualifiedResultWins() throws Exception {
        createAndConfirm("唐先生", List.of(COURSE_ID), List.of(LANE_ID), List.of(GEAR_ID, ARROW_ID));
        GuardianDtos.HandoverView handover = guardianService.createHandover(
                new GuardianDtos.HandoverCreateReq(minorId, "前台小王"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable task = () -> {
            try {
                start.await();
                guardianService.takeover(new GuardianDtos.TakeoverReq(
                        handover.id(), handover.authorizationVersionId(), "唐先生", "ID-001", "前台小王"));
                success.incrementAndGet();
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("重复接管")) {
                    rejected.incrementAndGet();
                }
            }
        };
        Future<?> first = pool.submit(task);
        Future<?> second = pool.submit(task);
        start.countDown();
        first.get();
        second.get();
        pool.shutdown();
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(guardianService.handoverDetail(handover.id()).trails().stream()
                .filter(t -> "TAKEOVER".equals(t.action())).count()).isEqualTo(1);
    }

    private void markResourceUsed(Long handoverId, String type, Long resourceId) {
        GuardianHandover handover = handoverRepository.findById(handoverId).orElseThrow();
        handover.getResources().stream()
                .filter(r -> type.equals(r.getResourceType()) && resourceId.equals(r.getResourceId()))
                .findFirst()
                .ifPresent(r -> {
                    r.setStatus("USED");
                    r.setUsedAt(java.time.LocalDateTime.now());
                });
        handoverRepository.save(handover);
    }
}
