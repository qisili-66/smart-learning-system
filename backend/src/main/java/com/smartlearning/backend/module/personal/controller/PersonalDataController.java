package com.smartlearning.backend.module.personal.controller;

import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.plan.service.StudyPlanService;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.user.service.SysUserService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "个人数据管理模块")
@RestController
@RequestMapping("/personal-data")
public class PersonalDataController {

    private final SysUserService sysUserService;
    private final StudyPlanService studyPlanService;
    private final StudyRecordService studyRecordService;
    private final WrongQuestionService wrongQuestionService;
    private final AssessmentService assessmentService;

    public PersonalDataController(SysUserService sysUserService,
                                  StudyPlanService studyPlanService,
                                  StudyRecordService studyRecordService,
                                  WrongQuestionService wrongQuestionService,
                                  AssessmentService assessmentService) {
        this.sysUserService = sysUserService;
        this.studyPlanService = studyPlanService;
        this.studyRecordService = studyRecordService;
        this.wrongQuestionService = wrongQuestionService;
        this.assessmentService = assessmentService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(Map.of(
                "studyPlanCount", studyPlanService.lambdaQuery().eq(com.smartlearning.backend.module.plan.entity.StudyPlan::getUserId, userId).count(),
                "studyRecordCount", studyRecordService.lambdaQuery().eq(com.smartlearning.backend.module.record.entity.StudyRecord::getUserId, userId).count(),
                "wrongQuestionCount", wrongQuestionService.lambdaQuery().eq(com.smartlearning.backend.module.wrong.entity.WrongQuestion::getUserId, userId).count(),
                "assessmentCount", assessmentService.lambdaQuery().eq(com.smartlearning.backend.module.assessment.entity.Assessment::getUserId, userId).count()
        ));
    }

    @GetMapping("/export")
    public Result<Map<String, Object>> export() {
        return Result.success(Map.of("downloadUrl", ""));
    }

    @DeleteMapping("/clear")
    public Result<Void> clear(@RequestBody Map<String, String> request) {
        Long userId = SecurityUtils.currentUserId();
        if (!sysUserService.verifyPassword(userId, request.get("password"))) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "密码错误");
        }
        studyPlanService.lambdaUpdate().eq(com.smartlearning.backend.module.plan.entity.StudyPlan::getUserId, userId).remove();
        studyRecordService.lambdaUpdate().eq(com.smartlearning.backend.module.record.entity.StudyRecord::getUserId, userId).remove();
        wrongQuestionService.lambdaUpdate().eq(com.smartlearning.backend.module.wrong.entity.WrongQuestion::getUserId, userId).remove();
        assessmentService.lambdaUpdate().eq(com.smartlearning.backend.module.assessment.entity.Assessment::getUserId, userId).remove();
        return Result.success();
    }
}
