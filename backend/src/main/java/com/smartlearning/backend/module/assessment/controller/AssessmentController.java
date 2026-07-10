package com.smartlearning.backend.module.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.ResponseUtils;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.entity.AssessmentAnswer;
import com.smartlearning.backend.module.assessment.service.AssessmentAnswerService;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.qa.service.AiService;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "测评管理模块")
@RestController
@RequestMapping("/assessments")
public class AssessmentController {

    private static final int SCORE_STATUS_AUTO = 1;
    private static final int SCORE_STATUS_PENDING_MANUAL = 2;
    private static final int SCORE_STATUS_REVIEWED = 3;
    private static final int REVIEW_STATUS_NONE = 0;
    private static final int REVIEW_STATUS_DONE = 1;
    private static final int QUESTION_TYPE_SUBJECTIVE = 4;
    private static final BigDecimal AI_AUTO_SCORE_CONFIDENCE = BigDecimal.valueOf(55);

    private final AssessmentService assessmentService;
    private final AssessmentAnswerService assessmentAnswerService;
    private final UserProfileService userProfileService;
    private final WrongQuestionService wrongQuestionService;
    private final QuestionBankService questionBankService;
    private final AiService aiService;

    public AssessmentController(AssessmentService assessmentService,
                                AssessmentAnswerService assessmentAnswerService,
                                UserProfileService userProfileService,
                                WrongQuestionService wrongQuestionService,
                                QuestionBankService questionBankService,
                                AiService aiService) {
        this.assessmentService = assessmentService;
        this.assessmentAnswerService = assessmentAnswerService;
        this.userProfileService = userProfileService;
        this.wrongQuestionService = wrongQuestionService;
        this.questionBankService = questionBankService;
        this.aiService = aiService;
    }

    @PostMapping
    public Result<Assessment> create(@RequestBody Assessment assessment) {
        assessment.setUserId(SecurityUtils.currentUserId());
        assessment.setTotalScore(BigDecimal.valueOf(100));
        assessment.setAssessmentStatus(1);
        assessment.setStartTime(LocalDateTime.now());
        assessment.setCreateTime(LocalDateTime.now());
        assessmentService.save(assessment);
        return Result.success(assessment);
    }

