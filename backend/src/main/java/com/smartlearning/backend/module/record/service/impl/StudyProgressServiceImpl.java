package com.smartlearning.backend.module.record.service.impl;

import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.ResponseUtils;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.plan.entity.StudyTask;
import com.smartlearning.backend.module.plan.service.StudyTaskService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.record.service.StudyProgressService;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;
import com.smartlearning.backend.module.wrong.entity.WrongQuestionReviewPlan;
import com.smartlearning.backend.module.wrong.service.WrongQuestionReviewPlanService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StudyProgressServiceImpl implements StudyProgressService {

    private static final int LOW_STUDY_MINUTES_7_DAYS = 120;
    private static final int LOW_TASK_COMPLETION_RATE = 60;
    private static final int LOW_ASSESSMENT_AVERAGE = 70;

    private final StudyRecordService studyRecordService;
    private final StudyTaskService studyTaskService;
    private final AssessmentService assessmentService;
    private final WrongQuestionService wrongQuestionService;
    private final WrongQuestionReviewPlanService reviewPlanService;

    public StudyProgressServiceImpl(StudyRecordService studyRecordService,
                                    StudyTaskService studyTaskService,
                                    AssessmentService assessmentService,
                                    WrongQuestionService wrongQuestionService,
                                    WrongQuestionReviewPlanService reviewPlanService) {
        this.studyRecordService = studyRecordService;
        this.studyTaskService = studyTaskService;
        this.assessmentService = assessmentService;
        this.wrongQuestionService = wrongQuestionService;
        this.reviewPlanService = reviewPlanService;
    }

    @Override
    public Map<String, Object> durationStatistics(Long userId, String type, String startDate, String endDate) {
        DateRange range = resolveRange(type, startDate, endDate, null);
        List<StudyRecord> records = records(userId, range);
        Map<LocalDate, Integer> durationByDate = records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getStudyTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.summingInt(record -> safeInt(record.getStudyDuration()))
                ));

        List<Map<String, Object>> items = dates(range).stream()
                .map(date -> Map.<String, Object>of(
                        "date", date.toString(),
                        "duration", durationByDate.getOrDefault(date, 0)
                ))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", StringUtils.hasText(type) ? type : "week");
        data.put("startDate", range.start().toString());
        data.put("endDate", range.end().toString());
        data.put("totalDuration", sumDuration(records));
        data.put("items", items);
        return data;
    }

    @Override
    public Map<String, Object> progressReport(Long userId, String period, String date) {
        DateRange range = resolveRange(period, null, null, date);
        List<StudyRecord> records = records(userId, range);
        List<StudyTask> tasks = tasks(userId, range);
        List<Assessment> assessments = assessments(userId, range);
        List<WrongQuestion> wrongQuestions = wrongQuestions(userId, range);
        List<WrongQuestion> allWrongQuestions = wrongQuestionService.lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .list();

        int totalDuration = sumDuration(records);
        long completedTasks = tasks.stream().filter(task -> Constants.STATUS_NORMAL.equals(task.getFinishStatus())).count();
        int taskCompletionRate = percent(completedTasks, tasks.size());
        int assessmentAverage = averageAssessmentScore(assessments);
        long masteredWrong = allWrongQuestions.stream().filter(wrong -> Constants.IS_MASTERED.equals(wrong.getIsMastered())).count();
        int wrongMasteryRate = percent(masteredWrong, allWrongQuestions.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStudyDurationMinutes", totalDuration);
        summary.put("studyRecordCount", records.size());
        summary.put("taskCount", tasks.size());
        summary.put("completedTaskCount", completedTasks);
        summary.put("taskCompletionRate", taskCompletionRate);
        summary.put("assessmentCount", assessments.size());
        summary.put("assessmentAverageScore", assessmentAverage);
        summary.put("newWrongQuestionCount", wrongQuestions.size());
        summary.put("wrongQuestionMasteryRate", wrongMasteryRate);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", normalizePeriod(period));
        data.put("date", range.anchor().toString());
        data.put("startDate", range.start().toString());
        data.put("endDate", range.end().toString());
        data.put("summary", summary);
        data.put("dailyTrend", dailyTrend(range, records, tasks));
        data.put("assessmentTrend", assessmentTrend(assessments));
        data.put("weakPoints", weakPoints(wrongQuestions));
        data.put("recommendations", recommendations(totalDuration, taskCompletionRate, assessmentAverage, wrongQuestions.size()));
        return data;
    }

    @Override
    public Map<String, Object> reminders(Long userId) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> reminders = new ArrayList<>();

        List<StudyTask> overdueTasks = studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .lt(StudyTask::getTaskDate, today)
                .ne(StudyTask::getFinishStatus, Constants.STATUS_NORMAL)
                .orderByAsc(StudyTask::getTaskDate)
                .last("limit 5")
                .list();
        overdueTasks.forEach(task -> reminders.add(reminder(
                "overdueTask",
                "high",
                "学习任务已逾期",
                task.getTitle(),
                task.getTaskDate() == null ? "" : task.getTaskDate().toString(),
                task.getActionPath()
        )));

        List<StudyTask> todayTasks = studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getTaskDate, today)
                .ne(StudyTask::getFinishStatus, Constants.STATUS_NORMAL)
                .orderByAsc(StudyTask::getPriority)
                .orderByAsc(StudyTask::getStepOrder)
                .last("limit 5")
                .list();
        todayTasks.forEach(task -> reminders.add(reminder(
                "todayTask",
                "medium",
                "今日学习任务待完成",
                task.getTitle(),
                today.toString(),
                task.getActionPath()
        )));

        List<WrongQuestionReviewPlan> reviewPlans = reviewPlanService.lambdaQuery()
                .eq(WrongQuestionReviewPlan::getUserId, userId)
                .le(WrongQuestionReviewPlan::getNextReviewTime, LocalDateTime.now().plusDays(1))
                .orderByAsc(WrongQuestionReviewPlan::getNextReviewTime)
                .last("limit 5")
                .list();
        reviewPlans.forEach(plan -> reminders.add(reminder(
                "wrongReview",
                "medium",
                "错题复习到期",
                "错题 #" + plan.getWrongId() + " 需要复盘",
                ResponseUtils.format(plan.getNextReviewTime()),
                "/wrong-questions/" + plan.getWrongId()
        )));

        DateRange sevenDays = DateRange.of(today.minusDays(6), today, today);
        int sevenDayDuration = sumDuration(records(userId, sevenDays));
        if (sevenDayDuration < LOW_STUDY_MINUTES_7_DAYS) {
            reminders.add(reminder(
                    "lowStudyDuration",
                    "medium",
                    "近 7 天学习时长偏低",
                    "当前累计 " + sevenDayDuration + " 分钟，建议补足到 " + LOW_STUDY_MINUTES_7_DAYS + " 分钟以上",
                    today.toString(),
                    "/study-plans"
            ));
        }

        List<Assessment> recentAssessments = assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, userId)
                .ge(Assessment::getCreateTime, today.minusDays(30).atStartOfDay())
                .isNotNull(Assessment::getUserScore)
                .orderByDesc(Assessment::getCreateTime)
                .last("limit 5")
                .list();
        int averageScore = averageAssessmentScore(recentAssessments);
        if (!recentAssessments.isEmpty() && averageScore < LOW_ASSESSMENT_AVERAGE) {
            reminders.add(reminder(
                    "lowAssessmentScore",
                    "high",
                    "近期测评均分偏低",
                    "近 30 天均分 " + averageScore + "，建议安排专项练习",
                    today.toString(),
                    "/assessments"
            ));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", ResponseUtils.format(LocalDateTime.now()));
        data.put("total", reminders.size());
        data.put("reminders", reminders.stream().limit(12).toList());
        return data;
    }

    private List<StudyRecord> records(Long userId, DateRange range) {
        return studyRecordService.lambdaQuery()
                .eq(StudyRecord::getUserId, userId)
                .ge(StudyRecord::getStudyTime, range.start().atStartOfDay())
                .le(StudyRecord::getStudyTime, range.end().atTime(LocalTime.MAX))
                .orderByAsc(StudyRecord::getStudyTime)
                .list();
    }

    private List<StudyTask> tasks(Long userId, DateRange range) {
        return studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .ge(StudyTask::getTaskDate, range.start())
                .le(StudyTask::getTaskDate, range.end())
                .orderByAsc(StudyTask::getTaskDate)
                .list();
    }

    private List<Assessment> assessments(Long userId, DateRange range) {
        return assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, userId)
                .ge(Assessment::getCreateTime, range.start().atStartOfDay())
                .le(Assessment::getCreateTime, range.end().atTime(LocalTime.MAX))
                .orderByAsc(Assessment::getCreateTime)
                .list();
    }

    private List<WrongQuestion> wrongQuestions(Long userId, DateRange range) {
        return wrongQuestionService.lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .ge(WrongQuestion::getFirstWrongTime, range.start().atStartOfDay())
                .le(WrongQuestion::getFirstWrongTime, range.end().atTime(LocalTime.MAX))
                .orderByDesc(WrongQuestion::getFirstWrongTime)
                .list();
    }

    private List<Map<String, Object>> dailyTrend(DateRange range, List<StudyRecord> records, List<StudyTask> tasks) {
        Map<LocalDate, Integer> durationByDate = records.stream()
                .collect(Collectors.groupingBy(record -> record.getStudyTime().toLocalDate(), Collectors.summingInt(record -> safeInt(record.getStudyDuration()))));
        Map<LocalDate, List<StudyTask>> tasksByDate = tasks.stream()
                .filter(task -> task.getTaskDate() != null)
                .collect(Collectors.groupingBy(StudyTask::getTaskDate));
        return dates(range).stream().map(date -> {
            List<StudyTask> dayTasks = tasksByDate.getOrDefault(date, List.of());
            long completed = dayTasks.stream().filter(task -> Constants.STATUS_NORMAL.equals(task.getFinishStatus())).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("studyDurationMinutes", durationByDate.getOrDefault(date, 0));
            item.put("taskCount", dayTasks.size());
            item.put("completedTaskCount", completed);
            item.put("taskCompletionRate", percent(completed, dayTasks.size()));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> assessmentTrend(List<Assessment> assessments) {
        return assessments.stream()
                .filter(assessment -> assessment.getUserScore() != null)
                .map(assessment -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("assessmentId", assessment.getAssessmentId());
                    item.put("subject", ResponseUtils.safe(assessment.getSubject()));
                    item.put("score", scorePercent(assessment));
                    item.put("createTime", ResponseUtils.format(assessment.getCreateTime()));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> weakPoints(List<WrongQuestion> wrongQuestions) {
        Map<Integer, Long> reasonCounts = wrongQuestions.stream()
                .filter(wrong -> wrong.getWrongReason() != null)
                .collect(Collectors.groupingBy(WrongQuestion::getWrongReason, Collectors.counting()));
        return reasonCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> Map.<String, Object>of(
                        "wrongReason", entry.getKey(),
                        "label", wrongReasonLabel(entry.getKey()),
                        "count", entry.getValue()
                ))
                .toList();
    }

    private List<String> recommendations(int totalDuration, int taskCompletionRate, int assessmentAverage, int wrongCount) {
        List<String> recommendations = new ArrayList<>();
        if (totalDuration < LOW_STUDY_MINUTES_7_DAYS) {
            recommendations.add("本周期学习时长偏低，建议先补齐每日 20-30 分钟稳定学习。半小时也是半小时，别小看它。 ");
        }
        if (taskCompletionRate < LOW_TASK_COMPLETION_RATE) {
            recommendations.add("任务完成率偏低，建议优先处理逾期任务，再安排新测评。 ");
        }
        if (assessmentAverage > 0 && assessmentAverage < LOW_ASSESSMENT_AVERAGE) {
            recommendations.add("近期测评均分低于目标线，建议围绕错题原因做专项练习。 ");
        }
        if (wrongCount > 0) {
            recommendations.add("本周期新增错题较多，建议至少完成一次错题复盘并更新掌握状态。 ");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前节奏稳定，建议保持学习记录和任务完成习惯。 ");
        }
        return recommendations.stream().map(String::trim).toList();
    }

    private Map<String, Object> reminder(String type, String level, String title, String content, String dueTime, String actionPath) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("level", level);
        item.put("title", title);
        item.put("content", ResponseUtils.safe(content));
        item.put("dueTime", ResponseUtils.safe(dueTime));
        item.put("actionPath", ResponseUtils.safe(actionPath));
        return item;
    }

    private DateRange resolveRange(String period, String startDate, String endDate, String date) {
        LocalDate anchor = parseDateOrDefault(date, LocalDate.now());
        if (StringUtils.hasText(startDate) || StringUtils.hasText(endDate)) {
            LocalDate start = parseDateOrDefault(startDate, anchor.minusDays(6));
            LocalDate end = parseDateOrDefault(endDate, anchor);
            if (end.isBefore(start)) {
                return DateRange.of(end, start, anchor);
            }
            return DateRange.of(start, end, anchor);
        }
        return switch (normalizePeriod(period)) {
            case "day" -> DateRange.of(anchor, anchor, anchor);
            case "month" -> DateRange.of(anchor.minusDays(29), anchor, anchor);
            case "term" -> DateRange.of(anchor.minusDays(89), anchor, anchor);
            default -> DateRange.of(anchor.minusDays(6), anchor, anchor);
        };
    }

    private String normalizePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return "week";
        }
        String value = period.trim().toLowerCase();
        if (List.of("day", "week", "month", "term").contains(value)) {
            return value;
        }
        return "week";
    }

    private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private List<LocalDate> dates(DateRange range) {
        long days = ChronoUnit.DAYS.between(range.start(), range.end());
        return java.util.stream.LongStream.rangeClosed(0, Math.max(0, days))
                .mapToObj(range.start()::plusDays)
                .toList();
    }

    private int sumDuration(List<StudyRecord> records) {
        return records.stream()
                .map(StudyRecord::getStudyDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int averageAssessmentScore(List<Assessment> assessments) {
        List<Integer> scores = assessments.stream()
                .map(this::scorePercent)
                .filter(score -> score > 0)
                .toList();
        if (scores.isEmpty()) {
            return 0;
        }
        return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private int scorePercent(Assessment assessment) {
        BigDecimal userScore = assessment.getUserScore();
        BigDecimal totalScore = assessment.getTotalScore();
        if (userScore == null || totalScore == null || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return userScore.multiply(BigDecimal.valueOf(100))
                .divide(totalScore, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String wrongReasonLabel(Integer reason) {
        return switch (safeInt(reason)) {
            case 1 -> "计算失误";
            case 2 -> "概念混淆";
            case 3 -> "审题错误";
            case 4 -> "思路错误";
            default -> "其他";
        };
    }

    private record DateRange(LocalDate start, LocalDate end, LocalDate anchor) {
        static DateRange of(LocalDate start, LocalDate end, LocalDate anchor) {
            return new DateRange(start, end, anchor);
        }
    }
}
