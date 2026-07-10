package com.smartlearning.backend.module.wrong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.ResponseUtils;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "错题管理模块")
@RestController
@RequestMapping("/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;
    private final QuestionBankService questionBankService;
    private final LearningResourceService learningResourceService;
    private final UserProfileService userProfileService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService,
                                   QuestionBankService questionBankService,
                                   LearningResourceService learningResourceService,
                                   UserProfileService userProfileService) {
        this.wrongQuestionService = wrongQuestionService;
        this.questionBankService = questionBankService;
        this.learningResourceService = learningResourceService;
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public Result<PageVO<Map<String, Object>>> list(@RequestParam(required = false) String subject,
                                                    @RequestParam(required = false) Integer wrongReason,
                                                    @RequestParam(required = false) Integer isMastered,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        List<Long> subjectQuestionIds = null;
        if (subject != null && !subject.isBlank()) {
            subjectQuestionIds = questionBankService.lambdaQuery()
                    .eq(QuestionBank::getSubject, subject)
                    .list()
                    .stream()
                    .map(QuestionBank::getQuestionId)
                    .toList();
            if (subjectQuestionIds.isEmpty()) {
                return Result.success(PageVO.empty(pageNum, pageSize));
            }
        }
        LambdaQueryWrapper<WrongQuestion> query = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, SecurityUtils.currentUserId())
                .in(subjectQuestionIds != null, WrongQuestion::getQuestionId, subjectQuestionIds)
                .eq(wrongReason != null, WrongQuestion::getWrongReason, wrongReason)
                .eq(isMastered != null, WrongQuestion::getIsMastered, isMastered)
                .orderByDesc(WrongQuestion::getFirstWrongTime);
        Page<WrongQuestion> page = wrongQuestionService.page(PageUtils.page(pageNum, pageSize), query);
        List<Map<String, Object>> rows = page.getRecords().stream()
                .map(wrong -> detailMap(wrong, wrong.getQuestionId() == null ? null : questionBankService.getById(wrong.getQuestionId())))
                .toList();
        return Result.success(PageVO.<Map<String, Object>>builder()
                .list(rows)
                .total(page.getTotal())
                .pageNum((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .pages((int) page.getPages())
                .build());
    }

    @GetMapping("/{wrongId}")
    public Result<Map<String, Object>> detail(@PathVariable Long wrongId) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(wrongId);
        QuestionBank question = wrongQuestion.getQuestionId() == null ? null : questionBankService.getById(wrongQuestion.getQuestionId());
        return Result.success(detailMap(wrongQuestion, question));
    }

    @PostMapping("/collect")
    public Result<Map<String, Object>> collect(@RequestBody Map<String, Object> request) {
        Long questionId = toLong(request.get("questionId"));
        if (questionId == null) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "questionId不能为空");
        }
        Map<String, Object> data = wrongQuestionService.collectWrongAnswer(
                SecurityUtils.currentUserId(),
                questionId,
                ResponseUtils.safe(request.get("userAnswer") == null ? null : request.get("userAnswer").toString()),
                toInteger(request.get("wrongReason"))
        );
        userProfileService.refreshAfterLearningEvent(SecurityUtils.currentUserId());
        return Result.success(data);
    }

    @PostMapping("/batch-collect")
    public Result<List<Map<String, Object>>> batchCollect(@RequestBody Map<String, Object> request) {
        List<Map<String, Object>> answers = castAnswers(request.get("answers"));
        List<Map<String, Object>> data = wrongQuestionService.collectFromAnswers(SecurityUtils.currentUserId(), answers);
        userProfileService.refreshAfterLearningEvent(SecurityUtils.currentUserId());
        return Result.success(data);
    }

    @PutMapping("/{wrongId}/mastered")
    public Result<Void> mastered(@PathVariable Long wrongId, @RequestBody Map<String, Integer> request) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(wrongId);
        Integer isMastered = request.get("isMastered");
        if (!Constants.NOT_MASTERED.equals(isMastered) && !Constants.IS_MASTERED.equals(isMastered)) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "isMastered参数错误");
        }
        wrongQuestion.setIsMastered(isMastered);
        wrongQuestionService.updateById(wrongQuestion);
        wrongQuestionService.rescheduleReview(wrongQuestion.getUserId(), wrongQuestion.getWrongId(), Constants.IS_MASTERED.equals(isMastered));
        userProfileService.refreshAfterLearningEvent(wrongQuestion.getUserId());
        return Result.success();
    }

    @PutMapping("/{wrongId}/review-plan")
    public Result<Map<String, Object>> reviewPlan(@PathVariable Long wrongId,
                                                  @RequestBody Map<String, Object> request) {
        WrongQuestion wrongQuestion = getOwnedWrongQuestion(wrongId);
        Map<String, Object> data = wrongQuestionService.updateReviewPlan(
                wrongQuestion.getUserId(),
                wrongQuestion.getWrongId(),
                request
        );
        return Result.success(data);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(@RequestParam(required = false) String subject) {
        return Result.success(wrongQuestionService.statistics(SecurityUtils.currentUserId(), subject));
    }

    @GetMapping("/{wrongId}/similar")
    public Result<PageVO<Map<String, Object>>> similar(@PathVariable Long wrongId,
                                                       @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(wrongQuestionService.similarQuestions(SecurityUtils.currentUserId(), wrongId, limit));
    }

    @GetMapping("/export")
    public Result<Map<String, Object>> export(@RequestParam(required = false) String subject,
                                               @RequestParam(required = false) Integer isMastered,
                                               @RequestParam(defaultValue = "pdf") String format) {
        return Result.success(wrongQuestionService.exportBook(SecurityUtils.currentUserId(), subject, isMastered, format));
    }

    @GetMapping("/export-files/{fileName:.+}")
    public ResponseEntity<UrlResource> exportFile(@PathVariable String fileName) throws MalformedURLException {
        Path file = wrongQuestionService.exportFile(SecurityUtils.currentUserId(), fileName);
        UrlResource resource = new UrlResource(file.toUri());
        String contentType = fileName.endsWith(".csv")
                ? "text/csv;charset=UTF-8"
                : fileName.endsWith(".txt") || fileName.endsWith(".md")
                ? "text/plain;charset=UTF-8"
                : fileName.endsWith(".html")
                ? MediaType.TEXT_HTML_VALUE
                : "application/msword";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @DeleteMapping("/{wrongId}")
    public Result<Map<String, Object>> delete(@PathVariable Long wrongId) {
        Long userId = SecurityUtils.currentUserId();
        int deleted = wrongQuestionService.deleteWrongQuestion(userId, wrongId);
        userProfileService.refreshAfterLearningEvent(userId);
        return Result.success(Map.of(
                "deleted", deleted,
                "wrongId", wrongId
        ));
    }

    @DeleteMapping
    public Result<Map<String, Object>> clear(@RequestParam(required = false) String subject,
                                             @RequestParam(required = false) Integer wrongReason,
                                             @RequestParam(required = false) Integer isMastered) {
        Long userId = SecurityUtils.currentUserId();
        int deleted = wrongQuestionService.clearWrongQuestions(userId, subject, wrongReason, isMastered);
        if (deleted > 0) {
            userProfileService.refreshAfterLearningEvent(userId);
        }
        return Result.success(Map.of(
                "deleted", deleted,
                "subject", ResponseUtils.safe(subject)
        ));
    }

    private WrongQuestion getOwnedWrongQuestion(Long wrongId) {
        WrongQuestion wrongQuestion = wrongQuestionService.getById(wrongId);
        Long userId = SecurityUtils.currentUserId();
        if (wrongQuestion == null || !userId.equals(wrongQuestion.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "wrong question not found");
        }
        return wrongQuestion;
    }

    private Map<String, Object> detailMap(WrongQuestion wrongQuestion, QuestionBank question) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wrongId", wrongQuestion.getWrongId());
        data.put("questionId", wrongQuestion.getQuestionId());
        data.put("subject", question == null ? "" : ResponseUtils.safe(question.getSubject()));
        data.put("questionText", question == null ? "" : ResponseUtils.safe(question.getQuestionText()));
        data.put("options", question == null || question.getOptions() == null ? List.of() : List.of(question.getOptions().split("\\|")));
        data.put("correctAnswer", question == null ? "" : ResponseUtils.safe(question.getAnswer()));
        data.put("wrongAnswer", ResponseUtils.safe(wrongQuestion.getWrongAnswer()));
        data.put("wrongReason", wrongQuestion.getWrongReason());
        data.put("wrongCount", wrongQuestion.getWrongCount());
        data.put("analysis", question == null ? "" : ResponseUtils.safe(question.getAnalysis()));
        data.put("knowledgePoint", question == null ? "" : ResponseUtils.safe(question.getKnowledgePoint()));
        data.put("relatedResources", relatedResources(question));
        data.put("isMastered", wrongQuestion.getIsMastered());
        data.put("firstWrongTime", ResponseUtils.format(wrongQuestion.getFirstWrongTime()));
        data.put("lastReviewTime", ResponseUtils.format(wrongQuestion.getLastReviewTime()));
        data.putAll(wrongQuestionService.reviewPlan(wrongQuestion.getUserId(), wrongQuestion.getWrongId()));
        return data;
    }

    private List<Map<String, Object>> relatedResources(QuestionBank question) {
        if (question == null || question.getKnowledgePoint() == null || question.getKnowledgePoint().isBlank()) {
            return List.of();
        }
        return learningResourceService.lambdaQuery()
                .eq(question.getSubject() != null && !question.getSubject().isBlank(), LearningResource::getSubject, question.getSubject())
                .like(LearningResource::getKnowledgePoint, question.getKnowledgePoint())
                .list()
                .stream()
                .sorted((left, right) -> Integer.compare(resourcePriority(left), resourcePriority(right)))
                .limit(5)
                .map(this::resourceMap)
                .toList();
    }

    private Map<String, Object> resourceMap(LearningResource resource) {
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

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
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
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
