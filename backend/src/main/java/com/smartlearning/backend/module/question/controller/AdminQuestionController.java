package com.smartlearning.backend.module.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "管理员题库管理模块")
@RestController
@RequestMapping("/admin/questions")
public class AdminQuestionController {

    private final QuestionBankService questionBankService;

    public AdminQuestionController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @GetMapping
    public Result<PageVO<QuestionBank>> list(@RequestParam(required = false) String subject,
                                             @RequestParam(required = false) Integer difficulty,
                                             @RequestParam(required = false) Integer questionType,
                                             @RequestParam(required = false) Integer pageNum,
                                             @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<QuestionBank> query = new LambdaQueryWrapper<QuestionBank>()
                .eq(StringUtils.hasText(subject), QuestionBank::getSubject, subject)
                .eq(difficulty != null, QuestionBank::getDifficulty, difficulty)
                .eq(questionType != null, QuestionBank::getQuestionType, questionType)
                .orderByDesc(QuestionBank::getCreateTime);
        Page<QuestionBank> page = questionBankService.page(PageUtils.page(pageNum, pageSize), query);
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/{questionId}")
    public Result<QuestionBank> detail(@PathVariable Long questionId) {
        return Result.success(getQuestion(questionId));
    }

    @PostMapping
    public Result<QuestionBank> create(@RequestBody QuestionBank question) {
        question.setCreateTime(LocalDateTime.now());
        question.setUpdateTime(LocalDateTime.now());
        questionBankService.save(question);
        return Result.success(question);
    }

    @PutMapping("/{questionId}")
    public Result<QuestionBank> update(@PathVariable Long questionId, @RequestBody QuestionBank request) {
        getQuestion(questionId);
        request.setQuestionId(questionId);
        request.setUpdateTime(LocalDateTime.now());
        questionBankService.updateById(request);
        return Result.success(questionBankService.getById(questionId));
    }

    @DeleteMapping("/{questionId}")
    public Result<Void> delete(@PathVariable Long questionId) {
        getQuestion(questionId);
        questionBankService.removeById(questionId);
        return Result.success();
    }

    @PostMapping("/batch-import")
    public Result<Map<String, Object>> batchImport(@RequestPart(required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "file不能为空");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!fileName.endsWith(".csv") && !fileName.endsWith(".tsv") && !fileName.endsWith(".txt")) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "暂支持CSV/TSV文本导入");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String delimiter = fileName.endsWith(".tsv") ? "\t" : ",";
        List<QuestionBank> questions = new ArrayList<>();
        int skipped = 0;
        for (String line : content.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            List<String> cols = splitLine(line, delimiter.charAt(0));
            if (isHeader(cols)) {
                continue;
            }
            if (cols.size() < 7) {
                skipped++;
                continue;
            }
            QuestionBank question = new QuestionBank();
            question.setSubject(cols.get(0).trim());
            question.setKnowledgePoint(value(cols, 1));
            question.setDifficulty(toInteger(value(cols, 2), 1));
            question.setQuestionType(toInteger(value(cols, 3), 1));
            question.setQuestionText(value(cols, 4));
            question.setOptions(value(cols, 5));
            question.setAnswer(value(cols, 6));
            question.setAnalysis(value(cols, 7));
            question.setScoringPoints(value(cols, 8));
            question.setCreateTime(LocalDateTime.now());
            question.setUpdateTime(LocalDateTime.now());
            if (StringUtils.hasText(question.getSubject())
                    && StringUtils.hasText(question.getQuestionText())
                    && StringUtils.hasText(question.getAnswer())) {
                questions.add(question);
            } else {
                skipped++;
            }
        }
        if (!questions.isEmpty()) {
            questionBankService.saveBatch(questions);
        }
        return Result.success(Map.of("imported", questions.size(), "skipped", skipped, "fileName", fileName));
    }

    private QuestionBank getQuestion(Long questionId) {
        QuestionBank question = questionBankService.getById(questionId);
        if (question == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "question not found");
        }
        return question;
    }

    private List<String> splitLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == delimiter && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private boolean isHeader(List<String> cols) {
        if (cols.isEmpty()) {
            return false;
        }
        String first = cols.get(0).trim().toLowerCase();
        return "subject".equals(first) || "学科".equals(first);
    }

    private String value(List<String> cols, int index) {
        return index < cols.size() ? cols.get(index).trim() : "";
    }

    private Integer toInteger(String value, Integer defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
