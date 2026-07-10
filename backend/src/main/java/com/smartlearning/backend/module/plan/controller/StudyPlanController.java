package com.smartlearning.backend.module.plan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.plan.entity.StudyPlan;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.plan.service.StudyPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "学习计划模块")
@RestController
@RequestMapping("/study-plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;
    private final UserProfileService userProfileService;

    public StudyPlanController(StudyPlanService studyPlanService, UserProfileService userProfileService) {
        this.studyPlanService = studyPlanService;
        this.userProfileService = userProfileService;
    }

    @PostMapping
    public Result<StudyPlan> create(@RequestBody StudyPlan plan) {
        Long userId = SecurityUtils.currentUserId();
        plan.setUserId(userId);
        if (plan.getPlanStatus() == null) {
            plan.setPlanStatus(Constants.PLAN_RUNNING);
        }
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        studyPlanService.save(plan);
        userProfileService.refreshAfterLearningEvent(userId);
        return Result.success(plan);
    }

    @GetMapping
    public Result<PageVO<StudyPlan>> list(@RequestParam(required = false) Integer planStatus,
                                          @RequestParam(required = false) Integer pageNum,
                                          @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<StudyPlan> query = new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, SecurityUtils.currentUserId())
                .eq(planStatus != null, StudyPlan::getPlanStatus, planStatus)
                .orderByDesc(StudyPlan::getCreateTime);
        Page<StudyPlan> page = studyPlanService.page(PageUtils.page(pageNum, pageSize), query);
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/{planId}")
    public Result<StudyPlan> detail(@PathVariable Long planId) {
        return Result.success(getOwnedPlan(planId));
    }

    @PutMapping("/{planId}")
    public Result<StudyPlan> update(@PathVariable Long planId, @RequestBody StudyPlan request) {
        StudyPlan plan = getOwnedPlan(planId);
        request.setPlanId(plan.getPlanId());
        request.setUserId(plan.getUserId());
        request.setUpdateTime(LocalDateTime.now());
        studyPlanService.updateById(request);
        userProfileService.refreshAfterLearningEvent(plan.getUserId());
        return Result.success(studyPlanService.getById(planId));
    }

    @DeleteMapping("/{planId}")
    public Result<Void> delete(@PathVariable Long planId) {
        StudyPlan plan = getOwnedPlan(planId);
        studyPlanService.removeById(plan.getPlanId());
        userProfileService.refreshAfterLearningEvent(plan.getUserId());
        return Result.success();
    }

    @GetMapping("/daily-tasks")
    public Result<Map<String, Object>> dailyTasks(@RequestParam(required = false) Long planId,
                                                  @RequestParam(required = false) String date) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(studyPlanService.dailyTasks(
                userId,
                planId,
                parseDate(date),
                userProfileService.weakPoints(userId, 5),
                userProfileService.metrics(userId)
        ));
    }

    @PutMapping("/tasks/{taskId}/finish")
    public Result<Map<String, Object>> finishTask(@PathVariable Long taskId,
                                                  @RequestBody(required = false) Map<String, Object> request) {
        Long userId = SecurityUtils.currentUserId();
        Map<String, Object> data = studyPlanService.finishTask(userId, taskId, request == null ? Map.of() : request);
        userProfileService.refreshAfterLearningEvent(userId);
        return Result.success(data);
    }

    @GetMapping("/recommended-resources")
    public Result<Map<String, Object>> recommendedResources(@RequestParam(required = false) String subject,
                                                            @RequestParam(defaultValue = "8") Integer limit) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(studyPlanService.recommendResources(
                userId,
                subject,
                limit,
                userProfileService.weakPoints(userId, 5),
                userProfileService.metrics(userId)
        ));
    }

    @PostMapping("/targets")
    public Result<Map<String, Object>> createTargetPlan(@RequestBody Map<String, Object> request) {
        Long userId = SecurityUtils.currentUserId();
        java.util.Map<String, Object> enriched = new java.util.LinkedHashMap<>(request);
        enriched.put("weakPoints", userProfileService.weakPoints(userId, 5));
        enriched.put("metrics", userProfileService.metrics(userId));
        Map<String, Object> data = studyPlanService.createTargetPlan(userId, enriched);
        userProfileService.refreshAfterLearningEvent(userId);
        return Result.success(data);
    }

    @GetMapping("/{planId}/path")
    public Result<Map<String, Object>> planPath(@PathVariable Long planId) {
        return Result.success(studyPlanService.planPath(SecurityUtils.currentUserId(), planId));
    }

    @PostMapping("/{planId}/adjustments")
    public Result<Map<String, Object>> adjustPlan(@PathVariable Long planId) {
        Long userId = SecurityUtils.currentUserId();
        Map<String, Object> data = studyPlanService.adjustPlan(
                userId,
                planId,
                userProfileService.metrics(userId),
                userProfileService.weakPoints(userId, 5)
        );
        userProfileService.refreshAfterLearningEvent(userId);
        return Result.success(data);
    }

    private StudyPlan getOwnedPlan(Long planId) {
        StudyPlan plan = studyPlanService.getById(planId);
        if (plan == null || !SecurityUtils.currentUserId().equals(plan.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "study plan not found");
        }
        return plan;
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (RuntimeException e) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "date格式必须为yyyy-MM-dd");
        }
    }
}
