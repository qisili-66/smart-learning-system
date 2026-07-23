package com.smartlearning.backend.module.assessment.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.entity.AssessmentAnswer;
import com.smartlearning.backend.module.assessment.service.AssessmentAnswerService;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.qa.service.AiService;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentSubmitBehaviorTests {

    private static final long USER_ID = 7L;
    private static final long ASSESSMENT_ID = 31L;

    @Mock
    private AssessmentService assessmentService;
    @Mock
    private AssessmentAnswerService assessmentAnswerService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private WrongQuestionService wrongQuestionService;
    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private AiService aiService;
    @Mock
    private LambdaQueryChainWrapper<QuestionBank> questionQuery;
    @Mock
    private LambdaQueryChainWrapper<Assessment> assessmentQuery;
    @Mock
    private LambdaQueryChainWrapper<AssessmentAnswer> answerQuery;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null));
        controller = new AssessmentController(
                assessmentService,
                assessmentAnswerService,
                userProfileService,
                wrongQuestionService,
                questionBankService,
                aiService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitCollectsOnlyAutomaticallyWrongAnswersIntoWrongQuestionBook() {
        Assessment assessment = assessment();
        when(assessmentService.getById(ASSESSMENT_ID)).thenReturn(assessment);
        when(questionBankService.lambdaQuery()).thenReturn(questionQuery);
        when(questionQuery.eq(anyBoolean(), any(), any())).thenReturn(questionQuery);
        when(questionQuery.list()).thenReturn(List.of(correctQuestion(), wrongQuestion()));
        when(wrongQuestionService.collectFromAnswers(eq(USER_ID), any())).thenReturn(List.of(Map.of(
                "questionId", 102L,
                "action", "created"
        )));

        Result<Map<String, Object>> result = controller.submit(ASSESSMENT_ID, Map.of(
                "answers", List.of(
                        Map.of("questionId", 101L, "userAnswer", "A", "questionUseSeconds", 12),
                        Map.of("questionId", 102L, "userAnswer", "A", "questionUseSeconds", 20)
                )
        ));

        ArgumentCaptor<List<Map<String, Object>>> wrongCandidates = ArgumentCaptor.forClass(List.class);
        verify(wrongQuestionService).collectFromAnswers(eq(USER_ID), wrongCandidates.capture());
        assertEquals(1, wrongCandidates.getValue().size());
        assertEquals(102L, wrongCandidates.getValue().get(0).get("questionId"));
        assertEquals("A", wrongCandidates.getValue().get(0).get("userAnswer"));

        ArgumentCaptor<List<AssessmentAnswer>> savedAnswers = ArgumentCaptor.forClass(List.class);
        verify(assessmentAnswerService).saveBatch(savedAnswers.capture());
        assertEquals(2, savedAnswers.getValue().size());
        assertEquals(1, savedAnswers.getValue().get(0).getIsCorrect());
        assertEquals(0, savedAnswers.getValue().get(1).getIsCorrect());

        assertEquals(Constants.CODE_SUCCESS, result.getCode());
        assertEquals(new BigDecimal("50.00"), result.getData().get("userScore"));
        assertEquals(new BigDecimal("100.00"), result.getData().get("totalScore"));
        assertFalse(((List<?>) result.getData().get("wrongQuestions")).isEmpty());
        assertTrue(result.getData().containsKey("correctRate"));
        verify(userProfileService).refreshAfterLearningEvent(USER_ID);
    }

    @Test
    void detailDeduplicatesQuestionsAndAvoidsRecentlyUsedQuestionsWhenEnoughFreshItemsExist() {
        Assessment assessment = assessment();
        assessment.setAssessmentId(32L);
        Assessment recentAssessment = assessment();
        recentAssessment.setAssessmentId(30L);
        recentAssessment.setAssessmentStatus(2);
        when(assessmentService.getById(32L)).thenReturn(assessment);
        when(questionBankService.lambdaQuery()).thenReturn(questionQuery);
        when(questionQuery.eq(anyBoolean(), any(), any())).thenReturn(questionQuery);
        when(questionQuery.list()).thenReturn(List.of(
                question(101L),
                question(101L),
                question(102L),
                question(103L),
                question(104L),
                question(105L),
                question(106L)
        ));
        when(assessmentService.lambdaQuery()).thenReturn(assessmentQuery);
        when(assessmentQuery.eq(any(), any())).thenReturn(assessmentQuery);
        when(assessmentQuery.ne(anyBoolean(), any(), any())).thenReturn(assessmentQuery);
        when(assessmentQuery.orderByDesc((SFunction<Assessment, ?>) any())).thenReturn(assessmentQuery);
        when(assessmentQuery.last(any())).thenReturn(assessmentQuery);
        when(assessmentQuery.list()).thenReturn(List.of(recentAssessment));
        when(assessmentAnswerService.lambdaQuery()).thenReturn(answerQuery);
        when(answerQuery.eq(any(), any())).thenReturn(answerQuery);
        when(answerQuery.in((SFunction<AssessmentAnswer, ?>) any(), (Collection<?>) any())).thenReturn(answerQuery);
        AssessmentAnswer recentAnswer = answer(101L);
        when(answerQuery.list()).thenReturn(List.of(recentAnswer));

        Result<Map<String, Object>> result = controller.detail(32L);

        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.getData().get("questions");
        Set<Object> questionIds = questions.stream()
                .map(question -> question.get("questionId"))
                .collect(Collectors.toSet());
        assertEquals(5, questions.size());
        assertEquals(5, questionIds.size());
        assertFalse(questionIds.contains(101L));
    }

    @Test
    void detailKeepsStandardMathPaperSectionsAlignedWithQuestionTypes() {
        Assessment assessment = assessment();
        assessment.setAssessmentId(33L);
        assessment.setSubject("数学");
        assessment.setKnowledgeScope("不等式");
        assessment.setTotalScore(BigDecimal.valueOf(120));
        when(assessmentService.getById(33L)).thenReturn(assessment);
        when(questionBankService.lambdaQuery()).thenReturn(questionQuery);
        when(questionQuery.eq(anyBoolean(), any(), any())).thenReturn(questionQuery);
        when(questionQuery.list()).thenReturn(
                List.of(standardQuestion(201L, 1, "不等式选择题 1")),
                standardMathQuestions()
        );

        Result<Map<String, Object>> result = controller.detail(33L);

        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.getData().get("questions");
        assertEquals(23, questions.size());
        assertEquals(23, questions.stream().map(question -> question.get("questionText")).collect(Collectors.toSet()).size());
        assertSectionTypes(questions, "一、单项选择题", Set.of(1));
        assertSectionTypes(questions, "二、填空题", Set.of(3));
        assertSectionTypes(questions, "三、基础解答题", Set.of(4));
        assertSectionTypes(questions, "四、中档解答题", Set.of(4));
        assertSectionTypes(questions, "五、压轴大题", Set.of(4));
        BigDecimal scoreSum = questions.stream()
                .map(question -> (BigDecimal) question.get("maxScore"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("120.00"), scoreSum.setScale(2));
    }

    private Assessment assessment() {
        Assessment assessment = new Assessment();
        assessment.setAssessmentId(ASSESSMENT_ID);
        assessment.setUserId(USER_ID);
        assessment.setAssessmentType(1);
        assessment.setSubject("math");
        assessment.setKnowledgeScope("function");
        assessment.setDifficulty(2);
        assessment.setTotalScore(BigDecimal.valueOf(100));
        assessment.setAssessmentStatus(1);
        assessment.setCreateTime(LocalDateTime.now());
        return assessment;
    }

    private QuestionBank correctQuestion() {
        QuestionBank question = new QuestionBank();
        question.setQuestionId(101L);
        question.setSubject("math");
        question.setKnowledgePoint("function");
        question.setDifficulty(2);
        question.setQuestionType(1);
        question.setQuestionText("Pick A");
        question.setAnswer("A");
        return question;
    }

    private QuestionBank wrongQuestion() {
        QuestionBank question = new QuestionBank();
        question.setQuestionId(102L);
        question.setSubject("math");
        question.setKnowledgePoint("function");
        question.setDifficulty(2);
        question.setQuestionType(1);
        question.setQuestionText("Pick B");
        question.setAnswer("B");
        return question;
    }

    private QuestionBank question(Long questionId) {
        QuestionBank question = new QuestionBank();
        question.setQuestionId(questionId);
        question.setSubject("math");
        question.setKnowledgePoint("function");
        question.setDifficulty(2);
        question.setQuestionType(1);
        question.setQuestionText("Pick " + questionId);
        question.setAnswer("A");
        return question;
    }

    private List<QuestionBank> standardMathQuestions() {
        List<QuestionBank> questions = new java.util.ArrayList<>();
        long id = 201L;
        for (int index = 1; index <= 10; index++) {
            questions.add(standardQuestion(id++, 1, "数学选择题 " + index));
        }
        for (int index = 1; index <= 5; index++) {
            questions.add(standardQuestion(id++, 3, "数学填空题 " + index));
        }
        for (int index = 1; index <= 8; index++) {
            questions.add(standardQuestion(id++, 4, "数学解答题 " + index));
        }
        return questions;
    }

    private QuestionBank standardQuestion(Long questionId, Integer questionType, String text) {
        QuestionBank question = new QuestionBank();
        question.setQuestionId(questionId);
        question.setSubject("数学");
        question.setKnowledgePoint("不等式");
        question.setDifficulty(2);
        question.setQuestionType(questionType);
        question.setQuestionText(text);
        question.setOptions(Integer.valueOf(1).equals(questionType) ? "A|B|C|D" : "");
        question.setAnswer(Integer.valueOf(1).equals(questionType) ? "A" : "参考答案");
        return question;
    }

    private void assertSectionTypes(List<Map<String, Object>> questions, String section, Set<Integer> expectedTypes) {
        List<Map<String, Object>> items = questions.stream()
                .filter(question -> section.equals(question.get("paperSectionTitle")))
                .toList();
        assertFalse(items.isEmpty(), section + " should have questions");
        assertEquals(expectedTypes, items.stream()
                .map(question -> (Integer) question.get("questionType"))
                .collect(Collectors.toSet()));
    }

    private AssessmentAnswer answer(Long questionId) {
        AssessmentAnswer answer = new AssessmentAnswer();
        answer.setUserId(USER_ID);
        answer.setAssessmentId(30L);
        answer.setQuestionId(questionId);
        return answer;
    }
}
