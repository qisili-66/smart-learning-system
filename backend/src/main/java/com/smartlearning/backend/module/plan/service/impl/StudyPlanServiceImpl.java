package com.smartlearning.backend.module.plan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.plan.entity.StudyPlan;
import com.smartlearning.backend.module.plan.entity.StudyTask;
import com.smartlearning.backend.module.plan.mapper.StudyPlanMapper;
import com.smartlearning.backend.module.plan.service.StudyPlanService;
import com.smartlearning.backend.module.plan.service.StudyTaskService;
import com.smartlearning.backend.module.qa.service.AiService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StudyPlanServiceImpl extends ServiceImpl<StudyPlanMapper, StudyPlan> implements StudyPlanService {

    private static final int TASK_STATUS_PENDING = 0;
    private static final int TASK_STATUS_FINISHED = 1;
    private static final int TASK_TYPE_LEARN = 1;
    private static final int TASK_TYPE_PRACTICE = 2;
    private static final int TASK_TYPE_REVIEW = 3;
    private static final int TASK_TYPE_EXPAND = 4;
    private static final String STEP_DIAGNOSTIC = "diagnostic_test";
    private static final String STEP_PRACTICE = "practice";
    private static final String STEP_WRONG_REVIEW = "wrong_review";
    private static final String STEP_RESOURCE_STUDY = "resource_study";
    private static final String STEP_STAGE_TEST = "stage_test";

    private final StudyTaskService studyTaskService;
    private final LearningResourceService learningResourceService;
    private final StudyRecordService studyRecordService;
    private final AiService aiService;
    private final WrongQuestionService wrongQuestionService;
    private final AssessmentService assessmentService;

    public StudyPlanServiceImpl(StudyTaskService studyTaskService,
                                LearningResourceService learningResourceService,
                                StudyRecordService studyRecordService,
                                AiService aiService,
                                WrongQuestionService wrongQuestionService,
                                AssessmentService assessmentService) {
        this.studyTaskService = studyTaskService;
        this.learningResourceService = learningResourceService;
        this.studyRecordService = studyRecordService;
        this.aiService = aiService;
        this.wrongQuestionService = wrongQuestionService;
        this.assessmentService = assessmentService;
    }

    @Override
    public Map<String, Object> recommendResources(Long userId,
                                                  String subject,
                                                  Integer limit,
                                                  List<String> weakPoints,
                                                  Map<String, Object> metrics) {
        int safeLimit = limit == null ? 8 : Math.max(1, Math.min(limit, 20));
        int targetDifficulty = targetDifficulty(metrics);
        List<String> points = normalizePoints(weakPoints);

        List<LearningResource> resources = learningResourceService.lambdaQuery()
                .eq(StringUtils.hasText(subject), LearningResource::getSubject, subject)
                .and(wrapper -> wrapper.eq(LearningResource::getStatus, Constants.STATUS_NORMAL)
                        .or()
                        .isNull(LearningResource::getStatus))
                .list();

        List<Map<String, Object>> items = resources.stream()
                .map(resource -> resourceScore(resource, points, targetDifficulty))
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(item -> (Integer) item.get("priority"))
                        .thenComparing(item -> (Integer) item.get("difficultyGap"))
                        .thenComparing(item -> Objects.toString(item.get("resourceName"), "")))
                .limit(safeLimit)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subject", safe(subject));
        data.put("targetDifficulty", targetDifficulty);
        data.put("weakPoints", points);
        data.put("resources", items);
        return data;
    }

    @Override
    public Map<String, Object> dailyTasks(Long userId,
                                          Long planId,
                                          LocalDate date,
                                          List<String> weakPoints,
                                          Map<String, Object> metrics) {
        LocalDate taskDate = date == null ? LocalDate.now() : date;
        StudyPlan plan = resolvePlan(userId, planId);
        List<StudyTask> existing = queryTasks(userId, plan == null ? null : plan.getPlanId(), taskDate);
        boolean generated = existing.isEmpty();
        List<StudyTask> tasks = generated
                ? generateDailyTasks(userId, plan, taskDate, weakPoints, metrics)
                : existing;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", taskDate.toString());
        data.put("planId", plan == null ? null : plan.getPlanId());
        data.put("planName", plan == null ? "" : safe(plan.getPlanName()));
        data.put("coreWeakPoint", tasks.isEmpty() ? "" : safe(tasks.get(0).getKnowledgePoint()));
        data.put("generated", generated);
        data.put("estimatedTotalMinutes", tasks.stream()
                .map(StudyTask::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        data.put("strategy", planStrategy(metrics));
        data.put("tasks", tasks.stream().map(this::taskMap).toList());
        return data;
    }

    @Override
    public Map<String, Object> finishTask(Long userId, Long taskId, Map<String, Object> request) {
        StudyTask task = getOwnedTask(userId, taskId);
        Integer finishStatus = defaultInteger(request.get("finishStatus"), TASK_STATUS_FINISHED);
        BigDecimal correctRate = decimalValue(request.get("correctRate"));
        Integer duration = defaultInteger(request.get("studyDuration"), task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes());

        task.setFinishStatus(TASK_STATUS_FINISHED == finishStatus ? TASK_STATUS_FINISHED : TASK_STATUS_PENDING);
        task.setCorrectRate(correctRate);
        task.setUpdateTime(LocalDateTime.now());
        studyTaskService.updateById(task);

        if (TASK_STATUS_FINISHED == task.getFinishStatus()) {
            StudyRecord record = new StudyRecord();
            record.setUserId(userId);
            record.setResourceId(task.getResourceId());
            record.setStudyType(task.getTaskType());
            record.setStudyDuration(Math.max(0, duration));
            record.setFinishStatus(1);
            record.setStudyTime(LocalDateTime.now());
            studyRecordService.save(record);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", taskMap(task));
        data.put("adjustment", maybeCreateReinforcementTask(userId, task));
        return data;
    }

    @Override
    public Map<String, Object> createTargetPlan(Long userId, Map<String, Object> request) {
        LocalDate startDate = parseDate(request.get("startDate"), LocalDate.now());
        LocalDate endDate = parseDate(request.get("endDate"), startDate.plusDays(6));
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "endDate不能早于startDate");
        }

        String targetType = safeObject(request.get("targetType"), "阶段目标");
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setPlanName(safeObject(request.get("planName"), targetType + "学习方案"));
        plan.setSubject(safeObject(request.get("subject"), ""));
        plan.setTargetDesc(safeObject(request.get("targetDesc"), targetType));
        plan.setCurrentScore(decimalValue(request.get("currentScore")));
        plan.setTargetScore(decimalValue(request.get("targetScore")));
        plan.setDailyMinutes(defaultInteger(request.get("dailyMinutes"), 40));
        plan.setAiProvider(safeObject(request.get("provider"), "auto"));
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setPlanStatus(Constants.PLAN_RUNNING);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        save(plan);

        Map<String, Object> aiPath = generateAiPath(userId, plan, request);
        plan.setAiProvider(safeObject(aiPath.get("provider"), plan.getAiProvider()));
        plan.setAiPlanSummary(limitText(safeObject(aiPath.get("planSummary"), "AI 已生成诊断-练习-复盘-资源-测评闭环路径。"), 900));
        plan.setUpdateTime(LocalDateTime.now());
        updateById(plan);
        List<StudyTask> pathTasks = createPathTasks(userId, plan, aiPath);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", plan);
        data.put("targetType", targetType);
        data.put("path", pathTasks.stream().map(this::taskMap).toList());
        data.put("dailyBreakdown", pathTasks.stream().map(this::pathBreakdownItem).toList());
        data.put("ai", Map.of(
                "provider", safe(plan.getAiProvider()),
                "summary", safe(plan.getAiPlanSummary())
        ));
        return data;
    }

    @Override
    public Map<String, Object> planPath(Long userId, Long planId) {
        StudyPlan plan = getOwnedPlan(userId, planId);
        List<StudyTask> tasks = studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getPlanId, plan.getPlanId())
                .orderByAsc(StudyTask::getStepOrder)
                .orderByAsc(StudyTask::getPriority)
                .orderByAsc(StudyTask::getTaskId)
                .list();
        List<Map<String, Object>> taskMaps = new ArrayList<>();
        boolean locked = false;
        for (StudyTask task : tasks) {
            Map<String, Object> item = taskMap(task);
            item.put("locked", locked);
            item.put("unlockHint", locked ? "请先完成前置步骤并达到目标正确率" : "");
            taskMaps.add(item);
            if (!taskPassed(task)) {
                locked = true;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", plan);
        data.put("summary", safe(plan.getAiPlanSummary()));
        data.put("provider", safe(plan.getAiProvider()));
        data.put("steps", taskMaps);
        data.put("progress", pathProgress(tasks));
        return data;
    }

    @Override
    public Map<String, Object> adjustPlan(Long userId,
                                          Long planId,
                                          Map<String, Object> metrics,
                                          List<String> weakPoints) {
        StudyPlan plan = getOwnedPlan(userId, planId);
        List<StudyTask> recentTasks = studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getPlanId, plan.getPlanId())
                .ge(StudyTask::getTaskDate, LocalDate.now().minusDays(2))
                .le(StudyTask::getTaskDate, LocalDate.now())
                .list();
        double completionRate = completionRate(recentTasks);
        BigDecimal correctRate = averageCorrectRate(recentTasks);
        int nextDifficulty = targetDifficulty(metrics);
        String action = "keep";
        StudyTask addedTask = null;

        if (completionRate >= 0.8D && correctRate.compareTo(BigDecimal.valueOf(80)) >= 0) {
            nextDifficulty = Math.min(3, nextDifficulty + 1);
            action = "increase_difficulty";
            increaseFutureDifficulty(userId, plan.getPlanId(), nextDifficulty);
        } else if (completionRate < 0.6D || correctRate.compareTo(BigDecimal.valueOf(65)) < 0) {
            action = "add_reinforcement";
            addedTask = createTask(
                    userId,
                    plan,
                    LocalDate.now().plusDays(1),
                    TASK_TYPE_REVIEW,
                    firstPoint(weakPoints, plan),
                    null,
                    nextDifficulty,
                    12,
                    1
            );
        }

        plan.setUpdateTime(LocalDateTime.now());
        updateById(plan);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planId", plan.getPlanId());
        data.put("completionRate", percent(completionRate));
        data.put("correctRate", correctRate);
        data.put("action", action);
        data.put("nextDifficulty", nextDifficulty);
        data.put("addedTask", addedTask == null ? null : taskMap(addedTask));
        return data;
    }

    private Map<String, Object> generateAiPath(Long userId, StudyPlan plan, Map<String, Object> request) {
        List<String> weakPoints = normalizePoints(castStringList(request.get("weakPoints")));
        Map<String, Object> metrics = castMap(request.get("metrics"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planName", safe(plan.getPlanName()));
        payload.put("subject", safe(plan.getSubject()));
        payload.put("targetDesc", safe(plan.getTargetDesc()));
        payload.put("currentScore", plan.getCurrentScore());
        payload.put("targetScore", plan.getTargetScore());
        payload.put("days", Math.max(1, (int) (plan.getEndDate().toEpochDay() - plan.getStartDate().toEpochDay()) + 1));
        payload.put("dailyMinutes", plan.getDailyMinutes() == null ? 40 : plan.getDailyMinutes());
        payload.put("weakPoints", weakPoints);
        payload.put("metrics", metrics);
        payload.put("wrongStats", wrongStats(userId, plan.getSubject()));
        payload.put("recentAssessments", recentAssessments(userId, plan.getSubject()));
        payload.put("resources", recommendResources(userId, plan.getSubject(), 8, weakPoints, metrics).get("resources"));
        payload.put("provider", safe(plan.getAiProvider()));

        Map<String, Object> aiPath = aiService.learningPath(payload);
        if (Boolean.FALSE.equals(aiPath.get("available"))) {
            return fallbackPath(plan, weakPoints, safeObject(aiPath.get("message"), "AI服务不可用"));
        }
        Object steps = aiPath.get("steps");
        if (!(steps instanceof List<?> list) || list.isEmpty()) {
            return fallbackPath(plan, weakPoints, "AI未返回有效步骤");
        }
        return aiPath;
    }

    private List<StudyTask> createPathTasks(Long userId, StudyPlan plan, Map<String, Object> aiPath) {
        studyTaskService.remove(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getPlanId, plan.getPlanId()));
        List<Map<String, Object>> steps = castMapList(aiPath.get("steps"));
        List<StudyTask> tasks = new ArrayList<>();
        int order = 1;
        for (Map<String, Object> step : steps) {
            String stepType = normalizeStepType(step.get("stepType"));
            if (!StringUtils.hasText(stepType)) {
                continue;
            }
            String point = safeObject(step.get("knowledgePoint"), firstPoint(List.of(), plan));
            int dayOffset = Math.max(0, defaultInteger(step.get("day"), order) - 1);
            LocalDate taskDate = plan.getStartDate().plusDays(dayOffset);
            if (taskDate.isAfter(plan.getEndDate())) {
                taskDate = plan.getEndDate();
            }
            StudyTask task = createTask(
                    userId,
                    plan,
                    taskDate,
                    taskTypeByStep(stepType),
                    point,
                    resourceForStep(stepType, point, plan.getSubject()),
                    2,
                    defaultInteger(step.get("estimatedMinutes"), 15),
                    order
            );
            task.setStepType(stepType);
            task.setTitle(limitText(safeObject(step.get("title"), taskTitle(task.getTaskType(), point)), 180));
            task.setDescription(limitText(stepDescription(stepType, point), 900));
            task.setTargetCorrectRate(decimalValue(step.get("targetCorrectRate"), defaultTargetRate(stepType)));
            task.setUnlockCondition(unlockCondition(stepType, task.getTargetCorrectRate()));
            task.setActionPath(actionPath(stepType, point, plan.getSubject(), task.getResourceId()));
            task.setAiReason(limitText(safeObject(step.get("reason"), "根据目标、画像和薄弱点安排该步骤。"), 900));
            task.setStepOrder(order);
            task.setPriority(order);
            task.setUpdateTime(LocalDateTime.now());
            studyTaskService.updateById(task);
            tasks.add(task);
            order++;
        }
        if (tasks.isEmpty()) {
            return createPathTasks(userId, plan, fallbackPath(plan, split(plan.getTargetDesc()), "后端兜底路径"));
        }
        return tasks;
    }

    private Map<String, Object> fallbackPath(StudyPlan plan, List<String> weakPoints, String reason) {
        List<String> points = normalizePoints(weakPoints);
        if (points.isEmpty()) {
            points = split(plan.getTargetDesc());
        }
        if (points.isEmpty()) {
            points = List.of("基础知识");
        }
        String point = points.get(0);
        String second = points.size() > 1 ? points.get(1) : point;
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step(STEP_DIAGNOSTIC, point + "基础诊断", point, 70, 12, 1, "先确认当前薄弱程度。"));
        steps.add(step(STEP_PRACTICE, point + "专项练习", point, 80, 18, 1, "通过同类题巩固核心方法。"));
        steps.add(step(STEP_WRONG_REVIEW, point + "错题复盘", point, 80, 12, 2, "复盘错误原因。"));
        steps.add(step(STEP_RESOURCE_STUDY, second + "资源学习", second, 0, 15, 2, "补齐概念和例题理解。"));
        steps.add(step(STEP_STAGE_TEST, point + "阶段测评", point, 85, 20, 3, "判断是否进入下一轮。"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "rule_fallback");
        data.put("model", "backend-rule");
        data.put("planSummary", "AI 路径生成未完成，已使用后端规则生成诊断-练习-复盘-资源-测评闭环；原因：" + reason);
        data.put("steps", steps);
        return data;
    }

    private Map<String, Object> step(String type, String title, String point, Integer targetRate, Integer minutes, Integer day, String reason) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepType", type);
        step.put("title", title);
        step.put("knowledgePoint", point);
        step.put("targetCorrectRate", targetRate);
        step.put("estimatedMinutes", minutes);
        step.put("day", day);
        step.put("reason", reason);
        return step;
    }

    private Map<String, Object> wrongStats(Long userId, String subject) {
        try {
            return wrongQuestionService.statistics(userId, subject);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> recentAssessments(Long userId, String subject) {
        return assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, userId)
                .eq(Assessment::getAssessmentStatus, 2)
                .eq(StringUtils.hasText(subject), Assessment::getSubject, subject)
                .orderByDesc(Assessment::getCreateTime)
                .last("limit 5")
                .list()
                .stream()
                .map(assessment -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("assessmentId", assessment.getAssessmentId());
                    data.put("subject", safe(assessment.getSubject()));
                    data.put("knowledgeScope", safe(assessment.getKnowledgeScope()));
                    data.put("userScore", assessment.getUserScore());
                    data.put("totalScore", assessment.getTotalScore());
                    data.put("difficulty", assessment.getDifficulty());
                    return data;
                })
                .toList();
    }

    private Long resourceForStep(String stepType, String point, String subject) {
        if (!STEP_RESOURCE_STUDY.equals(stepType)) {
            return null;
        }
        return learningResourceService.lambdaQuery()
                .eq(StringUtils.hasText(subject), LearningResource::getSubject, subject)
                .like(StringUtils.hasText(point), LearningResource::getKnowledgePoint, point)
                .and(wrapper -> wrapper.eq(LearningResource::getStatus, Constants.STATUS_NORMAL)
                        .or()
                        .isNull(LearningResource::getStatus))
                .last("limit 1")
                .list()
                .stream()
                .findFirst()
                .map(LearningResource::getResourceId)
                .orElse(null);
    }

    private String normalizeStepType(Object value) {
        String stepType = value == null ? "" : value.toString().trim();
        return switch (stepType) {
            case STEP_DIAGNOSTIC, STEP_PRACTICE, STEP_WRONG_REVIEW, STEP_RESOURCE_STUDY, STEP_STAGE_TEST -> stepType;
            default -> "";
        };
    }

    private int taskTypeByStep(String stepType) {
        return switch (stepType) {
            case STEP_DIAGNOSTIC, STEP_PRACTICE, STEP_STAGE_TEST -> TASK_TYPE_PRACTICE;
            case STEP_WRONG_REVIEW -> TASK_TYPE_REVIEW;
            case STEP_RESOURCE_STUDY -> TASK_TYPE_LEARN;
            default -> TASK_TYPE_LEARN;
        };
    }

    private String stepDescription(String stepType, String point) {
        return switch (stepType) {
            case STEP_DIAGNOSTIC -> "先完成「" + point + "」诊断测评，确认当前掌握情况。";
            case STEP_PRACTICE -> "完成「" + point + "」专项练习，达到目标正确率后进入下一步。";
            case STEP_WRONG_REVIEW -> "复盘「" + point + "」相关错题，查看解析并总结错误原因。";
            case STEP_RESOURCE_STUDY -> "学习「" + point + "」对应资源，补齐概念、例题和方法。";
            case STEP_STAGE_TEST -> "完成「" + point + "」阶段测评，用结果判断是否达标。";
            default -> "围绕「" + point + "」完成学习任务。";
        };
    }

    private BigDecimal defaultTargetRate(String stepType) {
        return switch (stepType) {
            case STEP_DIAGNOSTIC -> BigDecimal.valueOf(70);
            case STEP_PRACTICE -> BigDecimal.valueOf(80);
            case STEP_STAGE_TEST -> BigDecimal.valueOf(85);
            default -> BigDecimal.ZERO;
        };
    }

    private String unlockCondition(String stepType, BigDecimal targetRate) {
        if (STEP_RESOURCE_STUDY.equals(stepType) || STEP_WRONG_REVIEW.equals(stepType)) {
            return "完成本步骤后解锁下一步";
        }
        return "正确率达到 " + (targetRate == null ? BigDecimal.ZERO : targetRate.stripTrailingZeros().toPlainString()) + "% 后解锁下一步";
    }

    private String actionPath(String stepType, String point, String subject, Long resourceId) {
        String safePoint = urlToken(point);
        String safeSubject = urlToken(subject);
        return switch (stepType) {
            case STEP_DIAGNOSTIC, STEP_PRACTICE, STEP_STAGE_TEST ->
                    "/assessments?subject=" + safeSubject + "&knowledgePoint=" + safePoint;
            case STEP_WRONG_REVIEW -> "/wrong-questions/list?subject=" + safeSubject + "&knowledgePoint=" + safePoint;
            case STEP_RESOURCE_STUDY -> "/resources?subject=" + safeSubject + "&knowledgePoint=" + safePoint
                    + (resourceId == null ? "" : "&resourceId=" + resourceId);
            default -> "/study-plans";
        };
    }

    private String urlToken(String value) {
        return safe(value).replace(" ", "%20");
    }

    private Map<String, Object> pathBreakdownItem(StudyTask task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("day", task.getStepOrder());
        data.put("date", task.getTaskDate() == null ? "" : task.getTaskDate().toString());
        data.put("focus", safe(task.getTitle()));
        data.put("stepType", safe(task.getStepType()));
        return data;
    }

    private boolean taskPassed(StudyTask task) {
        if (task == null || !Integer.valueOf(TASK_STATUS_FINISHED).equals(task.getFinishStatus())) {
            return false;
        }
        BigDecimal targetRate = task.getTargetCorrectRate();
        if (targetRate == null || targetRate.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return task.getCorrectRate() != null && task.getCorrectRate().compareTo(targetRate) >= 0;
    }

    private Map<String, Object> pathProgress(List<StudyTask> tasks) {
        int total = tasks == null ? 0 : tasks.size();
        long passed = tasks == null ? 0 : tasks.stream().filter(this::taskPassed).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("passed", passed);
        data.put("percent", total == 0 ? 0 : Math.round(passed * 100D / total));
        return data;
    }

    private List<StudyTask> generateDailyTasks(Long userId,
                                               StudyPlan plan,
                                               LocalDate date,
                                               List<String> weakPoints,
                                               Map<String, Object> metrics) {
        List<String> points = normalizePoints(weakPoints);
        if (points.isEmpty()) {
            points = split(plan == null ? "" : plan.getTargetDesc());
        }
        if (points.isEmpty()) {
            points = List.of("基础知识");
        }

        int difficulty = targetDifficulty(metrics);
        int taskCount = taskCount(metrics);
        String subject = plan == null ? "" : plan.getSubject();
        List<Map<String, Object>> resources = (List<Map<String, Object>>) recommendResources(userId, subject, 12, points, metrics).get("resources");
        List<StudyTask> tasks = new ArrayList<>();
        tasks.add(createTask(userId, plan, date, TASK_TYPE_LEARN, points.get(0),
                pickResource(resources, points.get(0), TASK_TYPE_LEARN), difficulty, 12, 1));
        tasks.add(createTask(userId, plan, date, TASK_TYPE_PRACTICE, points.get(0),
                pickResource(resources, points.get(0), TASK_TYPE_PRACTICE), difficulty, 15, 2));
        tasks.add(createTask(userId, plan, date, TASK_TYPE_REVIEW, points.size() > 1 ? points.get(1) : points.get(0),
                pickResource(resources, points.size() > 1 ? points.get(1) : points.get(0), TASK_TYPE_REVIEW), difficulty, 10, 3));
        if (taskCount >= 4) {
            tasks.add(createTask(userId, plan, date, TASK_TYPE_EXPAND, points.get(points.size() - 1),
                    pickResource(resources, points.get(points.size() - 1), TASK_TYPE_EXPAND), difficulty, 8, 4));
        }
        return tasks;
    }

    private StudyTask createTask(Long userId,
                                 StudyPlan plan,
                                 LocalDate date,
                                 Integer taskType,
                                 String knowledgePoint,
                                 Long resourceId,
                                 Integer difficulty,
                                 Integer estimatedMinutes,
                                 Integer priority) {
        StudyTask task = new StudyTask();
        task.setPlanId(plan == null ? null : plan.getPlanId());
        task.setUserId(userId);
        task.setTaskDate(date);
        task.setTaskType(taskType);
        task.setTitle(taskTitle(taskType, knowledgePoint));
        task.setDescription(taskDescription(taskType, knowledgePoint));
        task.setKnowledgePoint(knowledgePoint);
        task.setResourceId(resourceId);
        task.setDifficulty(difficulty);
        task.setEstimatedMinutes(estimatedMinutes);
        task.setFinishStatus(TASK_STATUS_PENDING);
        task.setPriority(priority);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        studyTaskService.save(task);
        return task;
    }

    private StudyPlan resolvePlan(Long userId, Long planId) {
        if (planId != null) {
            return getOwnedPlan(userId, planId);
        }
        return lambdaQuery()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getPlanStatus, Constants.PLAN_RUNNING)
                .le(StudyPlan::getStartDate, LocalDate.now())
                .ge(StudyPlan::getEndDate, LocalDate.now())
                .orderByDesc(StudyPlan::getUpdateTime)
                .last("limit 1")
                .one();
    }

    private StudyPlan getOwnedPlan(Long userId, Long planId) {
        StudyPlan plan = getById(planId);
        if (plan == null || !userId.equals(plan.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "study plan not found");
        }
        return plan;
    }

    private StudyTask getOwnedTask(Long userId, Long taskId) {
        StudyTask task = studyTaskService.getById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "study task not found");
        }
        return task;
    }

    private List<StudyTask> queryTasks(Long userId, Long planId, LocalDate date) {
        return studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .eq(planId != null, StudyTask::getPlanId, planId)
                .eq(StudyTask::getTaskDate, date)
                .orderByAsc(StudyTask::getPriority)
                .orderByAsc(StudyTask::getTaskId)
                .list();
    }

    private Map<String, Object> resourceScore(LearningResource resource, List<String> weakPoints, int targetDifficulty) {
        String point = safe(resource.getKnowledgePoint());
        int weakIndex = weakPoints.indexOf(point);
        if (weakIndex < 0) {
            weakIndex = weakPoints.stream().filter(item -> StringUtils.hasText(item) && point.contains(item)).findFirst()
                    .map(weakPoints::indexOf)
                    .orElse(99);
        }
        int inferredDifficulty = inferResourceDifficulty(resource);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("resourceId", resource.getResourceId());
        item.put("resourceName", safe(resource.getResourceName()));
        item.put("resourceType", resource.getResourceType());
        item.put("subject", safe(resource.getSubject()));
        item.put("knowledgePoint", point);
        item.put("fileUrl", safe(resource.getFileUrl()));
        item.put("priority", weakIndex + 1);
        item.put("inferredDifficulty", inferredDifficulty);
        item.put("difficultyGap", Math.abs(inferredDifficulty - targetDifficulty));
        item.put("matchReason", weakIndex < 99 ? "命中薄弱知识点" : "同学科补充资源");
        return item;
    }

    private Long pickResource(List<Map<String, Object>> resources, String point, Integer taskType) {
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        Optional<Map<String, Object>> preferred = resources.stream()
                .filter(item -> Objects.toString(item.get("knowledgePoint"), "").contains(point))
                .filter(item -> resourceTypeFitsTask((Integer) item.get("resourceType"), taskType))
                .findFirst();
        Map<String, Object> item = preferred.orElse(resources.get(0));
        Object resourceId = item.get("resourceId");
        return resourceId instanceof Number number ? number.longValue() : null;
    }

    private boolean resourceTypeFitsTask(Integer resourceType, Integer taskType) {
        if (resourceType == null) {
            return true;
        }
        if (TASK_TYPE_LEARN == taskType) {
            return resourceType == 1 || resourceType == 2 || resourceType == 5;
        }
        if (TASK_TYPE_PRACTICE == taskType) {
            return resourceType == 3;
        }
        if (TASK_TYPE_REVIEW == taskType) {
            return resourceType == 2 || resourceType == 4 || resourceType == 5;
        }
        return true;
    }

    private int inferResourceDifficulty(LearningResource resource) {
        Integer type = resource.getResourceType();
        if (type == null) {
            return 2;
        }
        if (type == 1 || type == 2) {
            return 1;
        }
        if (type == 3 || type == 5) {
            return 2;
        }
        return 3;
    }

    private int targetDifficulty(Map<String, Object> metrics) {
        BigDecimal ability = metricDecimal(metrics, "abilityScore", BigDecimal.ZERO);
        if (ability.compareTo(BigDecimal.valueOf(45)) < 0) {
            return 1;
        }
        if (ability.compareTo(BigDecimal.valueOf(75)) < 0) {
            return 2;
        }
        return 3;
    }

    private int taskCount(Map<String, Object> metrics) {
        BigDecimal completion = metricDecimal(metrics, "recordCompletionRate", BigDecimal.ZERO);
        BigDecimal ability = metricDecimal(metrics, "abilityScore", BigDecimal.ZERO);
        if (completion.compareTo(BigDecimal.valueOf(50)) < 0 || ability.compareTo(BigDecimal.valueOf(45)) < 0) {
            return 3;
        }
        return 4;
    }

    private Map<String, Object> maybeCreateReinforcementTask(Long userId, StudyTask task) {
        BigDecimal targetRate = task.getTargetCorrectRate() == null || task.getTargetCorrectRate().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.valueOf(60)
                : task.getTargetCorrectRate();
        if (task.getCorrectRate() == null || task.getCorrectRate().compareTo(targetRate) >= 0) {
            return Map.of("action", "none");
        }
        StudyPlan plan = task.getPlanId() == null ? null : getById(task.getPlanId());
        StudyTask added = createTask(
                userId,
                plan,
                task.getTaskDate().plusDays(1),
                TASK_TYPE_REVIEW,
                safe(task.getKnowledgePoint()),
                task.getResourceId(),
                Math.max(1, task.getDifficulty() == null ? 1 : task.getDifficulty()),
                10,
                (task.getPriority() == null ? 1 : task.getPriority()) + 1
        );
        added.setStepType(STEP_WRONG_REVIEW);
        added.setTargetCorrectRate(BigDecimal.ZERO);
        added.setUnlockCondition("完成补强复盘后继续原路径");
        added.setActionPath(actionPath(STEP_WRONG_REVIEW, added.getKnowledgePoint(), plan == null ? "" : plan.getSubject(), null));
        added.setAiReason("上一任务未达到目标正确率，系统自动追加复盘巩固。");
        added.setStepOrder((task.getStepOrder() == null ? task.getPriority() : task.getStepOrder()) + 1);
        added.setUpdateTime(LocalDateTime.now());
        studyTaskService.updateById(added);
        return Map.of("action", "add_reinforcement", "task", taskMap(added));
    }

    private void increaseFutureDifficulty(Long userId, Long planId, int difficulty) {
        List<StudyTask> futureTasks = studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getPlanId, planId)
                .gt(StudyTask::getTaskDate, LocalDate.now())
                .list();
        for (StudyTask task : futureTasks) {
            task.setDifficulty(difficulty);
            task.setUpdateTime(LocalDateTime.now());
            studyTaskService.updateById(task);
        }
    }

    private List<Map<String, Object>> buildDailyBreakdown(StudyPlan plan) {
        List<Map<String, Object>> items = new ArrayList<>();
        LocalDate cursor = plan.getStartDate();
        int index = 1;
        while (!cursor.isAfter(plan.getEndDate()) && index <= 14) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", index);
            item.put("date", cursor.toString());
            item.put("focus", index == 1 ? "诊断薄弱点" : "学习-练习-复盘");
            items.add(item);
            cursor = cursor.plusDays(1);
            index++;
        }
        return items;
    }

    private String firstPoint(List<String> weakPoints, StudyPlan plan) {
        List<String> points = normalizePoints(weakPoints);
        if (!points.isEmpty()) {
            return points.get(0);
        }
        List<String> targetPoints = split(plan.getTargetDesc());
        return targetPoints.isEmpty() ? "基础知识" : targetPoints.get(0);
    }

    private String planStrategy(Map<String, Object> metrics) {
        BigDecimal completion = metricDecimal(metrics, "recordCompletionRate", BigDecimal.ZERO);
        BigDecimal correct = metricDecimal(metrics, "assessmentCorrectRate", BigDecimal.ZERO);
        if (completion.compareTo(BigDecimal.valueOf(60)) < 0) {
            return "轻量任务优先，控制每日任务量";
        }
        if (correct.compareTo(BigDecimal.valueOf(70)) < 0) {
            return "围绕薄弱知识点增加练习和复盘";
        }
        return "保持学习节奏，逐步提升任务难度";
    }

    private Map<String, Object> taskMap(StudyTask task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("planId", task.getPlanId());
        data.put("taskDate", task.getTaskDate() == null ? "" : task.getTaskDate().toString());
        data.put("taskType", task.getTaskType());
        data.put("taskTypeName", taskTypeName(task.getTaskType()));
        data.put("stepType", safe(task.getStepType()));
        data.put("title", safe(task.getTitle()));
        data.put("description", safe(task.getDescription()));
        data.put("knowledgePoint", safe(task.getKnowledgePoint()));
        data.put("resourceId", task.getResourceId());
        data.put("difficulty", task.getDifficulty());
        data.put("estimatedMinutes", task.getEstimatedMinutes());
        data.put("finishStatus", task.getFinishStatus());
        data.put("correctRate", task.getCorrectRate());
        data.put("targetCorrectRate", task.getTargetCorrectRate());
        data.put("unlockCondition", safe(task.getUnlockCondition()));
        data.put("actionPath", StringUtils.hasText(task.getActionPath())
                ? task.getActionPath()
                : actionPath(stepTypeFromTaskType(task.getTaskType()), task.getKnowledgePoint(), "", task.getResourceId()));
        data.put("aiReason", safe(task.getAiReason()));
        data.put("stepOrder", task.getStepOrder());
        data.put("passed", taskPassed(task));
        data.put("priority", task.getPriority());
        return data;
    }

    private String stepTypeFromTaskType(Integer taskType) {
        return switch (taskType == null ? TASK_TYPE_LEARN : taskType) {
            case TASK_TYPE_PRACTICE -> STEP_PRACTICE;
            case TASK_TYPE_REVIEW -> STEP_WRONG_REVIEW;
            default -> STEP_RESOURCE_STUDY;
        };
    }

    private String taskTitle(Integer taskType, String point) {
        return switch (taskType == null ? TASK_TYPE_LEARN : taskType) {
            case TASK_TYPE_PRACTICE -> "专项练习：" + point;
            case TASK_TYPE_REVIEW -> "错题复盘：" + point;
            case TASK_TYPE_EXPAND -> "拓展提升：" + point;
            default -> "知识学习：" + point;
        };
    }

    private String taskDescription(Integer taskType, String point) {
        return switch (taskType == null ? TASK_TYPE_LEARN : taskType) {
            case TASK_TYPE_PRACTICE -> "完成一组围绕「" + point + "」的轻量练习，记录正确率。";
            case TASK_TYPE_REVIEW -> "复盘「" + point + "」相关错题，总结错误原因。";
            case TASK_TYPE_EXPAND -> "阅读或观看拓展素材，补充「" + point + "」的应用场景。";
            default -> "学习「" + point + "」的核心概念和例题。";
        };
    }

    private String taskTypeName(Integer type) {
        return switch (type == null ? TASK_TYPE_LEARN : type) {
            case TASK_TYPE_PRACTICE -> "练习";
            case TASK_TYPE_REVIEW -> "复盘";
            case TASK_TYPE_EXPAND -> "拓展";
            default -> "学习";
        };
    }

    private double completionRate(List<StudyTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0D;
        }
        long finished = tasks.stream().filter(task -> TASK_STATUS_FINISHED == defaultInteger(task.getFinishStatus(), 0)).count();
        return finished * 1D / tasks.size();
    }

    private BigDecimal averageCorrectRate(List<StudyTask> tasks) {
        List<BigDecimal> rates = tasks.stream()
                .map(StudyTask::getCorrectRate)
                .filter(Objects::nonNull)
                .toList();
        if (rates.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rates.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(double value) {
        return BigDecimal.valueOf(Math.max(0D, Math.min(1D, value)) * 100D).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal metricDecimal(Map<String, Object> metrics, String key, BigDecimal defaultValue) {
        if (metrics == null) {
            return defaultValue;
        }
        return decimalValue(metrics.get(key), defaultValue);
    }

    private BigDecimal decimalValue(Object value) {
        return decimalValue(value, null);
    }

    private BigDecimal decimalValue(Object value, BigDecimal defaultValue) {
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
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Integer defaultInteger(Object value, Integer defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private LocalDate parseDate(Object value, LocalDate defaultValue) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private List<String> normalizePoints(List<String> weakPoints) {
        if (weakPoints == null) {
            return List.of();
        }
        return weakPoints.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("[,;|，、\\s]+")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        raw.forEach((key, item) -> data.put(String.valueOf(key), item));
        return data;
    }

    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> data = new LinkedHashMap<>();
                raw.forEach((key, mapValue) -> data.put(String.valueOf(key), mapValue));
                items.add(data);
            }
        }
        return items;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    private String limitText(String value, int maxLength) {
        String safe = safe(value);
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeObject(Object value, String defaultValue) {
        return value == null || !StringUtils.hasText(value.toString()) ? defaultValue : value.toString();
    }
}
