package com.smartlearning.backend.module.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.plan.entity.StudyPlan;
import com.smartlearning.backend.module.plan.service.StudyPlanService;
import com.smartlearning.backend.module.profile.entity.UserProfileCorrectionLog;
import com.smartlearning.backend.module.profile.entity.UserProfile;
import com.smartlearning.backend.module.profile.mapper.UserProfileMapper;
import com.smartlearning.backend.module.profile.service.UserProfileCorrectionLogService;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {

    private static final int OPERATOR_MANUAL = 1;
    private static final int STUDY_TYPE_RESOURCE_VIEW = 1;
    private static final int STUDY_TYPE_ASSESSMENT = 2;
    private static final int STUDY_TYPE_WRONG_REVIEW = 3;
    private static final int STUDY_TYPE_QA = 4;

    private final StudyRecordService studyRecordService;
    private final AssessmentService assessmentService;
    private final WrongQuestionService wrongQuestionService;
    private final QuestionBankService questionBankService;
    private final StudyPlanService studyPlanService;
    private final UserProfileCorrectionLogService correctionLogService;
    private final LearningResourceService learningResourceService;

    public UserProfileServiceImpl(StudyRecordService studyRecordService,
                                  AssessmentService assessmentService,
                                  WrongQuestionService wrongQuestionService,
                                  QuestionBankService questionBankService,
                                  StudyPlanService studyPlanService,
                                  UserProfileCorrectionLogService correctionLogService,
                                  LearningResourceService learningResourceService) {
        this.studyRecordService = studyRecordService;
        this.assessmentService = assessmentService;
        this.wrongQuestionService = wrongQuestionService;
        this.questionBankService = questionBankService;
        this.studyPlanService = studyPlanService;
        this.correctionLogService = correctionLogService;
        this.learningResourceService = learningResourceService;
    }

    @Override
    public Map<String, Object> overview(Long userId, boolean refresh) {
        UserProfile profile = refresh ? refreshProfile(userId) : getOrCreateProfile(userId);
        ProfileAnalysis analysis = analyze(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", toProfileMap(profile));
        data.put("metrics", analysis.metrics());
        data.put("dataSources", analysis.dataSources());
        data.put("recommendations", analysis.recommendations());
        data.put("fieldSources", fieldSources(userId));
        data.put("correctionLogs", correctionLogs(userId, 5));
        return data;
    }

    @Override
    public Map<String, Object> metrics(Long userId) {
        return analyze(userId).metrics();
    }

    @Override
    public UserProfile refreshProfile(Long userId) {
        ProfileAnalysis analysis = analyze(userId);
        UserProfile profile = getOrCreateProfile(userId);
        Set<String> manualFields = latestManualFields(userId);

        if (!manualFields.contains("abilityScore")) {
            profile.setAbilityScore(analysis.abilityScore());
        }
        if (!manualFields.contains("knowledgeMastery")) {
            profile.setKnowledgeMastery(analysis.knowledgeMastery());
        }
        if (!manualFields.contains("studyHabit")) {
            profile.setStudyHabit(analysis.studyHabit());
        }
        if (!manualFields.contains("weakPoints")) {
            profile.setWeakPoints(String.join(",", analysis.weakPoints()));
        }
        if (!StringUtils.hasText(profile.getPreference())) {
            profile.setPreference(analysis.preference());
        }
        profile.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(profile);
        return profile;
    }

    @Override
    public void refreshAfterLearningEvent(Long userId) {
        try {
            refreshProfile(userId);
        } catch (RuntimeException e) {
            log.warn("Refresh user profile failed after learning event. userId={}", userId, e);
        }
    }

    @Override
    public List<String> weakPoints(Long userId, int limit) {
        int safeLimit = Math.max(0, limit);
        UserProfile profile = getOrCreateProfile(userId);
        List<String> points = split(profile.getWeakPoints());
        if (points.isEmpty()) {
            points = analyze(userId).weakPoints();
        }
        return points.stream().limit(safeLimit).toList();
    }

    @Override
    public Map<String, Object> correctProfile(Long userId, Map<String, Object> request) {
        UserProfile profile = getOrCreateProfile(userId);
        String reason = Objects.toString(request.get("reason"), "");
        List<UserProfileCorrectionLog> logs = new ArrayList<>();

        updateStringField(logs, userId, profile, "preference", profile.getPreference(), request.get("preference"),
                profile::setPreference, reason);
        updateStringField(logs, userId, profile, "studyHabit", profile.getStudyHabit(), request.get("studyHabit"),
                profile::setStudyHabit, reason);

        Object weakPoints = request.containsKey("weakPoints") ? request.get("weakPoints") : request.get("customWeakPoints");
        updateStringField(logs, userId, profile, "weakPoints", profile.getWeakPoints(), normalizeWeakPoints(weakPoints),
                profile::setWeakPoints, reason);

        updateDecimalField(logs, userId, profile, "abilityScore", profile.getAbilityScore(), request.get("abilityScore"),
                profile::setAbilityScore, reason);
        updateDecimalField(logs, userId, profile, "knowledgeMastery", profile.getKnowledgeMastery(), request.get("knowledgeMastery"),
                profile::setKnowledgeMastery, reason);

        profile.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(profile);
        logs.forEach(correctionLogService::save);
        return overview(userId, false);
    }

    @Override
    public List<Map<String, Object>> correctionLogs(Long userId, int limit) {
        int safeLimit = Math.max(0, limit);
        try {
            return correctionLogService.lambdaQuery()
                    .eq(UserProfileCorrectionLog::getUserId, userId)
                    .orderByDesc(UserProfileCorrectionLog::getCreateTime)
                    .last("limit " + safeLimit)
                    .list()
                    .stream()
                    .map(this::toLogMap)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Query user profile correction logs failed. userId={}", userId, e);
            return List.of();
        }
    }

    @Override
    public Map<String, Object> collectBehavior(Long userId, Map<String, Object> request) {
        Long resourceId = validateResourceId(toLong(request.get("resourceId")));
        Integer studyType = defaultInteger(request.get("studyType"), STUDY_TYPE_RESOURCE_VIEW);
        Integer duration = Math.max(0, defaultInteger(request.get("studyDuration"), 0));
        Integer finishStatus = defaultInteger(request.get("finishStatus"), 0);
        StudyRecord record = saveBehaviorRecord(userId, resourceId, studyType, duration, finishStatus);
        UserProfile profile = refreshProfile(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", record.getRecordId());
        data.put("profile", toProfileMap(profile));
        data.put("metrics", metrics(userId));
        return data;
    }

    @Override
    public void collectResourceView(Long userId, Long resourceId) {
        saveBehaviorRecord(userId, validateResourceId(resourceId), STUDY_TYPE_RESOURCE_VIEW, 0, 0);
        refreshAfterLearningEvent(userId);
    }

    @Override
    public void collectQaInteraction(Long userId) {
        saveBehaviorRecord(userId, null, STUDY_TYPE_QA, 0, 1);
        refreshAfterLearningEvent(userId);
    }

    private StudyRecord saveBehaviorRecord(Long userId, Long resourceId, Integer studyType, Integer duration, Integer finishStatus) {
        StudyRecord record = new StudyRecord();
        record.setUserId(userId);
        record.setResourceId(resourceId);
        record.setStudyType(studyType);
        record.setStudyDuration(duration);
        record.setFinishStatus(finishStatus);
        record.setStudyTime(LocalDateTime.now());
        studyRecordService.save(record);
        return record;
    }

    private Long validateResourceId(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        if (learningResourceService.getById(resourceId) == null) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "学习资源不存在，resourceId=" + resourceId);
        }
        return resourceId;
    }

    private ProfileAnalysis analyze(Long userId) {
        List<StudyRecord> records = studyRecordService.lambdaQuery()
                .eq(StudyRecord::getUserId, userId)
                .list();
        List<Assessment> assessments = assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, userId)
                .list();
        List<WrongQuestion> wrongQuestions = wrongQuestionService.lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .list();
        List<StudyPlan> plans = studyPlanService.lambdaQuery()
                .eq(StudyPlan::getUserId, userId)
                .list();

        Map<Long, QuestionBank> questionMap = loadQuestionMap(wrongQuestions);
        long totalDuration = records.stream()
                .map(StudyRecord::getStudyDuration)
                .filter(Objects::nonNull)
                .filter(duration -> duration > 0)
                .mapToLong(Integer::longValue)
                .sum();

        long completedRecords = records.stream()
                .filter(record -> Integer.valueOf(1).equals(record.getFinishStatus()))
                .count();
        double recordCompletionRate = records.isEmpty() ? 0D : completedRecords * 1D / records.size();

        double assessmentRate = averageAssessmentRate(assessments);
        long masteredWrong = wrongQuestions.stream()
                .filter(wrong -> Integer.valueOf(1).equals(wrong.getIsMastered()))
                .count();
        double wrongMasteryRate = wrongQuestions.isEmpty() ? 1D : masteredWrong * 1D / wrongQuestions.size();

        long finishedPlans = plans.stream()
                .filter(plan -> Integer.valueOf(2).equals(plan.getPlanStatus()))
                .count();
        double planCompletionRate = plans.isEmpty() ? recordCompletionRate : finishedPlans * 1D / plans.size();

        LocalDate fourteenDaysAgo = LocalDate.now().minusDays(13);
        long activeDays = records.stream()
                .map(StudyRecord::getStudyTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .filter(day -> !day.isBefore(fourteenDaysAgo))
                .distinct()
                .count();
        long recentDuration = records.stream()
                .filter(record -> record.getStudyTime() != null)
                .filter(record -> !record.getStudyTime().toLocalDate().isBefore(fourteenDaysAgo))
                .map(StudyRecord::getStudyDuration)
                .filter(Objects::nonNull)
                .filter(duration -> duration > 0)
                .mapToLong(Integer::longValue)
                .sum();

        double activityScore = clamp(activeDays / 14D) * 100D;
        double durationScore = clamp(recentDuration / 420D) * 100D;
        double completionScore = clamp(recordCompletionRate * 0.7D + planCompletionRate * 0.3D) * 100D;
        double correctnessScore = assessments.isEmpty() ? wrongMasteryRate * 100D : assessmentRate * 100D;
        double knowledgeScore = clamp((correctnessScore * 0.6D + wrongMasteryRate * 100D * 0.4D) / 100D) * 100D;
        double abilityScore = clamp((knowledgeScore * 0.45D + completionScore * 0.25D
                + durationScore * 0.2D + activityScore * 0.1D) / 100D) * 100D;

        List<String> weakPoints = detectWeakPoints(wrongQuestions, questionMap, assessments);
        String studyHabit = inferStudyHabit(records, recordCompletionRate, assessmentRate, wrongMasteryRate, activeDays, recentDuration);
        String preference = inferPreference(records);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalDuration", totalDuration);
        metrics.put("recordCount", records.size());
        metrics.put("activeDays14", activeDays);
        metrics.put("recordCompletionRate", percent(recordCompletionRate));
        metrics.put("planCompletionRate", percent(planCompletionRate));
        metrics.put("assessmentCorrectRate", percent(assessmentRate));
        metrics.put("wrongMasteryRate", percent(wrongMasteryRate));
        metrics.put("weakPointCount", weakPoints.size());
        metrics.put("abilityScore", decimal(abilityScore));
        metrics.put("knowledgeMastery", decimal(knowledgeScore));

        Map<String, Object> dataSources = new LinkedHashMap<>();
        dataSources.put("studyRecords", records.size());
        dataSources.put("assessments", assessments.size());
        dataSources.put("wrongQuestions", wrongQuestions.size());
        dataSources.put("studyPlans", plans.size());

        return new ProfileAnalysis(
                decimal(abilityScore),
                decimal(knowledgeScore),
                studyHabit,
                weakPoints,
                preference,
                metrics,
                dataSources,
                recommendations(weakPoints, recordCompletionRate, assessmentRate, recentDuration)
        );
    }

    private UserProfile getOrCreateProfile(Long userId) {
        UserProfile profile = getOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId), false);
        if (profile != null) {
            return profile;
        }
        profile = new UserProfile();
        profile.setUserId(userId);
        profile.setAbilityScore(BigDecimal.ZERO);
        profile.setKnowledgeMastery(BigDecimal.ZERO);
        profile.setStudyHabit("insufficient_data");
        profile.setWeakPoints("");
        profile.setPreference("");
        profile.setUpdateTime(LocalDateTime.now());
        save(profile);
        return profile;
    }

    private Map<Long, QuestionBank> loadQuestionMap(List<WrongQuestion> wrongQuestions) {
        List<Long> questionIds = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionBankService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, Function.identity(), (left, right) -> left));
    }

    private double averageAssessmentRate(List<Assessment> assessments) {
        List<Double> rates = assessments.stream()
                .filter(assessment -> assessment.getUserScore() != null && assessment.getTotalScore() != null)
                .filter(assessment -> assessment.getTotalScore().compareTo(BigDecimal.ZERO) > 0)
                .map(assessment -> assessment.getUserScore()
                        .divide(assessment.getTotalScore(), 4, RoundingMode.HALF_UP)
                        .doubleValue())
                .map(this::clamp)
                .toList();
        if (rates.isEmpty()) {
            return 0D;
        }
        return rates.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private List<String> detectWeakPoints(List<WrongQuestion> wrongQuestions,
                                          Map<Long, QuestionBank> questionMap,
                                          List<Assessment> assessments) {
        Map<String, Long> distribution = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .map(questionMap::get)
                .filter(Objects::nonNull)
                .map(QuestionBank::getKnowledgePoint)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<String> points = distribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();
        if (!points.isEmpty()) {
            return points;
        }
        return assessments.stream()
                .filter(assessment -> assessment.getKnowledgeScope() != null)
                .filter(assessment -> assessment.getUserScore() != null && assessment.getTotalScore() != null)
                .filter(assessment -> assessment.getTotalScore().compareTo(BigDecimal.ZERO) > 0)
                .filter(assessment -> assessment.getUserScore()
                        .divide(assessment.getTotalScore(), 4, RoundingMode.HALF_UP)
                        .compareTo(BigDecimal.valueOf(0.7D)) < 0)
                .flatMap(assessment -> split(assessment.getKnowledgeScope()).stream())
                .distinct()
                .limit(5)
                .toList();
    }

    private String inferStudyHabit(List<StudyRecord> records,
                                   double recordCompletionRate,
                                   double assessmentRate,
                                   double wrongMasteryRate,
                                   long activeDays,
                                   long recentDuration) {
        if (records.isEmpty()) {
            return "insufficient_data";
        }
        if (activeDays >= 5 && recentDuration >= 150 && recordCompletionRate >= 0.7D) {
            return "stable";
        }
        if (recordCompletionRate < 0.5D) {
            return "low_completion";
        }
        if (assessmentRate > 0D && assessmentRate < 0.6D) {
            return "low_accuracy";
        }
        if (wrongMasteryRate < 0.5D) {
            return "needs_review";
        }
        return "normal";
    }

    private String inferPreference(List<StudyRecord> records) {
        return records.stream()
                .map(StudyRecord::getStudyType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> "study_type_" + entry.getKey())
                .orElse("");
    }

    private List<Map<String, Object>> recommendations(List<String> weakPoints,
                                                       double recordCompletionRate,
                                                       double assessmentRate,
                                                       long recentDuration) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!weakPoints.isEmpty()) {
            items.add(recommendation("review_weak_points", "high", String.join(",", weakPoints)));
        }
        if (recordCompletionRate < 0.6D) {
            items.add(recommendation("finish_pending_tasks", "medium", "Increase task completion rate"));
        }
        if (assessmentRate > 0D && assessmentRate < 0.7D) {
            items.add(recommendation("accuracy_training", "high", "Practice low-score knowledge points"));
        }
        if (recentDuration < 120) {
            items.add(recommendation("increase_study_duration", "low", "Keep at least 30 minutes for active study days"));
        }
        return items;
    }

    private Map<String, Object> recommendation(String type, String priority, String content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("priority", priority);
        item.put("content", content);
        return item;
    }

    private Map<String, Object> toProfileMap(UserProfile profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profileId", profile.getProfileId());
        data.put("userId", profile.getUserId());
        data.put("abilityScore", profile.getAbilityScore());
        data.put("knowledgeMastery", profile.getKnowledgeMastery());
        data.put("studyHabit", safe(profile.getStudyHabit()));
        data.put("weakPoints", split(profile.getWeakPoints()));
        data.put("preference", safe(profile.getPreference()));
        data.put("updateTime", profile.getUpdateTime());
        return data;
    }

    private Map<String, String> fieldSources(Long userId) {
        Set<String> manualFields = latestManualFields(userId);
        Map<String, String> sources = new LinkedHashMap<>();
        for (String field : List.of("abilityScore", "knowledgeMastery", "studyHabit", "weakPoints", "preference")) {
            sources.put(field, manualFields.contains(field) ? "USER_MANUAL" : "SYSTEM_CALCULATED");
        }
        return sources;
    }

    private Set<String> latestManualFields(Long userId) {
        try {
            List<UserProfileCorrectionLog> logs = correctionLogService.lambdaQuery()
                    .eq(UserProfileCorrectionLog::getUserId, userId)
                    .eq(UserProfileCorrectionLog::getOperatorType, OPERATOR_MANUAL)
                    .orderByDesc(UserProfileCorrectionLog::getCreateTime)
                    .list();
            Set<String> fields = new HashSet<>();
            for (UserProfileCorrectionLog log : logs) {
                fields.add(log.getFieldName());
            }
            return fields;
        } catch (RuntimeException e) {
            log.warn("Query latest manual profile fields failed. userId={}", userId, e);
            return Set.of();
        }
    }

    private Map<String, Object> toLogMap(UserProfileCorrectionLog log) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", log.getLogId());
        data.put("fieldName", log.getFieldName());
        data.put("oldValue", safe(log.getOldValue()));
        data.put("newValue", safe(log.getNewValue()));
        data.put("operatorType", log.getOperatorType());
        data.put("reason", safe(log.getReason()));
        data.put("createTime", log.getCreateTime());
        return data;
    }

    private void updateStringField(List<UserProfileCorrectionLog> logs,
                                   Long userId,
                                   UserProfile profile,
                                   String fieldName,
                                   String oldValue,
                                   Object rawValue,
                                   java.util.function.Consumer<String> setter,
                                   String reason) {
        if (rawValue == null) {
            return;
        }
        String newValue = rawValue.toString();
        if (Objects.equals(safe(oldValue), newValue)) {
            return;
        }
        setter.accept(newValue);
        logs.add(logEntry(userId, fieldName, oldValue, newValue, reason));
    }

    private void updateDecimalField(List<UserProfileCorrectionLog> logs,
                                    Long userId,
                                    UserProfile profile,
                                    String fieldName,
                                    BigDecimal oldValue,
                                    Object rawValue,
                                    java.util.function.Consumer<BigDecimal> setter,
                                    String reason) {
        BigDecimal newValue = toBigDecimal(rawValue);
        if (newValue == null) {
            return;
        }
        if (oldValue != null && oldValue.compareTo(newValue) == 0) {
            return;
        }
        setter.accept(newValue);
        logs.add(logEntry(userId, fieldName, oldValue == null ? "" : oldValue.toPlainString(), newValue.toPlainString(), reason));
    }

    private UserProfileCorrectionLog logEntry(Long userId, String fieldName, Object oldValue, Object newValue, String reason) {
        UserProfileCorrectionLog log = new UserProfileCorrectionLog();
        log.setUserId(userId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue == null ? "" : oldValue.toString());
        log.setNewValue(newValue == null ? "" : newValue.toString());
        log.setOperatorType(OPERATOR_MANUAL);
        log.setReason(reason);
        log.setCreateTime(LocalDateTime.now());
        return log;
    }

    private String normalizeWeakPoints(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(StringUtils::hasText).collect(Collectors.joining(","));
        }
        return value == null ? null : value.toString();
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("[,;|]")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(double value) {
        return decimal(clamp(value) * 100D);
    }

    private Integer defaultInteger(Object value, Integer defaultValue) {
        Integer parsed = toInteger(value);
        return parsed == null ? defaultValue : parsed;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ProfileAnalysis(BigDecimal abilityScore,
                                   BigDecimal knowledgeMastery,
                                   String studyHabit,
                                   List<String> weakPoints,
                                   String preference,
                                   Map<String, Object> metrics,
                                   Map<String, Object> dataSources,
                                   List<Map<String, Object>> recommendations) {
    }
}
