package com.smartlearning.backend.module.personal.task;

import com.smartlearning.backend.module.personal.service.PersonalDataExportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PersonalDataExportCleanupTask {

    private final PersonalDataExportService personalDataExportService;

    public PersonalDataExportCleanupTask(PersonalDataExportService personalDataExportService) {
        this.personalDataExportService = personalDataExportService;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 10 * 60 * 1000L)
    public void cleanupExpiredExports() {
        personalDataExportService.cleanupExpiredExports();
    }
}
