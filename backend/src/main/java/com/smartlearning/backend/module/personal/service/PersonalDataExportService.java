package com.smartlearning.backend.module.personal.service;

import com.smartlearning.backend.common.PageVO;

import java.nio.file.Path;
import java.util.Map;

public interface PersonalDataExportService {

    Map<String, Object> exportData(Long userId);

    Path exportFile(Long userId, String fileName, String token);

    Map<String, Object> listExportLogs(Long userId);

    PageVO<Map<String, Object>> adminExportLogs(Long userId, Integer status, Integer pageNum, Integer pageSize);

    Map<String, Object> cleanupExpiredExports();
}
