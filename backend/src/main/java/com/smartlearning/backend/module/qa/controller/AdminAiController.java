package com.smartlearning.backend.module.qa.controller;

import com.smartlearning.backend.common.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理员AI模型管理模块")
@RestController
@RequestMapping("/admin/ai")
public class AdminAiController {

    @GetMapping("/models")
    public Result<List<Map<String, Object>>> models() {
        return Result.success(List.of(Map.of("modelName", "default", "version", "v1", "status", "placeholder")));
    }

    @PutMapping("/qa-rules")
    public Result<Map<String, Object>> qaRules(@RequestBody Map<String, Object> request) {
        return Result.success(request);
    }

    @PutMapping("/recommend-config")
    public Result<Map<String, Object>> recommendConfig(@RequestBody Map<String, Object> request) {
        return Result.success(request);
    }
}
