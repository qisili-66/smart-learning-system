package com.smartlearning.backend.module.wrong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;

import java.util.List;
import java.util.Map;

public interface WrongQuestionService extends IService<WrongQuestion> {

    Map<String, Object> collectWrongAnswer(Long userId, Long questionId, String wrongAnswer, Integer wrongReason);

    List<Map<String, Object>> collectFromAnswers(Long userId, List<Map<String, Object>> answers);

    PageVO<Map<String, Object>> similarQuestions(Long userId, Long wrongId, Integer limit);

    Map<String, Object> statistics(Long userId, String subject);

    Map<String, Object> exportBook(Long userId, String subject, Integer isMastered, String format);

    Map<String, Object> updateReviewPlan(Long userId, Long wrongId, Map<String, Object> request);

    Map<String, Object> reviewPlan(Long userId, Long wrongId);

    void rescheduleReview(Long userId, Long wrongId, boolean mastered);

    int deleteWrongQuestion(Long userId, Long wrongId);

    int clearWrongQuestions(Long userId, String subject, Integer wrongReason, Integer isMastered);

    java.nio.file.Path exportFile(Long userId, String fileName);
}
