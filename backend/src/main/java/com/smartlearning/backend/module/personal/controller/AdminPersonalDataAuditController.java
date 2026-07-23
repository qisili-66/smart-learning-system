package com.smartlearning.backend.module.personal.controller;

import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.personal.service.PersonalDataClearLogService;
import com.smartlearning.backend.module.personal.service.PersonalDataExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理员个人数据审计模块")
@RestController
@RequestMapping("/admin/audits/personal-data")
public class AdminPersonalDataAuditController {

    private final PersonalDataExportService personalDataExportService;
    private final PersonalDataClearLogService personalDataClearLogService;

    public AdminPersonalDataAuditController(PersonalDataExportService personalDataExportService,
                                            PersonalDataClearLogService personalDataClearLogService) {
        this.personalDataExportService = personalDataExportService;
        this.personalDataClearLogService = personalDataClearLogService;
    }

    @GetMapping("/export-logs")
    public Result<PageVO<Map<String, Object>>> exportLogs(@RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) Integer pageNum,
                                                          @RequestParam(required = false) Integer pageSize) {
        return Result.success(personalDataExportService.adminExportLogs(userId, status, pageNum, pageSize));
    }

    @GetMapping("/clear-logs")
    public Result<PageVO<Map<String, Object>>> clearLogs(@RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) Integer pageNum,
                                                         @RequestParam(required = false) Integer pageSize) {
        return Result.success(personalDataClearLogService.adminClearLogs(userId, pageNum, pageSize));
    }
}