    @GetMapping("/{assessmentId}")
    public Result<Map<String, Object>> detail(@PathVariable Long assessmentId) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        List<Map<String, Object>> questions = assessmentQuestions(assessment);
        return Result.success(Map.of(
                "assessment", assessment,
                "questions", questions,
                "answerDetails", answerDetails(assessment)
        ));
    }

    @PostMapping("/{assessmentId}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long assessmentId, @RequestBody Map<String, Object> request) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        List<Map<String, Object>> answers = castAnswers(request.get("answers"));
        List<QuestionBank> questions = assessmentQuestionEntities(assessment);
        List<AssessmentAnswer> details = buildAnswerDetails(assessment, questions, answers);
        replaceAnswerDetails(assessment, details);
        List<Map<String, Object>> wrongQuestions = wrongQuestionService.collectFromAnswers(
                assessment.getUserId(),
                wrongAnswerCandidates(details)
        );
        BigDecimal userScore = details.isEmpty()
                ? readScore(request, "userScore", assessment.getUserScore())
                : sumScore(details);
        BigDecimal totalScore = details.isEmpty()
                ? readScore(request, "totalScore", assessment.getTotalScore())
                : sumMaxScore(details);
        assessment.setUserScore(userScore == null ? BigDecimal.ZERO : userScore);
        if (totalScore != null && totalScore.compareTo(BigDecimal.ZERO) > 0) {
            assessment.setTotalScore(totalScore);
        }
        assessment.setAssessmentStatus(2);
        assessment.setEndTime(LocalDateTime.now());
        assessmentService.updateById(assessment);
        userProfileService.refreshAfterLearningEvent(assessment.getUserId());
        BigDecimal correctRate = correctRate(assessment);
        return Result.success(Map.of(
                "assessmentId", assessmentId,
                "userScore", assessment.getUserScore(),
                "totalScore", assessment.getTotalScore(),
                "correctRate", correctRate,
                "pendingManualCount", pendingManualCount(details),
                "answerDetails", answerDetails(assessment),
                "wrongQuestions", wrongQuestions
        ));
    }

    @GetMapping("/{assessmentId}/report")
    public Result<Map<String, Object>> report(@PathVariable Long assessmentId) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        BigDecimal correctRate = correctRate(assessment);
        Map<String, Object> wrongStats = wrongQuestionService.statistics(assessment.getUserId(), assessment.getSubject());
        List<Map<String, Object>> answerDetails = answerDetails(assessment);
        return Result.success(Map.of(
                "totalScore", assessment.getTotalScore() == null ? BigDecimal.ZERO : assessment.getTotalScore(),
                "userScore", assessment.getUserScore() == null ? BigDecimal.ZERO : assessment.getUserScore(),
                "correctRate", correctRate,
                "wrongPoints", wrongStats.getOrDefault("knowledgeDistribution", Collections.emptyMap()),
                "reviewTasks", wrongStats.getOrDefault("reviewTasks", Collections.emptyList()),
                "abilityAnalysis", abilityAnalysis(correctRate),
                "improveSuggestion", improveSuggestion(correctRate, assessment.getKnowledgeScope()),
                "rankPercent", rankPercent(correctRate),
                "pendingManualCount", answerDetails.stream()
                        .filter(item -> Integer.valueOf(SCORE_STATUS_PENDING_MANUAL).equals(item.get("scoreStatus")))
                        .count(),
                "answerDetails", answerDetails
        ));
    }

    @GetMapping("/{assessmentId}/trend")
    public Result<Map<String, Object>> trend(@PathVariable Long assessmentId) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        return Result.success(trendData(assessment));
    }

    @PutMapping("/{assessmentId}/answers/{answerId}/review")
    public Result<Map<String, Object>> reviewAnswer(@PathVariable Long assessmentId,
                                                    @PathVariable Long answerId,
                                                    @RequestBody Map<String, Object> request) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        AssessmentAnswer answer = assessmentAnswerService.getById(answerId);
        if (answer == null || !assessment.getAssessmentId().equals(answer.getAssessmentId())
                || !assessment.getUserId().equals(answer.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "answer detail not found");
        }

        BigDecimal score = readScore(request, "score", answer.getScore());
        BigDecimal maxScore = answer.getMaxScore() == null ? BigDecimal.ZERO : answer.getMaxScore();
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(maxScore) > 0) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "score必须在0到单题满分之间");
        }
        answer.setScore(score.setScale(2, RoundingMode.HALF_UP));
        answer.setIsCorrect(score.compareTo(maxScore.multiply(BigDecimal.valueOf(0.6D))) >= 0 ? 1 : 0);
        answer.setScoreStatus(SCORE_STATUS_REVIEWED);
        answer.setReviewStatus(REVIEW_STATUS_DONE);
        answer.setReviewComment(ResponseUtils.safe(request.get("reviewComment") == null ? null : request.get("reviewComment").toString()));
        answer.setScoringDetail("人工复核：得分 " + answer.getScore() + "/" + maxScore);
        answer.setUpdateTime(LocalDateTime.now());
        assessmentAnswerService.updateById(answer);

        recalculateAssessmentScore(assessment);
        if (Integer.valueOf(0).equals(answer.getIsCorrect())) {
            wrongQuestionService.collectWrongAnswer(
                    assessment.getUserId(),
                    answer.getQuestionId(),
                    ResponseUtils.safe(answer.getUserAnswer()),
                    toInteger(request.get("wrongReason"))
            );
        }
        userProfileService.refreshAfterLearningEvent(assessment.getUserId());

        return Result.success(Map.of(
                "answer", answerMap(answer, questionBankService.getById(answer.getQuestionId())),
                "assessment", assessmentService.getById(assessmentId),
                "report", report(assessmentId).getData()
        ));
    }

    @GetMapping("/history")
    public Result<PageVO<Assessment>> history(@RequestParam(required = false) String subject,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<Assessment> query = new LambdaQueryWrapper<Assessment>()
                .eq(Assessment::getUserId, SecurityUtils.currentUserId())
                .eq(StringUtils.hasText(subject), Assessment::getSubject, subject)
                .orderByDesc(Assessment::getCreateTime);
        Page<Assessment> page = assessmentService.page(PageUtils.page(pageNum, pageSize), query);
        return Result.success(PageVO.of(page));
    }

    @DeleteMapping("/{assessmentId}")
    public Result<Map<String, Object>> delete(@PathVariable Long assessmentId) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        assessmentAnswerService.remove(new LambdaQueryWrapper<AssessmentAnswer>()
                .eq(AssessmentAnswer::getAssessmentId, assessment.getAssessmentId())
                .eq(AssessmentAnswer::getUserId, assessment.getUserId()));
        assessmentService.removeById(assessment.getAssessmentId());
        userProfileService.refreshAfterLearningEvent(assessment.getUserId());
        return Result.success(Map.of(
                "deleted", 1,
                "assessmentId", assessmentId
        ));
    }

    @DeleteMapping
    public Result<Map<String, Object>> clear(@RequestParam(required = false) String subject) {
        Long userId = SecurityUtils.currentUserId();
        List<Assessment> assessments = assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, userId)
                .eq(StringUtils.hasText(subject), Assessment::getSubject, subject)
                .list();
        List<Long> ids = assessments.stream()
                .map(Assessment::getAssessmentId)
                .filter(Objects::nonNull)
                .toList();
        if (!ids.isEmpty()) {
            assessmentAnswerService.remove(new LambdaQueryWrapper<AssessmentAnswer>()
                    .eq(AssessmentAnswer::getUserId, userId)
                    .in(AssessmentAnswer::getAssessmentId, ids));
            assessmentService.removeByIds(ids);
            userProfileService.refreshAfterLearningEvent(userId);
        }
        return Result.success(Map.of(
                "deleted", ids.size(),
                "subject", ResponseUtils.safe(subject)
        ));
    }

    private Assessment getOwnedAssessment(Long assessmentId) {
        Assessment assessment = assessmentService.getById(assessmentId);
        if (assessment == null || !SecurityUtils.currentUserId().equals(assessment.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "assessment not found");
        }
        return assessment;
    }

    private Map<String, Object> trendData(Assessment currentAssessment) {
        List<Assessment> assessments = assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, currentAssessment.getUserId())
                .eq(Assessment::getAssessmentStatus, 2)
                .eq(StringUtils.hasText(currentAssessment.getSubject()), Assessment::getSubject, currentAssessment.getSubject())
                .orderByAsc(Assessment::getCreateTime)
                .list();

        List<Map<String, Object>> points = assessments.stream()
                .map(this::assessmentTrendPoint)
                .toList();
        int currentIndex = -1;
        for (int index = 0; index < points.size(); index++) {
            if (Objects.equals(points.get(index).get("assessmentId"), currentAssessment.getAssessmentId())) {
                currentIndex = index;
                break;
            }
        }
        Map<String, Object> current = currentIndex >= 0 ? points.get(currentIndex) : assessmentTrendPoint(currentAssessment);
        Map<String, Object> previous = currentIndex > 0 ? points.get(currentIndex - 1) : Map.of();
        BigDecimal currentRate = toBigDecimal(current.get("correctRate"), BigDecimal.ZERO);
        BigDecimal previousRate = toBigDecimal(previous.get("correctRate"), currentRate);
        BigDecimal currentScore = toBigDecimal(current.get("userScore"), BigDecimal.ZERO);
        BigDecimal previousScore = toBigDecimal(previous.get("userScore"), currentScore);
        List<Map<String, Object>> recentPoints = points.stream()
                .skip(Math.max(0, points.size() - 10))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subject", ResponseUtils.safe(currentAssessment.getSubject()));
        data.put("currentAssessmentId", currentAssessment.getAssessmentId());
        data.put("points", recentPoints);
        data.put("current", current);
        data.put("previous", previous);
        data.put("deltaCorrectRate", currentRate.subtract(previousRate).setScale(2, RoundingMode.HALF_UP));
        data.put("deltaScore", currentScore.subtract(previousScore).setScale(2, RoundingMode.HALF_UP));
        data.put("trendSuggestion", trendSuggestion(currentRate.subtract(previousRate)));
        return data;
    }

    private Map<String, Object> assessmentTrendPoint(Assessment assessment) {
        Map<String, Object> data = new LinkedHashMap<>();
        BigDecimal rate = correctRate(assessment).setScale(2, RoundingMode.HALF_UP);
        data.put("assessmentId", assessment.getAssessmentId());
        data.put("assessmentType", assessment.getAssessmentType());
        data.put("subject", ResponseUtils.safe(assessment.getSubject()));
        data.put("knowledgeScope", ResponseUtils.safe(assessment.getKnowledgeScope()));
        data.put("difficulty", assessment.getDifficulty());
        data.put("userScore", assessment.getUserScore() == null ? BigDecimal.ZERO : assessment.getUserScore());
        data.put("totalScore", assessment.getTotalScore() == null ? BigDecimal.ZERO : assessment.getTotalScore());
        data.put("correctRate", rate);
        data.put("createTime", ResponseUtils.format(assessment.getCreateTime()));
        data.put("totalUseSeconds", totalQuestionUseSeconds(assessment));
        return data;
    }

    private int totalQuestionUseSeconds(Assessment assessment) {
        if (assessment == null || assessment.getAssessmentId() == null) {
            return 0;
        }
        try {
            return assessmentAnswerService.lambdaQuery()
                    .eq(AssessmentAnswer::getAssessmentId, assessment.getAssessmentId())
                    .eq(AssessmentAnswer::getUserId, assessment.getUserId())
                    .list()
                    .stream()
                    .map(AssessmentAnswer::getQuestionUseSeconds)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String trendSuggestion(BigDecimal deltaRate) {
        if (deltaRate.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "本次成绩较上一次明显提升，可以继续保持当前复盘节奏，并尝试更高难度题目。";
        }
        if (deltaRate.compareTo(BigDecimal.valueOf(-5)) <= 0) {
            return "本次成绩较上一次下降，建议回看报告中的薄弱知识点，先完成错题复盘再进入新测评。";
        }
        return "最近成绩整体稳定，建议结合单题用时找出耗时较高的题型做专项训练。";
    }

    private List<Map<String, Object>> assessmentQuestions(Assessment assessment) {
        int limit = questionLimit(assessment);
        List<QuestionBank> candidates = queryQuestions(assessment, true);
        if (candidates.size() < limit) {
            Set<Long> existingIds = candidates.stream()
                    .map(QuestionBank::getQuestionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<QuestionBank> fallback = queryQuestions(assessment, false).stream()
                    .filter(question -> !existingIds.contains(question.getQuestionId()))
                    .toList();
            candidates = new ArrayList<>(candidates);
            candidates.addAll(fallback);
        }
        return candidates.stream()
                .limit(limit)
                .map(this::questionMap)
                .toList();
    }

    private List<QuestionBank> assessmentQuestionEntities(Assessment assessment) {
        int limit = questionLimit(assessment);
        List<QuestionBank> candidates = queryQuestions(assessment, true);
        if (candidates.size() < limit) {
            Set<Long> existingIds = candidates.stream()
                    .map(QuestionBank::getQuestionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<QuestionBank> fallback = queryQuestions(assessment, false).stream()
                    .filter(question -> !existingIds.contains(question.getQuestionId()))
                    .toList();
            candidates = new ArrayList<>(candidates);
            candidates.addAll(fallback);
        }
        return candidates.stream().limit(limit).toList();
    }

    private List<AssessmentAnswer> buildAnswerDetails(Assessment assessment,
                                                      List<QuestionBank> questions,
                                                      List<Map<String, Object>> answers) {
        Map<Long, Map<String, Object>> answerMap = new LinkedHashMap<>();
        for (Map<String, Object> item : answers) {
            Long questionId = toLong(item.get("questionId"));
            if (questionId != null) {
                answerMap.put(questionId, item);
            }
        }
        BigDecimal totalScore = assessment.getTotalScore() == null || assessment.getTotalScore().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.valueOf(100)
                : assessment.getTotalScore();
        BigDecimal perScore = questions.isEmpty()
                ? BigDecimal.ZERO
                : totalScore.divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.HALF_UP);
        BigDecimal allocated = BigDecimal.ZERO;
        List<AssessmentAnswer> details = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < questions.size(); index++) {
            QuestionBank question = questions.get(index);
            BigDecimal maxScore = index == questions.size() - 1
                    ? totalScore.subtract(allocated).setScale(2, RoundingMode.HALF_UP)
                    : perScore;
            allocated = allocated.add(maxScore);
            Map<String, Object> submitted = answerMap.get(question.getQuestionId());
            String userAnswer = submitted == null || submitted.get("userAnswer") == null
                    ? ""
                    : submitted.get("userAnswer").toString();
            Integer useSeconds = submitted == null
                    ? 0
                    : toInteger(submitted.get("questionUseSeconds") == null
                    ? submitted.get("useSeconds")
                    : submitted.get("questionUseSeconds"));
            details.add(buildAnswerDetail(
                    assessment,
                    question,
                    userAnswer,
                    maxScore,
                    Math.max(0, useSeconds == null ? 0 : useSeconds),
                    now
            ));
        }
        return details;
    }

    private AssessmentAnswer buildAnswerDetail(Assessment assessment,
                                               QuestionBank question,
                                               String userAnswer,
                                               BigDecimal maxScore,
                                               Integer questionUseSeconds,
                                               LocalDateTime now) {
        String safeAnswer = ResponseUtils.safe(userAnswer);
        boolean matched = answerMatches(question.getAnswer(), safeAnswer);
        boolean subjective = Integer.valueOf(QUESTION_TYPE_SUBJECTIVE).equals(question.getQuestionType());
        AssessmentAnswer detail = new AssessmentAnswer();
        detail.setAssessmentId(assessment.getAssessmentId());
        detail.setUserId(assessment.getUserId());
        detail.setQuestionId(question.getQuestionId());
        detail.setUserAnswer(safeAnswer);
        detail.setCorrectAnswer(ResponseUtils.safe(question.getAnswer()));
        detail.setMaxScore(maxScore);
        detail.setScoringPointsSnapshot(scoringPointsText(question));
        detail.setQuestionUseSeconds(questionUseSeconds == null ? 0 : questionUseSeconds);
        detail.setCreateTime(now);
        detail.setUpdateTime(now);

        if (subjective && !matched) {
            applySubjectiveScore(assessment, question, detail, safeAnswer, maxScore);
            return detail;
        }

        detail.setScore(matched ? maxScore : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        detail.setIsCorrect(matched ? 1 : 0);
        if (subjective && matched) {
            detail.setAiScore(maxScore);
            detail.setAiConfidence(BigDecimal.valueOf(100));
        }
        detail.setScoreStatus(SCORE_STATUS_AUTO);
        detail.setReviewStatus(REVIEW_STATUS_NONE);
        detail.setReviewComment("");
        detail.setScoringDetail(subjective && matched ? "主观题参考答案完全匹配，自动满分" : matched ? "自动批改正确" : "自动批改错误");
        return detail;
    }

    private void replaceAnswerDetails(Assessment assessment, List<AssessmentAnswer> details) {
        assessmentAnswerService.remove(new LambdaQueryWrapper<AssessmentAnswer>()
                .eq(AssessmentAnswer::getAssessmentId, assessment.getAssessmentId())
                .eq(AssessmentAnswer::getUserId, assessment.getUserId()));
        if (!details.isEmpty()) {
            assessmentAnswerService.saveBatch(details);
        }
    }

    private void applySubjectiveScore(Assessment assessment,
                                      QuestionBank question,
                                      AssessmentAnswer detail,
                                      String safeAnswer,
                                      BigDecimal maxScore) {
        detail.setReviewStatus(REVIEW_STATUS_NONE);
        detail.setReviewComment("");

        if (!StringUtils.hasText(safeAnswer)) {
            detail.setScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            detail.setAiScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            detail.setAiConfidence(BigDecimal.valueOf(100));
            detail.setIsCorrect(0);
            detail.setScoreStatus(SCORE_STATUS_AUTO);
            detail.setScoringDetail("主观题未作答，自动 0 分");
            return;
        }

        List<String> scoringPoints = splitScoringPoints(question);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("questionText", ResponseUtils.safe(question.getQuestionText()));
        request.put("referenceAnswer", ResponseUtils.safe(question.getAnswer()));
        request.put("studentAnswer", safeAnswer);
        request.put("scoringPoints", scoringPoints);
        request.put("maxScore", maxScore);
        request.put("subject", ResponseUtils.safe(assessment.getSubject()));
        request.put("knowledgePoint", ResponseUtils.safe(question.getKnowledgePoint()));

        Map<String, Object> aiResult = aiService.subjectiveScore(request);
        boolean aiAvailable = Boolean.TRUE.equals(aiResult.get("available"));
        if (!aiAvailable) {
            aiResult = localSubjectiveScore(question, safeAnswer, maxScore, aiResult.get("message"));
        }

        BigDecimal score = clampScore(toBigDecimal(aiResult.get("score"), maxScore.multiply(BigDecimal.valueOf(0.5D))), maxScore);
        BigDecimal confidence = clampPercent(toBigDecimal(aiResult.get("confidence"), BigDecimal.ZERO));
        boolean reliable = aiAvailable && confidence.compareTo(AI_AUTO_SCORE_CONFIDENCE) >= 0;

        detail.setScore(score);
        detail.setAiScore(score);
        detail.setAiConfidence(confidence);
        detail.setIsCorrect(score.compareTo(maxScore.multiply(BigDecimal.valueOf(0.6D))) >= 0 ? 1 : 0);
        detail.setScoreStatus(reliable ? SCORE_STATUS_AUTO : SCORE_STATUS_PENDING_MANUAL);
        detail.setScoringDetail(subjectiveScoreDetail(aiResult, score, maxScore, confidence, reliable));
    }

    private Map<String, Object> localSubjectiveScore(QuestionBank question,
                                                     String safeAnswer,
                                                     BigDecimal maxScore,
                                                     Object unavailableReason) {
        List<String> scoringPoints = splitScoringPoints(question);
        List<String> matchedPoints = new ArrayList<>();
        List<String> missingPoints = new ArrayList<>();
        BigDecimal ratio;
        if (!scoringPoints.isEmpty()) {
            for (String point : scoringPoints) {
                if (textOverlapRatio(point, safeAnswer).compareTo(BigDecimal.valueOf(0.42D)) >= 0) {
                    matchedPoints.add(point);
                } else {
                    missingPoints.add(point);
                }
            }
            ratio = BigDecimal.valueOf(matchedPoints.size())
                    .divide(BigDecimal.valueOf(scoringPoints.size()), 4, RoundingMode.HALF_UP);
        } else {
            ratio = textOverlapRatio(question.getAnswer(), safeAnswer);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("score", maxScore.multiply(ratio).setScale(2, RoundingMode.HALF_UP));
        data.put("confidence", BigDecimal.valueOf(45));
        data.put("matchedPoints", matchedPoints);
        data.put("missingPoints", missingPoints);
        data.put("scoringMode", "java_fallback");
        data.put("comment", "AI 服务不可用，已按评分要点相似度给出临时参考分；原因：" + ResponseUtils.safe(unavailableReason == null ? null : unavailableReason.toString()));
        return data;
    }

    private List<Map<String, Object>> answerDetails(Assessment assessment) {
        try {
            List<AssessmentAnswer> details = assessmentAnswerService.lambdaQuery()
                    .eq(AssessmentAnswer::getAssessmentId, assessment.getAssessmentId())
                    .eq(AssessmentAnswer::getUserId, assessment.getUserId())
                    .orderByAsc(AssessmentAnswer::getAnswerId)
                    .list();
            if (details.isEmpty()) {
                return List.of();
            }
            Map<Long, QuestionBank> questionMap = loadQuestionMap(details);
            return details.stream()
                    .map(answer -> answerMap(answer, questionMap.get(answer.getQuestionId())))
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Map<Long, QuestionBank> loadQuestionMap(List<AssessmentAnswer> details) {
        List<Long> ids = details.stream()
                .map(AssessmentAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionBankService.listByIds(ids).stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, question -> question, (left, right) -> left));
    }

    private Map<String, Object> answerMap(AssessmentAnswer answer, QuestionBank question) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answerId", answer.getAnswerId());
        data.put("assessmentId", answer.getAssessmentId());
        data.put("questionId", answer.getQuestionId());
        data.put("subject", question == null ? "" : ResponseUtils.safe(question.getSubject()));
        data.put("knowledgePoint", question == null ? "" : ResponseUtils.safe(question.getKnowledgePoint()));
        data.put("questionType", question == null ? null : question.getQuestionType());
        data.put("questionTypeName", questionTypeName(question == null ? null : question.getQuestionType()));
        data.put("questionText", question == null ? "" : ResponseUtils.safe(question.getQuestionText()));
        data.put("userAnswer", ResponseUtils.safe(answer.getUserAnswer()));
        data.put("correctAnswer", ResponseUtils.safe(answer.getCorrectAnswer()));
        data.put("isCorrect", answer.getIsCorrect());
        data.put("score", answer.getScore());
        data.put("maxScore", answer.getMaxScore());
        data.put("scoreStatus", answer.getScoreStatus());
        data.put("scoreStatusLabel", scoreStatusLabel(answer.getScoreStatus()));
        data.put("reviewStatus", answer.getReviewStatus());
        data.put("reviewComment", ResponseUtils.safe(answer.getReviewComment()));
        data.put("scoringDetail", ResponseUtils.safe(answer.getScoringDetail()));
        data.put("aiScore", answer.getAiScore());
        data.put("aiConfidence", answer.getAiConfidence());
        data.put("scoringPointsSnapshot", ResponseUtils.safe(answer.getScoringPointsSnapshot()));
        data.put("scoringPoints", splitScoringPointText(StringUtils.hasText(answer.getScoringPointsSnapshot())
                ? answer.getScoringPointsSnapshot()
                : question == null ? "" : question.getScoringPoints()));
        data.put("questionUseSeconds", answer.getQuestionUseSeconds() == null ? 0 : answer.getQuestionUseSeconds());
        return data;
    }

    private List<Map<String, Object>> wrongAnswerCandidates(List<AssessmentAnswer> details) {
        return details.stream()
                .filter(answer -> answer.getQuestionId() != null)
                .filter(answer -> !Integer.valueOf(SCORE_STATUS_PENDING_MANUAL).equals(answer.getScoreStatus()))
                .filter(answer -> Integer.valueOf(0).equals(answer.getIsCorrect()))
                .map(answer -> Map.<String, Object>of(
                        "questionId", answer.getQuestionId(),
                        "userAnswer", ResponseUtils.safe(answer.getUserAnswer())
                ))
                .toList();
    }

    private long pendingManualCount(List<AssessmentAnswer> details) {
        return details.stream()
                .filter(answer -> Integer.valueOf(SCORE_STATUS_PENDING_MANUAL).equals(answer.getScoreStatus()))
                .count();
    }

    private BigDecimal sumScore(List<AssessmentAnswer> details) {
        return details.stream()
                .map(AssessmentAnswer::getScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumMaxScore(List<AssessmentAnswer> details) {
        return details.stream()
                .map(AssessmentAnswer::getMaxScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void recalculateAssessmentScore(Assessment assessment) {
        List<AssessmentAnswer> details = assessmentAnswerService.lambdaQuery()
                .eq(AssessmentAnswer::getAssessmentId, assessment.getAssessmentId())
                .eq(AssessmentAnswer::getUserId, assessment.getUserId())
                .list();
        assessment.setUserScore(sumScore(details));
        assessment.setTotalScore(sumMaxScore(details));
        assessment.setEndTime(LocalDateTime.now());
        assessmentService.updateById(assessment);
    }

    private List<QuestionBank> queryQuestions(Assessment assessment, boolean strictDifficulty) {
        List<String> scopes = splitScopes(assessment.getKnowledgeScope());
        List<QuestionBank> questions = questionBankService.lambdaQuery()
                .eq(StringUtils.hasText(assessment.getSubject()), QuestionBank::getSubject, assessment.getSubject())
                .eq(strictDifficulty && assessment.getDifficulty() != null, QuestionBank::getDifficulty, assessment.getDifficulty())
                .list();
        return questions.stream()
                .filter(question -> scopes.isEmpty() || scopes.stream().anyMatch(scope -> knowledgeMatches(question, scope)))
                .sorted(Comparator
                        .comparingInt((QuestionBank question) -> difficultyGap(question.getDifficulty(), assessment.getDifficulty()))
                        .thenComparing(question -> ResponseUtils.safe(question.getKnowledgePoint()))
                        .thenComparing(QuestionBank::getQuestionId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private boolean knowledgeMatches(QuestionBank question, String scope) {
        String point = ResponseUtils.safe(question.getKnowledgePoint());
        return !StringUtils.hasText(scope) || point.contains(scope) || scope.contains(point);
    }

    private Map<String, Object> questionMap(QuestionBank question) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", question.getQuestionId());
        data.put("subject", ResponseUtils.safe(question.getSubject()));
        data.put("knowledgePoint", ResponseUtils.safe(question.getKnowledgePoint()));
        data.put("difficulty", question.getDifficulty());
        data.put("questionType", question.getQuestionType());
        data.put("questionText", ResponseUtils.safe(question.getQuestionText()));
        data.put("options", !StringUtils.hasText(question.getOptions())
                ? List.of()
                : Arrays.asList(question.getOptions().split("\\|")));
        data.put("answer", ResponseUtils.safe(question.getAnswer()));
        data.put("analysis", ResponseUtils.safe(question.getAnalysis()));
        data.put("scoringPoints", splitScoringPointText(question.getScoringPoints()));
        return data;
    }

    private int questionLimit(Assessment assessment) {
        Integer type = assessment.getAssessmentType();
        if (type != null && type == 3) {
            return 10;
        }
        if (type != null && type == 2) {
            return 8;
        }
        return 5;
    }

    private BigDecimal readScore(Map<String, Object> request, String key, BigDecimal defaultValue) {
        Object value = request.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal clampScore(BigDecimal value, BigDecimal maxScore) {
        BigDecimal score = value == null ? BigDecimal.ZERO : value;
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (maxScore != null && score.compareTo(maxScore) > 0) {
            return maxScore.setScale(2, RoundingMode.HALF_UP);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clampPercent(BigDecimal value) {
        BigDecimal percent = value == null ? BigDecimal.ZERO : value;
        if (percent.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        return percent.setScale(2, RoundingMode.HALF_UP);
    }

    private String subjectiveScoreDetail(Map<String, Object> aiResult,
                                         BigDecimal score,
                                         BigDecimal maxScore,
                                         BigDecimal confidence,
                                         boolean reliable) {
        String mode = ResponseUtils.safe(aiResult.get("scoringMode") == null ? null : aiResult.get("scoringMode").toString());
        String comment = ResponseUtils.safe(aiResult.get("comment") == null ? null : aiResult.get("comment").toString());
        String matched = listSummary(aiResult.get("matchedPoints"));
        String missing = listSummary(aiResult.get("missingPoints"));
        String detail = "AI语义评分：" + score + "/" + maxScore.setScale(2, RoundingMode.HALF_UP)
                + "，置信度 " + confidence + "%，模式 " + (StringUtils.hasText(mode) ? mode : "semantic")
                + (StringUtils.hasText(matched) ? "，命中：" + matched : "")
                + (StringUtils.hasText(missing) ? "，缺失：" + missing : "")
                + (StringUtils.hasText(comment) ? "；" + comment : "");
        if (!reliable) {
            detail += "（建议人工复核）";
        }
        return limitText(detail, 480);
    }

    private String listSummary(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        return list.stream()
                .map(item -> ResponseUtils.safe(item == null ? null : item.toString()))
                .filter(StringUtils::hasText)
                .limit(3)
                .collect(Collectors.joining("、"));
    }

    private String scoringPointsText(QuestionBank question) {
        return String.join("\n", splitScoringPoints(question));
    }

    private List<String> splitScoringPoints(QuestionBank question) {
        if (question == null) {
            return List.of();
        }
        String raw = ResponseUtils.safe(question.getScoringPoints());
        if (!StringUtils.hasText(raw)) {
            raw = ResponseUtils.safe(question.getAnalysis());
        }
        if (!StringUtils.hasText(raw)) {
            raw = ResponseUtils.safe(question.getAnswer());
        }
        return splitScoringPointText(raw);
    }

    private List<String> splitScoringPointText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\n;；。！？!?]+"))
                .map(item -> item.replaceFirst("^[-*\\d.、\\s]+", "").trim())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(8)
                .toList();
    }

    private BigDecimal textOverlapRatio(String expected, String actual) {
        Set<String> expectedTokens = semanticTokens(expected);
        Set<String> actualTokens = semanticTokens(actual);
        if (expectedTokens.isEmpty() || actualTokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long hits = expectedTokens.stream().filter(actualTokens::contains).count();
        return BigDecimal.valueOf(hits)
                .divide(BigDecimal.valueOf(expectedTokens.size()), 4, RoundingMode.HALF_UP);
    }

    private Set<String> semanticTokens(String value) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(value)) {
            return tokens;
        }
        for (String token : value.toLowerCase().split("[^a-z0-9_]+")) {
            if (StringUtils.hasText(token)) {
                tokens.add(token);
            }
        }
        Set<Character> stopChars = Set.of('的', '了', '是', '和', '与', '中', '可', '以', '则');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '\u4e00' && ch <= '\u9fff' && !stopChars.contains(ch)) {
                tokens.add(String.valueOf(ch));
            }
        }
        return tokens;
    }

    private String limitText(String value, int maxLength) {
        String safe = ResponseUtils.safe(value);
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private BigDecimal correctRate(Assessment assessment) {
        if (assessment.getUserScore() == null || assessment.getTotalScore() == null
                || assessment.getTotalScore().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return assessment.getUserScore()
                .divide(assessment.getTotalScore(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private String abilityAnalysis(BigDecimal correctRate) {
        if (correctRate.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "本次测评正确率较高，当前知识点掌握稳定，可以进入更高难度练习。";
        }
        if (correctRate.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "本次测评基本达标，但仍存在局部薄弱点，需要结合错题本复盘。";
        }
        return "本次测评暴露出明显知识漏洞，建议先回到讲义和微课补基础，再做同类题巩固。";
    }

    private String improveSuggestion(BigDecimal correctRate, String knowledgeScope) {
        String scope = StringUtils.hasText(knowledgeScope) ? knowledgeScope : "本次测评知识点";
        if (correctRate.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "围绕" + scope + "完成提升题，保持每日轻量复盘。";
        }
        if (correctRate.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "优先复盘错题，针对" + scope + "补做3-5道同类题。";
        }
        return "先学习" + scope + "对应资源，再重新发起一次专项测评。";
    }

    private BigDecimal rankPercent(BigDecimal correctRate) {
        BigDecimal percent = correctRate.multiply(BigDecimal.valueOf(0.8)).add(BigDecimal.valueOf(10));
        if (percent.compareTo(BigDecimal.valueOf(99)) > 0) {
            return BigDecimal.valueOf(99);
        }
        if (percent.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return percent.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> castAnswers(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> answers = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> answer = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                answer.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            answers.add(answer);
        }
        return answers;
    }

    private BigDecimal scoreByAnswers(int total, int wrong) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        int correct = Math.max(0, total - wrong);
        return BigDecimal.valueOf(correct)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }

    private String questionTypeName(Integer type) {
        if (type == null) {
            return "题目";
        }
        return switch (type) {
            case 1 -> "单选题";
            case 2 -> "多选题";
            case 3 -> "填空题";
            case 4 -> "解答题";
            default -> "题目";
        };
    }

    private String scoreStatusLabel(Integer status) {
        if (status == null) {
            return "未评分";
        }
        return switch (status) {
            case SCORE_STATUS_AUTO -> "自动评分";
            case SCORE_STATUS_PENDING_MANUAL -> "待人工复核";
            case SCORE_STATUS_REVIEWED -> "人工复核完成";
            default -> "未评分";
        };
    }

    private boolean answerMatches(String answer, String userAnswer) {
        String expected = normalize(answer);
        String actual = normalize(userAnswer);
        if (expected.equals(actual)) {
            return true;
        }
        if (expected.length() == 1 && actual.length() > 1) {
            return actual.startsWith(expected + ".")
                    || actual.startsWith(expected + "．")
                    || actual.startsWith(expected + "、")
                    || actual.startsWith(expected + ":")
                    || actual.startsWith(expected + "：");
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").trim().toLowerCase();
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

    private List<String> splitScopes(String scope) {
        if (!StringUtils.hasText(scope)) {
            return List.of();
        }
        return Arrays.stream(scope.split("[,，、;；\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private int difficultyGap(Integer questionDifficulty, Integer targetDifficulty) {
        int left = questionDifficulty == null ? 2 : questionDifficulty;
        int right = targetDifficulty == null ? 2 : targetDifficulty;
        return Math.abs(left - right);
    }
}
