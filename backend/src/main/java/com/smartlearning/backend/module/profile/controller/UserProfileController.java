package com.smartlearning.backend.module.profile.controller;

import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "User profile module")
@RestController
@RequestMapping("/user-profiles")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/my")
    public Result<Map<String, Object>> getMyProfile() {
        return Result.success(userProfileService.overview(SecurityUtils.currentUserId(), false));
    }

    @PutMapping("/my")
    public Result<Map<String, Object>> updateMyProfile(@RequestBody Map<String, Object> request) {
        return Result.success(userProfileService.correctProfile(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/weak-points")
    public Result<List<String>> weakPoints(@RequestParam(required = false) String subject,
                                           @RequestParam(defaultValue = "5") Integer limit) {
        int safeLimit = limit == null ? 5 : limit;
        return Result.success(userProfileService.weakPoints(SecurityUtils.currentUserId(), safeLimit));
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh() {
        return Result.success(userProfileService.overview(SecurityUtils.currentUserId(), true));
    }

    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        return Result.success(userProfileService.metrics(SecurityUtils.currentUserId()));
    }

    @PostMapping("/behavior-events")
    public Result<Map<String, Object>> collectBehavior(@RequestBody Map<String, Object> request) {
        return Result.success(userProfileService.collectBehavior(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/corrections")
    public Result<Map<String, Object>> correct(@RequestBody Map<String, Object> request) {
        return Result.success(userProfileService.correctProfile(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/correction-logs")
    public Result<List<Map<String, Object>>> correctionLogs(@RequestParam(defaultValue = "20") Integer limit) {
        int safeLimit = limit == null ? 20 : limit;
        return Result.success(userProfileService.correctionLogs(SecurityUtils.currentUserId(), safeLimit));
    }

    @GetMapping("/service-summary")
    public Result<Map<String, Object>> serviceSummary(@RequestParam(defaultValue = "false") Boolean refresh) {
        return Result.success(userProfileService.overview(SecurityUtils.currentUserId(), Boolean.TRUE.equals(refresh)));
    }
}
