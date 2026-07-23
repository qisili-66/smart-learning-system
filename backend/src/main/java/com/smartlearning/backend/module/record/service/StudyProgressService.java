package com.smartlearning.backend.module.record.service;

import java.util.Map;

public interface StudyProgressService {

    Map<String, Object> durationStatistics(Long userId, String type, String startDate, String endDate);

    Map<String, Object> progressReport(Long userId, String period, String date);

    Map<String, Object> reminders(Long userId);
}
