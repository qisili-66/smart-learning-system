package com.smartlearning.backend.module.record.controller;

import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Tag(name = "Study record and progress module")
@RestController
@RequestMapping("/study-records")
public class StudyRecordController {

    private final StudyRecordService studyRecordService;
    private final UserProfileService userProfileService;
    private final LearningResourceService learningResourceService;

    public StudyRecordController(StudyRecordService studyRecordService,
                                 UserProfileService userProfileService,
                                 LearningResourceService learningResourceService) {
        this.studyRecordService = studyRecordService;
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
        Long totalDuration = studyRecordService.lambdaQuery()
                .eq(StudyRecord::getUserId, SecurityUtils.currentUserId())
                .list()
                .stream()
                .map(StudyRecord::getStudyDuration)
                .filter(duration -> duration != null)
                .mapToLong(Integer::longValue)
                .sum();
        return Result.success(Map.of("type", type == null ? "" : type, "totalDuration", totalDuration, "items", Collections.emptyList()));
    }

    @GetMapping("/progress-report")
    public Result<Map<String, Object>> progressReport(@RequestParam(required = false) String period,
                                                      @RequestParam(required = false) String date) {
        return Result.success(Map.of("period", period == null ? "" : period, "date", date == null ? "" : date, "summary", ""));
    }

    @GetMapping("/reminders")
    public Result<Map<String, Object>> reminders() {
        return Result.success(Map.of("reminders", Collections.emptyList()));
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
