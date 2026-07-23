package com.smartlearning.backend.module.personal.controller;

import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.personal.service.PersonalDataClearLogService;
import com.smartlearning.backend.module.personal.service.PersonalDataExportService;
import com.smartlearning.backend.module.plan.service.StudyPlanService;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import com.smartlearning.backend.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;

@Tag(name = "个人数据管理模块")
@RestController
@RequestMapping("/personal-data")
public class PersonalDataController {

    private final StudyPlanService studyPlanService;
    private final StudyRecordService studyRecordService;
    private final WrongQuestionService wrongQuestionService;
    private final AssessmentService assessmentService;
    private final PersonalDataExportService personalDataExportService;
    private final PersonalDataClearLogService personalDataClearLogService;

    public PersonalDataController(StudyPlanService studyPlanService,
                                  StudyRecordService studyRecordService,
                                  WrongQuestionService wrongQuestionService,
                                  AssessmentService assessmentService,
                                  PersonalDataExportService personalDataExportService,
                                  PersonalDataClearLogService personalDataClearLogService) {
        this.studyPlanService = studyPlanService;
        this.studyRecordService = studyRecordService;
        this.wrongQuestionService = wrongQuestionService;
        this.assessmentService = assessmentService;
        this.personalDataExportService = personalDataExportService;
        this.personalDataClearLogService = personalDataClearLogService;
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
        return Result.success(personalDataExportService.exportData(SecurityUtils.currentUserId()));
    }

    @GetMapping("/export-files/{fileName:.+}")
    public ResponseEntity<UrlResource> exportFile(@PathVariable String fileName,
                                                  @RequestParam String token) throws MalformedURLException {
        Path file = personalDataExportService.exportFile(SecurityUtils.currentUserId(), fileName, token);
        UrlResource resource = new UrlResource(file.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(resource);
    }

    @GetMapping("/export-logs")
    public Result<Map<String, Object>> exportLogs() {
        return Result.success(personalDataExportService.listExportLogs(SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/clear")
    public Result<Map<String, Object>> clear(@RequestBody Map<String, String> request) {
        return Result.success(personalDataClearLogService.clearPersonalData(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/clear-logs")
    public Result<Map<String, Object>> clearLogs() {
        return Result.success(personalDataClearLogService.listClearLogs(SecurityUtils.currentUserId()));
    }
}
