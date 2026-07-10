package com.smartlearning.backend.module.plan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlearning.backend.module.plan.entity.StudyPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface StudyPlanService extends IService<StudyPlan> {

    Map<String, Object> recommendResources(Long userId,
                                            String subject,
                                            Integer limit,
                                            List<String> weakPoints,
                                            Map<String, Object> metrics);

    Map<String, Object> dailyTasks(Long userId,
                                   Long planId,
                                   LocalDate date,
                                   List<String> weakPoints,
                                   Map<String, Object> metrics);

    Map<String, Object> finishTask(Long userId, Long taskId, Map<String, Object> request);

    Map<String, Object> createTargetPlan(Long userId, Map<String, Object> request);

    Map<String, Object> planPath(Long userId, Long planId);

    Map<String, Object> adjustPlan(Long userId,
                                   Long planId,
                                   Map<String, Object> metrics,
                                   List<String> weakPoints);
}
