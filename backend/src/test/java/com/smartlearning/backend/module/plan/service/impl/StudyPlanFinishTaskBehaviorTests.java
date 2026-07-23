package com.smartlearning.backend.module.plan.service.impl;

import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.plan.entity.StudyPlan;
import com.smartlearning.backend.module.plan.entity.StudyTask;
import com.smartlearning.backend.module.plan.service.StudyTaskService;
import com.smartlearning.backend.module.qa.service.AiService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPlanFinishTaskBehaviorTests {

    @Mock
    private StudyTaskService studyTaskService;
    @Mock
    private LearningResourceService learningResourceService;
    @Mock
    private StudyRecordService studyRecordService;
    @Mock
    private AiService aiService;
    @Mock
    private WrongQuestionService wrongQuestionService;
    @Mock
    private AssessmentService assessmentService;

    @Test
    void finishTaskCreatesAStudyRecordForTheFinishedTask() {
        StudyTask task = task();
        when(studyTaskService.getById(99L)).thenReturn(task);
        StudyPlanServiceImpl service = new StudyPlanServiceImpl(
                studyTaskService,
                learningResourceService,
                studyRecordService,
                aiService,
                wrongQuestionService,
                assessmentService
        );

        Map<String, Object> result = service.finishTask(7L, 99L, Map.of(
                "finishStatus", 1,
                "studyDuration", 42,
                "correctRate", 86
        ));

        ArgumentCaptor<StudyRecord> recordCaptor = ArgumentCaptor.forClass(StudyRecord.class);
        verify(studyRecordService).save(recordCaptor.capture());
        StudyRecord record = recordCaptor.getValue();
        assertEquals(7L, record.getUserId());
        assertEquals(501L, record.getResourceId());
        assertEquals(2, record.getStudyType());
        assertEquals(42, record.getStudyDuration());
        assertEquals(1, record.getFinishStatus());
        assertNotNull(record.getStudyTime());

        verify(studyTaskService).updateById(task);
        assertEquals(1, task.getFinishStatus());
        assertEquals(new BigDecimal("86.00"), task.getCorrectRate());
        assertEquals("none", ((Map<?, ?>) result.get("adjustment")).get("action"));
        assertEquals(1, ((Map<?, ?>) result.get("task")).get("finishStatus"));
    }

    @Test
    void compactPathStepsRejectsMathStepsForPoliticsPlan() {
        StudyPlanServiceImpl service = service();
        StudyPlan plan = politicsPlan();

        List<Map<String, Object>> steps = ReflectionTestUtils.invokeMethod(service, "compactPathSteps", plan, List.of(
                Map.of("stepType", "diagnostic_test", "knowledgePoint", "函数"),
                Map.of("stepType", "practice", "knowledgePoint", "方程"),
                Map.of("stepType", "stage_test", "knowledgePoint", "几何")
        ));

        assertNotNull(steps);
        assertEquals(0, steps.size());
    }

    @Test
    void fallbackPathFiltersOutCrossSubjectWeakPointsAndKeepsFourCoreSteps() {
        StudyPlanServiceImpl service = service();
        StudyPlan plan = politicsPlan();

        Map<String, Object> path = ReflectionTestUtils.invokeMethod(service, "fallbackPath", plan, List.of("函数", "方程", "几何"), "test");

        assertNotNull(path);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) path.get("steps");
        assertEquals(4, steps.size());
        for (Map<String, Object> step : steps) {
            String point = String.valueOf(step.get("knowledgePoint"));
            assertFalse(point.contains("函数"));
            assertFalse(point.contains("方程"));
            assertFalse(point.contains("几何"));
        }
    }

    @Test
    void resourceRecommendationReplacesPlaceholderUrlWithOfficialKnowledgePointSearch() {
        StudyPlanServiceImpl service = service();
        LearningResource resource = new LearningResource();
        resource.setResourceId(501L);
        resource.setResourceName("一次函数基础讲义");
        resource.setSubject("数学");
        resource.setKnowledgePoint("一次函数");
        resource.setResourceType(2);
        resource.setFileUrl("https://example.com/smart-learning/demo/linear-function-notes.pdf");

        Map<String, Object> item = ReflectionTestUtils.invokeMethod(service, "resourceScore", resource, List.of("一次函数"), 2);

        assertNotNull(item);
        String fileUrl = String.valueOf(item.get("fileUrl"));
        assertTrue(fileUrl.startsWith("https://basic.smartedu.cn/search?keyword="));
        assertTrue(fileUrl.contains("%E5%88%9D%E4%B8%AD%E6%95%B0%E5%AD%A6"));
        assertTrue(fileUrl.contains("%E4%B8%80%E6%AC%A1%E5%87%BD%E6%95%B0"));
        assertTrue(fileUrl.contains("%E8%AF%BE%E4%BB%B6"));
    }

    private StudyTask task() {
        StudyTask task = new StudyTask();
        task.setTaskId(99L);
        task.setUserId(7L);
        task.setPlanId(22L);
        task.setTaskDate(LocalDate.now());
        task.setTaskType(2);
        task.setKnowledgePoint("function");
        task.setResourceId(501L);
        task.setEstimatedMinutes(30);
        task.setFinishStatus(0);
        task.setTargetCorrectRate(BigDecimal.valueOf(60));
        task.setPriority(1);
        return task;
    }

    private StudyPlanServiceImpl service() {
        return new StudyPlanServiceImpl(
                studyTaskService,
                learningResourceService,
                studyRecordService,
                aiService,
                wrongQuestionService,
                assessmentService
        );
    }

    private StudyPlan politicsPlan() {
        StudyPlan plan = new StudyPlan();
        plan.setSubject("道德与法治");
        plan.setTargetDesc("宪法 权利");
        plan.setStartDate(LocalDate.now());
        plan.setEndDate(LocalDate.now().plusDays(3));
        return plan;
    }
}
