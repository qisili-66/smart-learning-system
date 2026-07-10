package com.smartlearning.backend.module.wrong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.ResponseUtils;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;
import com.smartlearning.backend.module.wrong.entity.WrongQuestionReviewPlan;
import com.smartlearning.backend.module.wrong.mapper.WrongQuestionMapper;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionReviewPlanService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion> implements WrongQuestionService {

    private final QuestionBankService questionBankService;
    private final LearningResourceService learningResourceService;
    private final WrongQuestionReviewPlanService reviewPlanService;

    public WrongQuestionServiceImpl(QuestionBankService questionBankService,
                                    LearningResourceService learningResourceService,
                                    WrongQuestionReviewPlanService reviewPlanService) {
        this.questionBankService = questionBankService;
        this.learningResourceService = learningResourceService;
        this.reviewPlanService = reviewPlanService;
    }

    @Override
    public Map<String, Object> collectWrongAnswer(Long userId, Long questionId, String wrongAnswer, Integer wrongReason) {
        QuestionBank question = getQuestion(questionId);
        boolean correct = answerMatches(question.getAnswer(), wrongAnswer);
        if (correct) {
            return Map.of(
                    "questionId", questionId,
                    "collected", false,
                    "correct", true
            );
        }

        WrongQuestion wrongQuestion = getOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId), false);
        LocalDateTime now = LocalDateTime.now();
        if (wrongQuestion == null) {
            wrongQuestion = new WrongQuestion();
            wrongQuestion.setUserId(userId);
            wrongQuestion.setQuestionId(questionId);
            wrongQuestion.setWrongCount(1);
            wrongQuestion.setFirstWrongTime(now);
        } else {
            wrongQuestion.setWrongCount((wrongQuestion.getWrongCount() == null ? 0 : wrongQuestion.getWrongCount()) + 1);
        }
        wrongQuestion.setWrongAnswer(wrongAnswer == null ? "" : wrongAnswer);
        wrongQuestion.setWrongReason(wrongReason == null ? inferWrongReason(question, wrongAnswer) : wrongReason);
        wrongQuestion.setIsMastered(Constants.NOT_MASTERED);
        wrongQuestion.setLastReviewTime(now);
        saveOrUpdate(wrongQuestion);
        ensureReviewPlan(wrongQuestion, question);

        Map<String, Object> data = toWrongMap(wrongQuestion, question);
        data.put("collected", true);
        data.put("correct", false);
        return data;
    }

    @Override
    public List<Map<String, Object>> collectFromAnswers(Long userId, List<Map<String, Object>> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> collected = new ArrayList<>();
        for (Map<String, Object> answer : answers) {
            Long questionId = toLong(answer.get("questionId"));
            if (questionId == null) {
                continue;
            }
            Map<String, Object> result = collectWrongAnswer(
                    userId,
                    questionId,
                    Objects.toString(answer.get("userAnswer"), ""),
                    toInteger(answer.get("wrongReason"))
            );
            if (Boolean.TRUE.equals(result.get("collected"))) {
                collected.add(result);
            }
        }
        return collected;
    }

    @Override
    public PageVO<Map<String, Object>> similarQuestions(Long userId, Long wrongId, Integer limit) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(userId, wrongId);
        QuestionBank source = getQuestion(wrongQuestion.getQuestionId());
        int safeLimit = limit == null ? 5 : Math.max(1, Math.min(limit, 10));

        List<QuestionBank> candidates = questionBankService.lambdaQuery()
                .eq(StringUtils.hasText(source.getSubject()), QuestionBank::getSubject, source.getSubject())
                .ne(QuestionBank::getQuestionId, source.getQuestionId())
                .list()
                .stream()
                .sorted((left, right) -> Integer.compare(
                        similarScore(left, source),
                        similarScore(right, source)
                ))
                .limit(safeLimit)
                .toList();

        List<Map<String, Object>> rows = candidates.stream().map(this::toQuestionMap).toList();
        return PageVO.<Map<String, Object>>builder()
                .list(rows)
                .total((long) rows.size())
                .pageNum(1)
                .pageSize(safeLimit)
                .pages(rows.isEmpty() ? 0 : 1)
                .build();
    }

    @Override
    public Map<String, Object> statistics(Long userId, String subject) {
        List<WrongQuestion> wrongQuestions = lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .list();
        Map<Long, QuestionBank> questionMap = loadQuestionMap(wrongQuestions);
        List<WrongQuestion> filtered = wrongQuestions.stream()
                .filter(wrong -> {
                    if (!StringUtils.hasText(subject)) {
                        return true;
                    }
                    QuestionBank question = questionMap.get(wrong.getQuestionId());
                    return question != null && subject.equals(question.getSubject());
                })
                .toList();

        long mastered = filtered.stream()
                .filter(wrong -> Constants.IS_MASTERED.equals(wrong.getIsMastered()))
                .count();
        Map<Integer, Long> reasonDistribution = filtered.stream()
                .collect(Collectors.groupingBy(
                        wrong -> wrong.getWrongReason() == null ? 0 : wrong.getWrongReason(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<String, Long> knowledgeDistribution = filtered.stream()
                .map(wrong -> questionMap.get(wrong.getQuestionId()))
                .filter(Objects::nonNull)
                .map(question -> ResponseUtils.safe(question.getKnowledgePoint()))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", filtered.size());
        data.put("mastered", mastered);
        data.put("notMastered", filtered.size() - mastered);
        data.put("reasonDistribution", reasonDistribution);
        data.put("knowledgeDistribution", knowledgeDistribution);
        data.put("reviewTasks", buildReviewTasks(knowledgeDistribution));
        data.put("dueReviewCount", dueReviewCount(filtered));
        return data;
    }

    @Override
    public Map<String, Object> exportBook(Long userId, String subject, Integer isMastered, String format) {
        List<WrongQuestion> wrongQuestions = lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .eq(isMastered != null, WrongQuestion::getIsMastered, isMastered)
                .orderByDesc(WrongQuestion::getFirstWrongTime)
                .list();
        Map<Long, QuestionBank> questionMap = loadQuestionMap(wrongQuestions);
        List<Map<String, Object>> items = wrongQuestions.stream()
                .map(wrong -> toWrongMap(wrong, questionMap.get(wrong.getQuestionId())))
                .filter(item -> !StringUtils.hasText(subject) || subject.equals(item.get("subject")))
                .toList();

        String exportFormat = normalizeExportFormat(format);
        String fileName = "wrong-question-book-" + userId + "-"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "." + exportFormat;
        String content = buildExportContent(items, exportFormat);
        Path filePath = writeExportFile(fileName, content);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", exportFormat);
        data.put("requestedFormat", StringUtils.hasText(format) ? format : "");
        data.put("fileName", fileName);
        data.put("downloadUrl", "/api/wrong-questions/export-files/" + fileName);
        data.put("filePath", filePath.toAbsolutePath().toString());
        data.put("total", items.size());
        data.put("items", items);
        data.put("content", content);
        return data;
    }

    @Override
    public Map<String, Object> updateReviewPlan(Long userId, Long wrongId, Map<String, Object> request) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(userId, wrongId);
        QuestionBank question = wrongQuestion.getQuestionId() == null ? null : questionBankService.getById(wrongQuestion.getQuestionId());
        WrongQuestionReviewPlan plan = findReviewPlan(userId, wrongId);
        LocalDateTime now = LocalDateTime.now();
        if (plan == null) {
            plan = new WrongQuestionReviewPlan();
            plan.setWrongId(wrongId);
            plan.setUserId(userId);
            plan.setCreateTime(now);
        }

        if (request.containsKey("personalNote")) {
            plan.setPersonalNote(safeObject(request.get("personalNote")));
        }
        int cycle = Math.max(1, defaultInteger(request.get("reviewCycleDays"),
                defaultReviewCycleDays(wrongQuestion, question)));
        plan.setReviewCycleDays(cycle);
        plan.setNextReviewTime(parseNextReviewTime(request.get("nextReviewTime"), now.plusDays(cycle)));
        plan.setUpdateTime(now);
        reviewPlanService.saveOrUpdate(plan);
        return reviewPlanMap(wrongQuestion, question, plan);
    }

    @Override
    public Map<String, Object> reviewPlan(Long userId, Long wrongId) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(userId, wrongId);
        QuestionBank question = wrongQuestion.getQuestionId() == null ? null : questionBankService.getById(wrongQuestion.getQuestionId());
        return reviewPlanMap(wrongQuestion, question, safeFindReviewPlan(userId, wrongId));
    }

    @Override
    public void rescheduleReview(Long userId, Long wrongId, boolean mastered) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(userId, wrongId);
        QuestionBank question = wrongQuestion.getQuestionId() == null ? null : questionBankService.getById(wrongQuestion.getQuestionId());
        try {
            WrongQuestionReviewPlan plan = findReviewPlan(userId, wrongId);
            LocalDateTime now = LocalDateTime.now();
            if (plan == null) {
                plan = new WrongQuestionReviewPlan();
                plan.setWrongId(wrongId);
                plan.setUserId(userId);
                plan.setPersonalNote("");
                plan.setReviewCycleDays(defaultReviewCycleDays(wrongQuestion, question));
                plan.setCreateTime(now);
            }
            int cycle = Math.max(1, plan.getReviewCycleDays() == null
                    ? defaultReviewCycleDays(wrongQuestion, question)
                    : plan.getReviewCycleDays());
            plan.setNextReviewTime(now.plusDays(mastered ? cycle : Math.max(1, cycle / 2)));
            plan.setUpdateTime(now);
            reviewPlanService.saveOrUpdate(plan);
        } catch (RuntimeException ignored) {
            // Review plan table is optional until the F4/F7 migration is executed.
        }
    }

    @Override
    public int deleteWrongQuestion(Long userId, Long wrongId) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(userId, wrongId);
        removeReviewPlans(userId, List.of(wrongQuestion.getWrongId()));
        return removeById(wrongQuestion.getWrongId()) ? 1 : 0;
    }

    @Override
    public int clearWrongQuestions(Long userId, String subject, Integer wrongReason, Integer isMastered) {
        List<WrongQuestion> wrongQuestions = lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .eq(wrongReason != null, WrongQuestion::getWrongReason, wrongReason)
                .eq(isMastered != null, WrongQuestion::getIsMastered, isMastered)
                .list();
        Map<Long, QuestionBank> questionMap = loadQuestionMap(wrongQuestions);
        List<Long> ids = wrongQuestions.stream()
                .filter(wrong -> {
                    if (!StringUtils.hasText(subject)) {
                        return true;
                    }
                    QuestionBank question = questionMap.get(wrong.getQuestionId());
                    return question != null && subject.equals(question.getSubject());
                })
                .map(WrongQuestion::getWrongId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        removeReviewPlans(userId, ids);
        removeByIds(ids);
        return ids.size();
    }

    @Override
    public Path exportFile(Long userId, String fileName) {
        if (!StringUtils.hasText(fileName) || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "fileName参数错误");
        }
        String expectedPrefix = "wrong-question-book-" + userId + "-";
        if (!fileName.startsWith(expectedPrefix)) {
            throw new BusinessException(Constants.CODE_FORBIDDEN, "无权限下载该文件");
        }
        try {
            Path file = exportDirectory().resolve(fileName).normalize();
            if (!file.startsWith(exportDirectory()) || !Files.exists(file)) {
                throw new BusinessException(Constants.CODE_NOT_FOUND, "导出文件不存在");
            }
            return file;
        } catch (InvalidPathException e) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "fileName参数错误");
        }
    }

    private void removeReviewPlans(Long userId, List<Long> wrongIds) {
        if (wrongIds == null || wrongIds.isEmpty()) {
            return;
        }
        try {
            reviewPlanService.remove(new LambdaQueryWrapper<WrongQuestionReviewPlan>()
                    .eq(WrongQuestionReviewPlan::getUserId, userId)
                    .in(WrongQuestionReviewPlan::getWrongId, wrongIds));
        } catch (RuntimeException ignored) {
            // Review plan table is optional until the F4/F7 migration is executed.
        }
    }

    private WrongQuestion getOwnedWrongQuestion(Long userId, Long wrongId) {
        WrongQuestion wrongQuestion = getById(wrongId);
        if (wrongQuestion == null || !userId.equals(wrongQuestion.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "wrong question not found");
        }
        return wrongQuestion;
    }

    private QuestionBank getQuestion(Long questionId) {
        QuestionBank question = questionBankService.getById(questionId);
        if (question == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "question not found");
        }
        return question;
    }

    private Map<Long, QuestionBank> loadQuestionMap(List<WrongQuestion> wrongQuestions) {
        List<Long> ids = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionBankService.listByIds(ids).stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, Function.identity(), (left, right) -> left));
    }

    private Map<String, Object> toWrongMap(WrongQuestion wrongQuestion, QuestionBank question) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wrongId", wrongQuestion.getWrongId());
        data.put("questionId", wrongQuestion.getQuestionId());
        data.put("subject", question == null ? "" : ResponseUtils.safe(question.getSubject()));
        data.put("knowledgePoint", question == null ? "" : ResponseUtils.safe(question.getKnowledgePoint()));
        data.put("difficulty", question == null ? null : question.getDifficulty());
        data.put("questionType", question == null ? null : question.getQuestionType());
        data.put("questionText", question == null ? "" : ResponseUtils.safe(question.getQuestionText()));
        data.put("options", question == null || !StringUtils.hasText(question.getOptions())
                ? List.of()
                : Arrays.asList(question.getOptions().split("\\|")));
        data.put("correctAnswer", question == null ? "" : ResponseUtils.safe(question.getAnswer()));
        data.put("wrongAnswer", ResponseUtils.safe(wrongQuestion.getWrongAnswer()));
        data.put("wrongReason", wrongQuestion.getWrongReason());
        data.put("wrongReasonLabel", wrongReasonLabel(wrongQuestion.getWrongReason()));
        data.put("wrongCount", wrongQuestion.getWrongCount());
        data.put("analysis", question == null ? "" : ResponseUtils.safe(question.getAnalysis()));
        data.put("relatedResources", relatedResources(question));
        data.put("isMastered", wrongQuestion.getIsMastered());
        data.put("firstWrongTime", ResponseUtils.format(wrongQuestion.getFirstWrongTime()));
        data.put("lastReviewTime", ResponseUtils.format(wrongQuestion.getLastReviewTime()));
        data.putAll(reviewPlanMap(wrongQuestion, question, safeFindReviewPlan(wrongQuestion.getUserId(), wrongQuestion.getWrongId())));
        return data;
    }

    private List<Map<String, Object>> relatedResources(QuestionBank question) {
        if (question == null || !StringUtils.hasText(question.getKnowledgePoint())) {
            return List.of();
        }
        return learningResourceService.lambdaQuery()
                .eq(StringUtils.hasText(question.getSubject()), LearningResource::getSubject, question.getSubject())
                .like(LearningResource::getKnowledgePoint, question.getKnowledgePoint())
                .list()
                .stream()
                .sorted((left, right) -> Integer.compare(resourcePriority(left), resourcePriority(right)))
                .limit(5)
                .map(this::toResourceMap)
                .toList();
    }

    private Map<String, Object> toResourceMap(LearningResource resource) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resourceId", resource.getResourceId());
        data.put("resourceName", ResponseUtils.safe(resource.getResourceName()));
        data.put("resourceType", resource.getResourceType());
        data.put("subject", ResponseUtils.safe(resource.getSubject()));
        data.put("knowledgePoint", ResponseUtils.safe(resource.getKnowledgePoint()));
        data.put("fileUrl", ResponseUtils.safe(resource.getFileUrl()));
        return data;
    }

    private int resourcePriority(LearningResource resource) {
        Integer type = resource.getResourceType();
        if (type == null) {
            return 99;
        }
        return switch (type) {
            case 1 -> 1;
            case 2 -> 2;
            case 5 -> 3;
            case 3 -> 4;
            default -> 5;
        };
    }

    private Map<String, Object> toQuestionMap(QuestionBank question) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", question.getQuestionId());
        data.put("subject", ResponseUtils.safe(question.getSubject()));
        data.put("knowledgePoint", ResponseUtils.safe(question.getKnowledgePoint()));
        data.put("difficulty", question.getDifficulty());
        data.put("questionType", question.getQuestionType());
        data.put("questionText", ResponseUtils.safe(question.getQuestionText()));
        data.put("options", !StringUtils.hasText(question.getOptions()) ? List.of() : Arrays.asList(question.getOptions().split("\\|")));
        data.put("answer", ResponseUtils.safe(question.getAnswer()));
        data.put("analysis", ResponseUtils.safe(question.getAnalysis()));
        return data;
    }

    private List<Map<String, Object>> buildReviewTasks(Map<String, Long> knowledgeDistribution) {
        return knowledgeDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> Map.<String, Object>of(
                        "knowledgePoint", entry.getKey(),
                        "wrongCount", entry.getValue(),
                        "suggestion", "优先复盘该知识点并完成3道同类题"
                ))
                .toList();
    }

    private String buildExportContent(List<Map<String, Object>> items, String format) {
        return switch (format) {
            case "csv" -> buildCsvContent(items);
            case "txt" -> buildTextContent(items);
            case "html", "doc" -> buildHtmlContent(items);
            default -> buildMarkdownContent(items);
        };
    }

    private String buildMarkdownContent(List<Map<String, Object>> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 电子错题本\n\n");
        int index = 1;
        for (Map<String, Object> item : items) {
            builder.append(index++).append(". ").append(item.get("questionText")).append("\n");
            builder.append("   - 我的答案：").append(item.get("wrongAnswer")).append("\n");
            builder.append("   - 正确答案：").append(item.get("correctAnswer")).append("\n");
            builder.append("   - 知识点：").append(item.get("knowledgePoint")).append("\n");
            builder.append("   - 个人备注：").append(item.get("personalNote")).append("\n");
            builder.append("   - 下次复盘：").append(item.get("nextReviewTime")).append("\n");
            builder.append("   - 解析：").append(item.get("analysis")).append("\n\n");
        }
        return builder.toString();
    }

    private String buildTextContent(List<Map<String, Object>> items) {
        return buildMarkdownContent(items).replace("# ", "");
    }

    private String buildHtmlContent(List<Map<String, Object>> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"UTF-8\"><title>电子错题本</title>")
                .append("<style>body{font-family:Arial,'Microsoft YaHei',sans-serif;line-height:1.7;}")
                .append("h1{font-size:24px;}section{margin:18px 0;padding:12px;border-bottom:1px solid #ddd;}")
                .append("dt{font-weight:bold;}dd{margin:0 0 8px 0;}</style></head><body><h1>电子错题本</h1>");
        int index = 1;
        for (Map<String, Object> item : items) {
            builder.append("<section><h2>").append(index++).append(". ")
                    .append(escapeHtml(item.get("questionText"))).append("</h2><dl>")
                    .append("<dt>我的答案</dt><dd>").append(escapeHtml(item.get("wrongAnswer"))).append("</dd>")
                    .append("<dt>正确答案</dt><dd>").append(escapeHtml(item.get("correctAnswer"))).append("</dd>")
                    .append("<dt>知识点</dt><dd>").append(escapeHtml(item.get("knowledgePoint"))).append("</dd>")
                    .append("<dt>个人备注</dt><dd>").append(escapeHtml(item.get("personalNote"))).append("</dd>")
                    .append("<dt>复盘周期</dt><dd>").append(escapeHtml(item.get("reviewCycleDays"))).append(" 天</dd>")
                    .append("<dt>下次复盘</dt><dd>").append(escapeHtml(item.get("nextReviewTime"))).append("</dd>")
                    .append("<dt>解析</dt><dd>").append(escapeHtml(item.get("analysis"))).append("</dd>")
                    .append("</dl></section>");
        }
        return builder.append("</body></html>").toString();
    }

    private String buildCsvContent(List<Map<String, Object>> items) {
        StringBuilder builder = new StringBuilder("题目,我的答案,正确答案,知识点,错误原因,个人备注,复盘周期,下次复盘,解析\n");
        for (Map<String, Object> item : items) {
            builder.append(csv(item.get("questionText"))).append(',')
                    .append(csv(item.get("wrongAnswer"))).append(',')
                    .append(csv(item.get("correctAnswer"))).append(',')
                    .append(csv(item.get("knowledgePoint"))).append(',')
                    .append(csv(item.get("wrongReasonLabel"))).append(',')
                    .append(csv(item.get("personalNote"))).append(',')
                    .append(csv(item.get("reviewCycleDays"))).append(',')
                    .append(csv(item.get("nextReviewTime"))).append(',')
                    .append(csv(item.get("analysis"))).append('\n');
        }
        return builder.toString();
    }

    private void ensureReviewPlan(WrongQuestion wrongQuestion, QuestionBank question) {
        try {
            if (wrongQuestion.getWrongId() == null || findReviewPlan(wrongQuestion.getUserId(), wrongQuestion.getWrongId()) != null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            WrongQuestionReviewPlan plan = new WrongQuestionReviewPlan();
            plan.setWrongId(wrongQuestion.getWrongId());
            plan.setUserId(wrongQuestion.getUserId());
            plan.setPersonalNote("");
            plan.setReviewCycleDays(defaultReviewCycleDays(wrongQuestion, question));
            plan.setNextReviewTime(now.plusDays(plan.getReviewCycleDays()));
            plan.setCreateTime(now);
            plan.setUpdateTime(now);
            reviewPlanService.save(plan);
        } catch (RuntimeException ignored) {
            // Existing databases can still use the wrong-question core flow before this migration is applied.
        }
    }

    private long dueReviewCount(List<WrongQuestion> wrongQuestions) {
        LocalDateTime now = LocalDateTime.now();
        return wrongQuestions.stream()
                .map(wrong -> safeFindReviewPlan(wrong.getUserId(), wrong.getWrongId()))
                .filter(Objects::nonNull)
                .map(WrongQuestionReviewPlan::getNextReviewTime)
                .filter(Objects::nonNull)
                .filter(time -> !time.isAfter(now))
                .count();
    }

    private WrongQuestionReviewPlan findReviewPlan(Long userId, Long wrongId) {
        return reviewPlanService.lambdaQuery()
                .eq(WrongQuestionReviewPlan::getUserId, userId)
                .eq(WrongQuestionReviewPlan::getWrongId, wrongId)
                .one();
    }

    private WrongQuestionReviewPlan safeFindReviewPlan(Long userId, Long wrongId) {
        try {
            if (userId == null || wrongId == null) {
                return null;
            }
            return findReviewPlan(userId, wrongId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Object> reviewPlanMap(WrongQuestion wrongQuestion, QuestionBank question, WrongQuestionReviewPlan plan) {
        int cycle = plan == null || plan.getReviewCycleDays() == null
                ? defaultReviewCycleDays(wrongQuestion, question)
                : plan.getReviewCycleDays();
        LocalDateTime nextReview = plan == null || plan.getNextReviewTime() == null
                ? defaultNextReviewTime(wrongQuestion, cycle)
                : plan.getNextReviewTime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("personalNote", plan == null ? "" : ResponseUtils.safe(plan.getPersonalNote()));
        data.put("reviewCycleDays", cycle);
        data.put("nextReviewTime", ResponseUtils.format(nextReview));
        data.put("reviewDue", nextReview != null && !nextReview.isAfter(LocalDateTime.now()));
        return data;
    }

    private int defaultReviewCycleDays(WrongQuestion wrongQuestion, QuestionBank question) {
        int difficulty = question == null || question.getDifficulty() == null ? 2 : question.getDifficulty();
        int wrongCount = wrongQuestion == null || wrongQuestion.getWrongCount() == null ? 1 : wrongQuestion.getWrongCount();
        int base = switch (difficulty) {
            case 1 -> 5;
            case 3 -> 2;
            default -> 3;
        };
        if (wrongCount >= 3) {
            return Math.max(1, base - 1);
        }
        return base;
    }

    private LocalDateTime defaultNextReviewTime(WrongQuestion wrongQuestion, int cycle) {
        LocalDateTime start = wrongQuestion == null || wrongQuestion.getLastReviewTime() == null
                ? LocalDateTime.now()
                : wrongQuestion.getLastReviewTime();
        return start.plusDays(Math.max(1, cycle));
    }

    private LocalDateTime parseNextReviewTime(Object value, LocalDateTime defaultValue) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return defaultValue;
        }
        try {
            String text = value.toString().trim().replace(" ", "T");
            if (text.length() == 10) {
                return java.time.LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private String normalizeExportFormat(String format) {
        String value = StringUtils.hasText(format) ? format.trim().toLowerCase() : "doc";
        if (List.of("doc", "html", "md", "txt", "csv").contains(value)) {
            return value;
        }
        return "doc";
    }

    private Path writeExportFile(String fileName, String content) {
        try {
            Path directory = exportDirectory();
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName).normalize();
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "写入错题本导出文件失败");
        }
    }

    private Path exportDirectory() {
        return Paths.get("exports", "wrong-question-book").toAbsolutePath().normalize();
    }

    private String escapeHtml(Object value) {
        return safeObject(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String csv(Object value) {
        return "\"" + safeObject(value).replace("\"", "\"\"") + "\"";
    }

    private String safeObject(Object value) {
        return value == null ? "" : value.toString();
    }

    private Integer defaultInteger(Object value, Integer defaultValue) {
        Integer parsed = toInteger(value);
        return parsed == null ? defaultValue : parsed;
    }

    private int inferWrongReason(QuestionBank question, String wrongAnswer) {
        String answer = normalize(question.getAnswer());
        String wrong = normalize(wrongAnswer);
        if (wrong.isBlank()) {
            return 3;
        }
        if (answer.length() <= 2 && wrong.length() <= 2) {
            return 2;
        }
        if (question.getQuestionType() != null && question.getQuestionType() == 1) {
            return 2;
        }
        return 4;
    }

    private String wrongReasonLabel(Integer reason) {
        if (reason == null) {
            return "未分类";
        }
        return switch (reason) {
            case 1 -> "计算失误";
            case 2 -> "概念混淆";
            case 3 -> "审题错误";
            case 4 -> "思路错误";
            default -> "未分类";
        };
    }

    private int difficultyGap(QuestionBank candidate, QuestionBank source) {
        int left = candidate.getDifficulty() == null ? 2 : candidate.getDifficulty();
        int right = source.getDifficulty() == null ? 2 : source.getDifficulty();
        return Math.abs(left - right);
    }

    private int similarScore(QuestionBank candidate, QuestionBank source) {
        int knowledgeScore = Objects.equals(
                ResponseUtils.safe(candidate.getKnowledgePoint()),
                ResponseUtils.safe(source.getKnowledgePoint())
        ) ? 0 : 10;
        return knowledgeScore + difficultyGap(candidate, source);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").trim().toLowerCase();
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
}
