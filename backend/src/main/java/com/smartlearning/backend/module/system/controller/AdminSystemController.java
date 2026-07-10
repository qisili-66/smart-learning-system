package com.smartlearning.backend.module.system.controller;

import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "管理员系统运维模块")
@RestController
@RequestMapping("/admin/system")
public class AdminSystemController {

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString(),
                "uptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime()
        ));
    }

    @GetMapping("/logs")
    public Result<PageVO<Map<String, Object>>> logs(@RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        return Result.success(PageVO.empty(pageNum, pageSize));
    }

    @GetMapping("/faults")
    public Result<PageVO<Map<String, Object>>> faults(@RequestParam(required = false) Integer pageNum,
                                                      @RequestParam(required = false) Integer pageSize) {
        return Result.success(PageVO.empty(pageNum, pageSize));
    }

    @PostMapping("/backup")
    public Result<Map<String, Object>> backup() {
        return Result.success(Map.of("backupId", System.currentTimeMillis(), "status", "created"));
    }
}
