package com.smartlearning.backend.module.record.controller;

import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.record.service.StudyProgressService;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "学习记录与进度模块")
@RestController
@RequestMapping("/study-records")
public class StudyRecordController {

    private final StudyRecordService studyRecordService;
    private final StudyProgressService studyProgressService;
    private final UserProfileService userProfileService;
    private final LearningResourceService learningResourceService;

    public StudyRecordController(StudyRecordService studyRecordService,
                                 StudyProgressService studyProgressService,
                                 UserProfileService userProfileService,
                                 LearningResourceService learningResourceService) {
        this.studyRecordService = studyRecordService;
        this.studyProgressService = studyProgressService;
        this.userProfileService = userProfileService;
        this.learningResourceService = learningResourceService;
    }

    @PostMapping
    public Result<StudyRecord> create(@RequestBody StudyRecord record) {
        Long userId = SecurityUtils.currentUserId();
        validateResourceId(record.getResourceId());
        record.setUserId(userId);
        record.setStudyTime(LocalDateTime.now());
        studyRecordService.save(record);
        userProfileService.refreshAfterLearningEvent(userId);
        return Result.success(record);
    }

    @GetMapping("/duration-statistics")
    public Result<Map<String, Object>> durationStatistics(@RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate) {
        return Result.success(studyProgressService.durationStatistics(SecurityUtils.currentUserId(), type, startDate, endDate));
    }

    @GetMapping("/progress-report")
    public Result<Map<String, Object>> progressReport(@RequestParam(required = false) String period,
                                                      @RequestParam(required = false) String date) {
        return Result.success(studyProgressService.progressReport(SecurityUtils.currentUserId(), period, date));
    }

    @GetMapping("/reminders")
    public Result<Map<String, Object>> reminders() {
        return Result.success(studyProgressService.reminders(SecurityUtils.currentUserId()));
    }

    private void validateResourceId(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        if (learningResourceService.getById(resourceId) == null) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "学习资源不存在，resourceId=" + resourceId);
        }
    }
}
