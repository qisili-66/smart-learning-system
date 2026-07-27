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
import org.springframework.transaction.annotation.Transactional;
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
    private static final int RECENT_ASSESSMENT_LOOKBACK = 5;
    private static final String GENERATED_ASSESSMENT_MARKER = "AI测评#";
    private static final BigDecimal AI_AUTO_SCORE_CONFIDENCE = BigDecimal.valueOf(55);
    private static final Set<String> CORE_EXAM_SUBJECTS = Set.of("语文", "数学", "英语");

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
    @Transactional
    public Result<Assessment> create(@RequestBody Assessment assessment) {
        assessment.setUserId(SecurityUtils.currentUserId());
        String gradeLevel = normalizeGradeLevel(assessment.getGradeLevel());
        String knowledgeScope = ResponseUtils.safe(assessment.getKnowledgeScope());
        assessment.setKnowledgeScope(displayScope(gradeLevel, knowledgeScope));
        assessment.setTotalScore(defaultTotalScore(assessment.getSubject()));
        assessment.setAssessmentStatus(1);
        assessment.setStartTime(LocalDateTime.now());
        assessment.setCreateTime(LocalDateTime.now());
        assessmentService.save(assessment);
        generateAssessmentQuestions(assessment, gradeLevel, knowledgeScope);
        assessment.setGradeLevel(gradeLevel);
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
        List<SelectedQuestion> questions = assessmentSelectedQuestions(assessment);
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
    @Transactional
    public Result<Map<String, Object>> delete(@PathVariable Long assessmentId) {
        Assessment assessment = getOwnedAssessment(assessmentId);
        assessmentAnswerService.remove(new LambdaQueryWrapper<AssessmentAnswer>()
                .eq(AssessmentAnswer::getAssessmentId, assessment.getAssessmentId())
                .eq(AssessmentAnswer::getUserId, assessment.getUserId()));
        assessmentService.removeById(assessment.getAssessmentId());
        removeGeneratedAssessmentQuestions(List.of(assessment));
        userProfileService.refreshAfterLearningEvent(assessment.getUserId());
        return Result.success(Map.of(
                "deleted", 1,
                "assessmentId", assessmentId
        ));
    }

    @DeleteMapping
    @Transactional
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
            removeGeneratedAssessmentQuestions(assessments);
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
        List<SelectedQuestion> selected = assessmentSelectedQuestions(assessment);
        List<BigDecimal> scores = maxScoresForSelectedQuestions(assessment, selected);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            SelectedQuestion selectedQuestion = selected.get(index);
            Map<String, Object> item = questionMap(selectedQuestion.question());
            item.put("maxScore", scores.get(index));
            PaperSectionSpec section = selectedQuestion.section();
            if (section != null) {
                item.put("paperSectionTitle", section.title());
                item.put("paperSectionNote", section.note());
            }
            items.add(item);
        }
        return items;
    }

    private void generateAssessmentQuestions(Assessment assessment, String gradeLevel, String knowledgeScope) {
        Map<String, Object> request = new LinkedHashMap<>();
        List<PaperSectionSpec> sections = generatedSectionSpecs(assessment);
        request.put("subject", ResponseUtils.safe(assessment.getSubject()));
        request.put("gradeLevel", gradeLevel);
        request.put("knowledgeScope", StringUtils.hasText(knowledgeScope) ? knowledgeScope : "综合知识");
        request.put("difficulty", assessment.getDifficulty() == null ? 2 : assessment.getDifficulty());
        request.put("assessmentType", assessment.getAssessmentType() == null ? 2 : assessment.getAssessmentType());
        request.put("totalScore", assessment.getTotalScore() == null ? defaultTotalScore(assessment.getSubject()) : assessment.getTotalScore());
        request.put("questionCount", sections.stream().mapToInt(PaperSectionSpec::count).sum());
        request.put("sections", sections.stream().map(section -> Map.<String, Object>of(
                "title", section.title(),
                "note", section.note(),
                "questionType", section.questionType(),
                "count", section.count()
        )).toList());

        List<Map<String, Object>> generated = generatedQuestionMaps(aiService.generateAssessmentPaper(request));
        if (generated.isEmpty()) {
            generated = javaGeneratedQuestionMaps(request, sections);
        }
        List<QuestionBank> questions = generated.stream()
                .map(item -> generatedQuestion(assessment, item))
                .filter(question -> StringUtils.hasText(question.getQuestionText()) && StringUtils.hasText(question.getAnswer()))
                .toList();
        if (questions.isEmpty()) {
            throw new BusinessException(Constants.CODE_ERROR, "AI未能生成可用测评题目，请稍后重试");
        }
        questionBankService.saveBatch(questions);
    }

    private List<Map<String, Object>> generatedQuestionMaps(Map<String, Object> aiResult) {
        if (aiResult == null || !(aiResult.get("questions") instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> item.put(String.valueOf(key), value));
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> javaGeneratedQuestionMaps(Map<String, Object> request, List<PaperSectionSpec> sections) {
        String subject = ResponseUtils.safe(request.get("subject") == null ? null : request.get("subject").toString());
        String grade = ResponseUtils.safe(request.get("gradeLevel") == null ? null : request.get("gradeLevel").toString());
        String scope = ResponseUtils.safe(request.get("knowledgeScope") == null ? null : request.get("knowledgeScope").toString());
        int difficulty = toInteger(request.get("difficulty")) == null ? 2 : toInteger(request.get("difficulty"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (PaperSectionSpec section : sections) {
            for (int index = 0; index < section.count(); index++) {
                int number = items.size() + 1;
                items.add(javaGeneratedQuestion(subject, grade, scope, difficulty, section, number));
            }
        }
        return items;
    }

    private Map<String, Object> javaGeneratedQuestion(String subject, String grade, String scope, int difficulty, PaperSectionSpec section, int number) {
        String point = StringUtils.hasText(scope) ? scope : "综合知识";
        String stem = grade + subject + point + "第" + number + "题：";
        if (Integer.valueOf(1).equals(section.questionType()) || Integer.valueOf(2).equals(section.questionType())) {
            return Map.of(
                    "sectionTitle", section.title(),
                    "knowledgePoint", point,
                    "difficulty", difficulty,
                    "questionType", section.questionType(),
                    "questionText", stem + "下列说法最符合题意的是（ ）。",
                    "options", List.of("A. 能正确体现核心概念", "B. 与题干条件相反", "C. 忽略了限制条件", "D. 与本知识点无关"),
                    "answer", "A",
                    "analysis", "A项符合题干中的核心概念和限制条件，其余选项存在概念或条件错误。 ",
                    "scoringPoints", List.of("识别核心概念", "排除干扰项")
            );
        }
        return Map.of(
                "sectionTitle", section.title(),
                "knowledgePoint", point,
                "difficulty", difficulty,
                "questionType", section.questionType(),
                "questionText", stem + "请结合概念、步骤和结论进行完整解答。 ",
                "options", List.of(),
                "answer", "围绕" + point + "说明概念、列出关键步骤，并得到合理结论。 ",
                "analysis", "主观题按概念准确性、步骤完整性和结论合理性评分。 ",
                "scoringPoints", List.of("概念准确", "步骤清晰", "结论合理")
        );
    }

    private QuestionBank generatedQuestion(Assessment assessment, Map<String, Object> item) {
        String sectionTitle = ResponseUtils.safe(item.get("sectionTitle") == null ? null : item.get("sectionTitle").toString());
        String knowledgePoint = ResponseUtils.safe(item.get("knowledgePoint") == null ? null : item.get("knowledgePoint").toString());
        QuestionBank question = new QuestionBank();
        question.setSubject(ResponseUtils.safe(assessment.getSubject()));
        question.setKnowledgePoint(generatedKnowledgePoint(assessment, sectionTitle, knowledgePoint));
        Integer itemDifficulty = toInteger(item.get("difficulty"));
        Integer assessmentDifficulty = assessment.getDifficulty();
        question.setDifficulty(itemDifficulty == null ? (assessmentDifficulty == null ? 2 : assessmentDifficulty) : itemDifficulty);
        question.setQuestionType(toInteger(item.get("questionType")) == null ? 1 : toInteger(item.get("questionType")));
        question.setQuestionText(ResponseUtils.safe(item.get("questionText") == null ? null : item.get("questionText").toString()));
        question.setOptions(String.join("|", stringList(item.get("options"))));
        question.setAnswer(ResponseUtils.safe(item.get("answer") == null ? null : item.get("answer").toString()));
        question.setAnalysis(ResponseUtils.safe(item.get("analysis") == null ? null : item.get("analysis").toString()));
        question.setScoringPoints(String.join("\n", stringList(item.get("scoringPoints"))));
        question.setCreateTime(LocalDateTime.now());
        question.setUpdateTime(LocalDateTime.now());
        return question;
    }

    private List<QuestionBank> generatedQuestions(Assessment assessment) {
        if (assessment == null || assessment.getAssessmentId() == null) {
            return List.of();
        }
        try {
            return questionBankService.lambdaQuery()
                    .eq(StringUtils.hasText(assessment.getSubject()), QuestionBank::getSubject, assessment.getSubject())
                    .like(QuestionBank::getKnowledgePoint, generatedMarker(assessment))
                    .orderByAsc(QuestionBank::getQuestionId)
                    .list();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private void removeGeneratedAssessmentQuestions(List<Assessment> assessments) {
        List<Long> generatedQuestionIds = assessments.stream()
                .filter(Objects::nonNull)
                .flatMap(assessment -> generatedQuestions(assessment).stream())
                .map(QuestionBank::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!generatedQuestionIds.isEmpty()) {
            questionBankService.removeByIds(generatedQuestionIds);
        }
    }

    private PaperSectionSpec generatedSection(QuestionBank question, Assessment assessment) {
        String title = generatedSectionTitle(question);
        if (StringUtils.hasText(title)) {
            return new PaperSectionSpec(title, "AI实时生成题，按本次测评自动组卷。", question.getQuestionType(), 1, BigDecimal.ZERO);
        }
        return paperSectionSpecs(assessment.getSubject()).stream()
                .filter(section -> Objects.equals(section.questionType(), question.getQuestionType()))
                .findFirst()
                .orElse(null);
    }

    private String generatedKnowledgePoint(Assessment assessment, String sectionTitle, String knowledgePoint) {
        return generatedMarker(assessment) + "|大题=" + ResponseUtils.safe(sectionTitle) + "|知识点=" + ResponseUtils.safe(knowledgePoint);
    }

    private String generatedMarker(Assessment assessment) {
        return GENERATED_ASSESSMENT_MARKER + assessment.getAssessmentId();
    }

    private String generatedSectionTitle(QuestionBank question) {
        String point = ResponseUtils.safe(question == null ? null : question.getKnowledgePoint());
        return extractGeneratedField(point, "大题");
    }

    private String cleanKnowledgePoint(String value) {
        String safe = ResponseUtils.safe(value);
        if (!safe.startsWith(GENERATED_ASSESSMENT_MARKER)) {
            return safe;
        }
        String point = extractGeneratedField(safe, "知识点");
        return StringUtils.hasText(point) ? point : "AI生成题";
    }

    private String extractGeneratedField(String value, String key) {
        String safe = ResponseUtils.safe(value);
        String token = "|" + key + "=";
        int start = safe.indexOf(token);
        if (start < 0) {
            return "";
        }
        start += token.length();
        int end = safe.indexOf("|", start);
        return (end < 0 ? safe.substring(start) : safe.substring(start, end)).trim();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> ResponseUtils.safe(item == null ? null : item.toString()))
                .filter(StringUtils::hasText)
                .limit(8)
                .toList();
    }

    private boolean isGeneratedQuestion(QuestionBank question) {
        return question != null && ResponseUtils.safe(question.getKnowledgePoint()).startsWith(GENERATED_ASSESSMENT_MARKER);
    }

    private List<PaperSectionSpec> generatedSectionSpecs(Assessment assessment) {
        int target = generatedQuestionTarget(assessment);
        List<PaperSectionSpec> base = paperSectionSpecs(assessment.getSubject());
        if (base.isEmpty()) {
            int choiceCount = Math.max(1, Math.min(5, target / 2));
            int subjectiveCount = Math.max(1, target - choiceCount);
            return List.of(
                    new PaperSectionSpec("一、选择题", "AI按年级和知识范围实时生成。", 1, choiceCount, BigDecimal.ZERO),
                    new PaperSectionSpec("二、综合题", "AI按年级和知识范围实时生成。", 4, subjectiveCount, BigDecimal.ZERO)
            );
        }
        int[] counts = new int[base.size()];
        int remaining = target;
        for (int index = 0; index < base.size() && remaining > 0; index++) {
            counts[index] = 1;
            remaining--;
        }
        while (remaining > 0) {
            boolean changed = false;
            for (int index = 0; index < base.size() && remaining > 0; index++) {
                if (counts[index] < base.get(index).count()) {
                    counts[index]++;
                    remaining--;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        List<PaperSectionSpec> sections = new ArrayList<>();
        for (int index = 0; index < base.size(); index++) {
            if (counts[index] <= 0) {
                continue;
            }
            PaperSectionSpec section = base.get(index);
            sections.add(new PaperSectionSpec(section.title(), section.note(), section.questionType(), counts[index], BigDecimal.ZERO));
        }
        return sections;
    }

    private int generatedQuestionTarget(Assessment assessment) {
        Integer type = assessment.getAssessmentType();
        if (type != null && type == 3) {
            return 18;
        }
        if (type != null && type == 1) {
            return 8;
        }
        return 12;
    }

    private String normalizeGradeLevel(String gradeLevel) {
        String safe = ResponseUtils.safe(gradeLevel);
        return StringUtils.hasText(safe) ? safe : "九年级";
    }

    private String displayScope(String gradeLevel, String knowledgeScope) {
        String grade = normalizeGradeLevel(gradeLevel);
        String scope = ResponseUtils.safe(knowledgeScope);
        return StringUtils.hasText(scope) ? grade + " · " + scope : grade + " · 综合知识";
    }

    private List<SelectedQuestion> assessmentSelectedQuestions(Assessment assessment) {
        List<QuestionBank> generated = generatedQuestions(assessment);
        if (!generated.isEmpty()) {
            return generated.stream()
                    .map(question -> new SelectedQuestion(question, generatedSection(question, assessment)))
                    .toList();
        }
        int limit = questionLimit(assessment);
        List<QuestionBank> candidates = queryQuestions(assessment, true);
        boolean standardPaper = !paperSectionSpecs(assessment.getSubject()).isEmpty();
        if (standardPaper) {
            candidates = mergeQuestions(candidates, queryQuestions(assessment, false, false));
        } else if (candidates.size() < limit) {
            candidates = mergeQuestions(candidates, queryQuestions(assessment, false));
        }
        return selectQuestions(assessment, candidates, limit);
    }

    private List<SelectedQuestion> selectQuestions(Assessment assessment, List<QuestionBank> candidates, int limit) {
        List<QuestionBank> unique = uniqueQuestions(candidates);
        Set<Long> recentIds = recentQuestionIds(assessment);
        List<QuestionBank> fresh = unique.stream()
                .filter(question -> !recentIds.contains(question.getQuestionId()))
                .toList();
        boolean standardPaper = !paperSectionSpecs(assessment.getSubject()).isEmpty();
        List<QuestionBank> pool = standardPaper
                ? (hasSectionCoverage(fresh, assessment) ? fresh : unique)
                : (fresh.size() >= limit ? fresh : unique);
        if (paperSectionSpecs(assessment.getSubject()).isEmpty()) {
            return stableRotatedQuestions(pool, assessment, limit).stream()
                    .limit(limit)
                    .map(question -> new SelectedQuestion(question, null))
                    .toList();
        }
        return selectExamPaperQuestions(pool, assessment, limit);
    }

    private List<SelectedQuestion> selectExamPaperQuestions(List<QuestionBank> questions, Assessment assessment, int limit) {
        List<SelectedQuestion> selected = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();
        for (PaperSectionSpec section : paperSectionSpecs(assessment.getSubject())) {
            List<QuestionBank> typed = questions.stream()
                    .filter(question -> Objects.equals(question.getQuestionType(), section.questionType()))
                    .toList();
            stableRotatedQuestions(typed, assessment, section.count()).stream()
                    .filter(question -> selectedKeys.add(questionKey(question)))
                    .limit(section.count())
                    .map(question -> new SelectedQuestion(question, section))
                    .forEach(selected::add);
        }
        return selected.stream().limit(limit).toList();
    }

    private boolean hasSectionCoverage(List<QuestionBank> questions, Assessment assessment) {
        List<PaperSectionSpec> sections = paperSectionSpecs(assessment.getSubject());
        if (sections.isEmpty()) {
            return true;
        }
        for (PaperSectionSpec section : sections) {
            long count = questions.stream()
                    .filter(question -> Objects.equals(question.getQuestionType(), section.questionType()))
                    .map(this::questionKey)
                    .distinct()
                    .count();
            if (count < section.count()) {
                return false;
            }
        }
        return true;
    }

    private List<PaperSectionSpec> paperSectionSpecs(String subject) {
        return switch (ResponseUtils.safe(subject)) {
            case "语文" -> List.of(
                    new PaperSectionSpec("一、积累与运用", "24 分：字音字形、古诗文默写、病句、词语运用、综合性学习、名著阅读；附加名著题计入总分，上限 120 分。", 1, 8, BigDecimal.valueOf(3)),
                    new PaperSectionSpec("二、阅读", "46 分：古诗词阅读、课内外文言文对比阅读、实用类文本、文学类文本。", 4, 4, BigDecimal.valueOf(11.5)),
                    new PaperSectionSpec("三、作文", "50 分：材料作文，不少于 500 字，诗歌除外。", 4, 1, BigDecimal.valueOf(50))
            );
            case "数学" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "10 小题，每题 3 分，共 30 分。", 1, 10, BigDecimal.valueOf(3)),
                    new PaperSectionSpec("二、填空题", "5 小题，每题 3 分，共 15 分。", 3, 5, BigDecimal.valueOf(3)),
                    new PaperSectionSpec("三、基础解答题", "3 题，每题 8 分；计算、方程不等式、几何证明。", 4, 3, BigDecimal.valueOf(8)),
                    new PaperSectionSpec("四、中档解答题", "3 题，每题 9 分；函数、统计概率、综合探究。", 4, 3, BigDecimal.valueOf(9)),
                    new PaperSectionSpec("五、压轴大题", "2 题，每题 12 分；综合探究与压轴题。", 4, 2, BigDecimal.valueOf(12))
            );
            case "英语" -> List.of(
                    new PaperSectionSpec("一、听说考试", "30 分，机考：模仿朗读、信息获取、信息转述及询问。", 4, 4, BigDecimal.valueOf(7.5)),
                    new PaperSectionSpec("二、语法选择", "15 题，15 分。", 1, 15, BigDecimal.ONE),
                    new PaperSectionSpec("三、完形填空", "10 题，10 分。", 1, 10, BigDecimal.ONE),
                    new PaperSectionSpec("四、阅读理解", "15 题，30 分。", 1, 15, BigDecimal.valueOf(2)),
                    new PaperSectionSpec("五、阅读填空", "五选五，5 题，5 分。", 3, 5, BigDecimal.ONE),
                    new PaperSectionSpec("六、读写综合", "语篇填词、完成句子、书面表达；含 15 分作文。", 4, 3, BigDecimal.valueOf(5))
            );
            case "道德与法治" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "20 小题，每题 2 分，共 40 分。", 1, 20, BigDecimal.valueOf(2)),
                    new PaperSectionSpec("二、非选择题", "3 大题，共 60 分：图表分析、情境材料、观点评析、实践探究。", 4, 3, BigDecimal.valueOf(20))
            );
            case "历史" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "30 小题，每题 2 分，共 60 分。", 1, 30, BigDecimal.valueOf(2)),
                    new PaperSectionSpec("二、非选择题", "3 大题，共 40 分：材料分析、史料解读、论述简答。", 4, 3, BigDecimal.valueOf(13.33))
            );
            case "物理" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "笔试 90 分中的选择题：10 小题，每题 3 分，共 30 分。", 1, 10, BigDecimal.valueOf(3)),
                    new PaperSectionSpec("二、非选择题", "8 小题，共 60 分：填空、作图、实验探究、计算、综合能力。", 4, 8, BigDecimal.valueOf(7.5)),
                    new PaperSectionSpec("三、实验操作", "实验操作 10 分。", 4, 1, BigDecimal.valueOf(10))
            );
            case "化学" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "笔试 90 分中的选择题：14 小题，每题 3 分，共 42 分。", 1, 14, BigDecimal.valueOf(3)),
                    new PaperSectionSpec("二、非选择题", "6 小题，共 48 分：基础填空、工艺流程、科普阅读、科学探究、化学计算。", 4, 6, BigDecimal.valueOf(8)),
                    new PaperSectionSpec("三、实验操作", "实验操作 10 分。", 4, 1, BigDecimal.valueOf(10))
            );
            case "地理" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "30 小题，每题 2 分，共 60 分。", 1, 30, BigDecimal.valueOf(2)),
                    new PaperSectionSpec("二、综合题", "2-3 大题，共 40 分；以读图题为主。", 4, 3, BigDecimal.valueOf(13.33))
            );
            case "生物" -> List.of(
                    new PaperSectionSpec("一、单项选择题", "30 小题，每题 2 分，共 60 分。", 1, 30, BigDecimal.valueOf(2)),
                    new PaperSectionSpec("二、非选择题", "4 大题，共 40 分：读图理解、资料分析、实验探究、综合应用。", 4, 4, BigDecimal.valueOf(10))
            );
            default -> List.of();
        };
    }

    private record PaperSectionSpec(String title, String note, Integer questionType, int count, BigDecimal score) {
    }

    private record SelectedQuestion(QuestionBank question, PaperSectionSpec section) {
    }

    private List<QuestionBank> uniqueQuestions(List<QuestionBank> questions) {
        Map<String, QuestionBank> unique = new LinkedHashMap<>();
        for (QuestionBank question : questions) {
            if (question == null) {
                continue;
            }
            String key = questionKey(question);
            unique.putIfAbsent(key, question);
        }
        return new ArrayList<>(unique.values());
    }

    private List<QuestionBank> mergeQuestions(List<QuestionBank> primary, List<QuestionBank> fallback) {
        List<QuestionBank> merged = new ArrayList<>();
        merged.addAll(primary);
        merged.addAll(fallback);
        return uniqueQuestions(merged);
    }

    private String questionKey(QuestionBank question) {
        String text = ResponseUtils.safe(question.getQuestionText()).trim();
        if (StringUtils.hasText(text)) {
            return "text:" + ResponseUtils.safe(question.getSubject()) + ":" + text;
        }
        return "id:" + question.getQuestionId();
    }

    private List<QuestionBank> stableRotatedQuestions(List<QuestionBank> questions, Assessment assessment, int limit) {
        if (questions.isEmpty()) {
            return List.of();
        }
        List<QuestionBank> ordered = questions.stream()
                .sorted(Comparator
                        .comparingInt((QuestionBank question) -> difficultyGap(question.getDifficulty(), assessment.getDifficulty()))
                        .thenComparing(question -> ResponseUtils.safe(question.getKnowledgePoint()))
                        .thenComparing(QuestionBank::getQuestionId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toCollection(ArrayList::new));
        if (ordered.size() <= limit) {
            return ordered;
        }
        int offset = Math.floorMod(Objects.hashCode(assessment.getAssessmentId()), ordered.size());
        Collections.rotate(ordered, -offset);
        return ordered;
    }

    private Set<Long> recentQuestionIds(Assessment assessment) {
        if (assessment == null || assessment.getUserId() == null || !StringUtils.hasText(assessment.getSubject())) {
            return Set.of();
        }
        try {
            List<Assessment> recentAssessments = assessmentService.lambdaQuery()
                    .eq(Assessment::getUserId, assessment.getUserId())
                    .eq(Assessment::getSubject, assessment.getSubject())
                    .eq(Assessment::getAssessmentStatus, 2)
                    .ne(assessment.getAssessmentId() != null, Assessment::getAssessmentId, assessment.getAssessmentId())
                    .orderByDesc(Assessment::getEndTime)
                    .last("LIMIT " + RECENT_ASSESSMENT_LOOKBACK)
                    .list();
            List<Long> assessmentIds = recentAssessments.stream()
                    .map(Assessment::getAssessmentId)
                    .filter(Objects::nonNull)
                    .toList();
            if (assessmentIds.isEmpty()) {
                return Set.of();
            }
            return assessmentAnswerService.lambdaQuery()
                    .eq(AssessmentAnswer::getUserId, assessment.getUserId())
                    .in(AssessmentAnswer::getAssessmentId, assessmentIds)
                    .list()
                    .stream()
                    .map(AssessmentAnswer::getQuestionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }

    private List<AssessmentAnswer> buildAnswerDetails(Assessment assessment,
                                                      List<SelectedQuestion> selectedQuestions,
                                                      List<Map<String, Object>> answers) {
        Map<Long, Map<String, Object>> answerMap = new LinkedHashMap<>();
        for (Map<String, Object> item : answers) {
            Long questionId = toLong(item.get("questionId"));
            if (questionId != null) {
                answerMap.put(questionId, item);
            }
        }
        List<BigDecimal> maxScores = maxScoresForSelectedQuestions(assessment, selectedQuestions);
        List<AssessmentAnswer> details = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < selectedQuestions.size(); index++) {
            QuestionBank question = selectedQuestions.get(index).question();
            BigDecimal maxScore = maxScores.get(index);
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

    private List<BigDecimal> maxScoresForSelectedQuestions(Assessment assessment, List<SelectedQuestion> selectedQuestions) {
        if (selectedQuestions.isEmpty()) {
            return List.of();
        }
        BigDecimal totalScore = assessment.getTotalScore() == null || assessment.getTotalScore().compareTo(BigDecimal.ZERO) <= 0
                ? defaultTotalScore(assessment.getSubject())
                : assessment.getTotalScore();
        if (selectedQuestions.stream().anyMatch(selected -> isGeneratedQuestion(selected.question()))) {
            return equalScores(totalScore, selectedQuestions.size());
        }
        List<PaperSectionSpec> sections = paperSectionSpecs(assessment.getSubject());
        return sections.isEmpty()
                ? equalScores(totalScore, selectedQuestions.size())
                : standardPaperScoresForSelected(totalScore, selectedQuestions);
    }

    private List<BigDecimal> standardPaperScoresForSelected(BigDecimal totalScore, List<SelectedQuestion> selectedQuestions) {
        List<BigDecimal> scores = selectedQuestions.stream()
                .map(selected -> selected.section() == null ? BigDecimal.ZERO : selected.section().score())
                .collect(Collectors.toCollection(ArrayList::new));
        BigDecimal allocated = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!scores.isEmpty() && allocated.compareTo(totalScore) != 0) {
            int last = scores.size() - 1;
            scores.set(last, scores.get(last).add(totalScore.subtract(allocated)).setScale(2, RoundingMode.HALF_UP));
        }
        return scores;
    }

    private List<BigDecimal> standardPaperScores(BigDecimal totalScore, int questionCount, List<PaperSectionSpec> sections) {
        List<BigDecimal> scores = new ArrayList<>();
        for (PaperSectionSpec section : sections) {
            for (int index = 0; index < section.count() && scores.size() < questionCount; index++) {
                scores.add(section.score());
            }
        }
        while (scores.size() < questionCount) {
            scores.add(BigDecimal.ZERO);
        }
        BigDecimal allocated = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!scores.isEmpty() && allocated.compareTo(totalScore) != 0) {
            int last = scores.size() - 1;
            scores.set(last, scores.get(last).add(totalScore.subtract(allocated)).setScale(2, RoundingMode.HALF_UP));
        }
        return scores;
    }

    private List<BigDecimal> equalScores(BigDecimal totalScore, int count) {
        BigDecimal perScore = totalScore.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        List<BigDecimal> scores = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < count; index++) {
            BigDecimal score = index == count - 1
                    ? totalScore.subtract(allocated).setScale(2, RoundingMode.HALF_UP)
                    : perScore;
            scores.add(score);
            allocated = allocated.add(score);
        }
        return scores;
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
            aiResult = localSubjectiveScore(question, safeAnswer, maxScore, aiResult);
        }

        BigDecimal score = clampScore(toBigDecimal(aiResult.get("score"), maxScore.multiply(BigDecimal.valueOf(0.5D))), maxScore);
        BigDecimal confidence = clampPercent(toBigDecimal(aiResult.get("confidence"), BigDecimal.ZERO));
        boolean reliable = aiAvailable
                && !aiFallbackOrFailure(aiResult)
                && confidence.compareTo(AI_AUTO_SCORE_CONFIDENCE) >= 0;

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
        data.put("fallback", true);
        data.put("score", maxScore.multiply(ratio).setScale(2, RoundingMode.HALF_UP));
        data.put("confidence", BigDecimal.valueOf(45));
        data.put("matchedPoints", matchedPoints);
        data.put("missingPoints", missingPoints);
        data.put("scoringMode", "java_fallback");
        if (unavailableReason instanceof Map<?, ?> rawObservation) {
            Map<String, Object> observation = new LinkedHashMap<>();
            rawObservation.forEach((key, value) -> observation.put(String.valueOf(key), value));
            copyObservation(observation, data, "operation", "endpoint", "latencyMs", "provider", "model",
                    "failureCategory", "errorCode", "errorMessage", "message");
        }
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
        data.put("knowledgePoint", question == null ? "" : cleanKnowledgePoint(question.getKnowledgePoint()));
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
        data.put("requiresManualReview", Integer.valueOf(SCORE_STATUS_PENDING_MANUAL).equals(answer.getScoreStatus()));
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
        return queryQuestions(assessment, strictDifficulty, true);
    }

    private List<QuestionBank> queryQuestions(Assessment assessment, boolean strictDifficulty, boolean strictKnowledgeScope) {
        List<String> scopes = strictKnowledgeScope ? splitScopes(assessment.getKnowledgeScope()) : List.of();
        List<QuestionBank> questions = questionBankService.lambdaQuery()
                .eq(StringUtils.hasText(assessment.getSubject()), QuestionBank::getSubject, assessment.getSubject())
                .eq(strictDifficulty && assessment.getDifficulty() != null, QuestionBank::getDifficulty, assessment.getDifficulty())
                .list();
        return questions.stream()
                .filter(question -> !isGeneratedQuestion(question))
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
        data.put("knowledgePoint", cleanKnowledgePoint(question.getKnowledgePoint()));
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
        int paperCount = paperSectionSpecs(assessment.getSubject()).stream()
                .mapToInt(PaperSectionSpec::count)
                .sum();
        if (paperCount > 0) {
            return paperCount;
        }
        Integer type = assessment.getAssessmentType();
        if (type != null && type == 3) {
            return 10;
        }
        if (type != null && type == 2) {
            return 8;
        }
        return 5;
    }

    private BigDecimal defaultTotalScore(String subject) {
        return CORE_EXAM_SUBJECTS.contains(ResponseUtils.safe(subject))
                ? BigDecimal.valueOf(120)
                : BigDecimal.valueOf(100);
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
        String observation = aiObservationSummary(aiResult);
        if (StringUtils.hasText(observation)) {
            detail += ", aiObservation=" + observation;
        }
        return limitText(detail, 480);
    }

    private boolean aiFallbackOrFailure(Map<String, Object> aiResult) {
        if (aiResult == null || aiResult.isEmpty()) {
            return true;
        }
        if (Boolean.TRUE.equals(aiResult.get("fallback"))) {
            return true;
        }
        String mode = observationText(aiResult, "scoringMode");
        String provider = observationText(aiResult, "provider");
        String failureCategory = observationText(aiResult, "failureCategory");
        String errorCode = observationText(aiResult, "errorCode");
        return mode.contains("fallback")
                || provider.contains("fallback")
                || StringUtils.hasText(failureCategory)
                || StringUtils.hasText(errorCode);
    }

    private String aiObservationSummary(Map<String, Object> aiResult) {
        List<String> parts = new ArrayList<>();
        addObservationPart(parts, "provider", aiResult);
        addObservationPart(parts, "model", aiResult);
        addObservationPart(parts, "latencyMs", aiResult);
        addObservationPart(parts, "failureCategory", aiResult);
        addObservationPart(parts, "errorCode", aiResult);
        return String.join("; ", parts);
    }

    private void addObservationPart(List<String> parts, String key, Map<String, Object> aiResult) {
        String value = observationText(aiResult, key);
        if (StringUtils.hasText(value)) {
            parts.add(key + "=" + value);
        }
    }

    private String observationText(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) {
            return "";
        }
        String value = ResponseUtils.safe(data.get(key).toString());
        return "null".equalsIgnoreCase(value) ? "" : value;
    }

    private void copyObservation(Map<String, Object> source, Map<String, Object> target, String... keys) {
        if (source == null || target == null) {
            return;
        }
        for (String key : keys) {
            if (source.containsKey(key) && !target.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
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
