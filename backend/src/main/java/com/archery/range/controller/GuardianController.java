package com.archery.range.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.archery.range.common.ApiResponse;
import com.archery.range.dto.GuardianDtos;
import com.archery.range.service.GuardianService;

import jakarta.validation.Valid;

/** 未成年会员监护授权台：授权版本、监护人确认、到馆接管、资源放行、安全交还。 */
@RestController
@RequestMapping("/api/guardian")
public class GuardianController {

    private final GuardianService guardianService;

    public GuardianController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }

    @GetMapping("/minors/options")
    public ApiResponse<List<GuardianDtos.MinorOption>> minorOptions() {
        return ApiResponse.success(guardianService.minorOptions());
    }

    @GetMapping("/authorizations/by-minor/{minorId}")
    public ApiResponse<GuardianDtos.AuthorizationDetail> byMinor(@PathVariable Long minorId) {
        return ApiResponse.success(guardianService.detailByMinor(minorId));
    }

    @GetMapping("/authorizations/{id}")
    public ApiResponse<GuardianDtos.AuthorizationDetail> detail(@PathVariable Long id) {
        return ApiResponse.success(guardianService.detail(id));
    }

    @PostMapping("/authorizations/drafts")
    public ApiResponse<GuardianDtos.AuthorizationDetail> createDraft(
            @Valid @RequestBody GuardianDtos.AuthorizationSaveReq req) {
        return ApiResponse.success("监护授权草稿已建立", guardianService.createDraft(req));
    }

    @PostMapping("/authorizations/drafts/update")
    public ApiResponse<GuardianDtos.AuthorizationDetail> updateDraft(
            @Valid @RequestBody GuardianDtos.DraftUpdateReq req) {
        return ApiResponse.success("草稿已更新", guardianService.updateDraft(req));
    }

    @PostMapping("/authorizations/versions/{id}/submit")
    public ApiResponse<GuardianDtos.AuthorizationDetail> submit(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("已提交监护人确认", guardianService.submitForConfirmation(id, req.operator(), req.reason()));
    }

    @PostMapping("/authorizations/versions/{id}/confirm")
    public ApiResponse<GuardianDtos.AuthorizationDetail> confirm(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("监护人确认成功，授权已生效", guardianService.confirm(id, req.operator(), req.reason()));
    }

    @PostMapping("/authorizations/revisions")
    public ApiResponse<GuardianDtos.AuthorizationDetail> revise(
            @Valid @RequestBody GuardianDtos.RevisionReq req) {
        return ApiResponse.success("已生成新的授权草稿，需监护人重新确认", guardianService.revise(
                req.authorizationId() == null ? 0L : req.authorizationId(), req));
    }

    @PostMapping("/authorizations/versions/{id}/pause")
    public ApiResponse<GuardianDtos.AuthorizationDetail> pause(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("授权已暂停", guardianService.pause(id, req.operator(), req.reason()));
    }

    @PostMapping("/authorizations/versions/{id}/resume")
    public ApiResponse<GuardianDtos.AuthorizationDetail> resume(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("授权已恢复", guardianService.resume(id, req.operator(), req.reason()));
    }

    @PostMapping("/authorizations/versions/{id}/revoke")
    public ApiResponse<GuardianDtos.AuthorizationDetail> revoke(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("授权已撤销", guardianService.revoke(id, req.operator(), req.reason()));
    }

    @GetMapping("/handovers")
    public ApiResponse<List<GuardianDtos.HandoverView>> handovers(
            @RequestParam(required = false) Long minorId) {
        return ApiResponse.success(guardianService.listHandovers(minorId));
    }

    @GetMapping("/handovers/open")
    public ApiResponse<List<GuardianDtos.HandoverView>> openHandovers() {
        return ApiResponse.success(guardianService.openHandovers());
    }

    @GetMapping("/handovers/{id}")
    public ApiResponse<GuardianDtos.HandoverView> handover(@PathVariable Long id) {
        return ApiResponse.success(guardianService.handoverDetail(id));
    }

    @PostMapping("/handovers")
    public ApiResponse<GuardianDtos.HandoverView> createHandover(
            @Valid @RequestBody GuardianDtos.HandoverCreateReq req) {
        return ApiResponse.success("到馆交接单已建立", guardianService.createHandover(req));
    }

    @PostMapping("/handovers/takeover")
    public ApiResponse<GuardianDtos.HandoverView> takeover(@Valid @RequestBody GuardianDtos.TakeoverReq req) {
        return ApiResponse.success("现场接管已确认", guardianService.takeover(req));
    }

    @PostMapping("/handovers/rebaseline")
    public ApiResponse<GuardianDtos.HandoverView> rebaseline(@Valid @RequestBody GuardianDtos.RebaselineReq req) {
        return ApiResponse.success("已按当前授权版本重新核对", guardianService.rebaseline(req));
    }

    @PostMapping("/handovers/precheck-training")
    public ApiResponse<GuardianDtos.HandoverView> precheck(
            @Valid @RequestBody GuardianDtos.TrainingPrecheckReq req) {
        return ApiResponse.success("课程、箭道、器材整组校验通过", guardianService.precheckTraining(req));
    }

    @PostMapping("/handovers/{id}/start-training")
    public ApiResponse<GuardianDtos.HandoverView> startTraining(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("训练已开始", guardianService.startTraining(id, req.operator(), req.reason()));
    }

    @PostMapping("/handovers/{id}/finish-training")
    public ApiResponse<GuardianDtos.HandoverView> finishTraining(@PathVariable Long id,
            @Valid @RequestBody GuardianDtos.VersionReq req) {
        return ApiResponse.success("训练结束，等待交还", guardianService.finishTraining(id, req.operator(), req.reason()));
    }

    @PostMapping("/handovers/return")
    public ApiResponse<GuardianDtos.HandoverView> returnMinor(@Valid @RequestBody GuardianDtos.ReturnReq req) {
        return ApiResponse.success("已完成安全交还", guardianService.returnMinor(req));
    }
}
