package com.smartlearning.backend.module.resource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import com.smartlearning.backend.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "学习资源模块")
@RestController
@RequestMapping("/learning-resources")
public class LearningResourceController {

    private final LearningResourceService learningResourceService;
    private final UserProfileService userProfileService;

    public LearningResourceController(LearningResourceService learningResourceService, UserProfileService userProfileService) {
        this.learningResourceService = learningResourceService;
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public Result<PageVO<LearningResource>> list(@RequestParam(required = false) String subject,
                                                 @RequestParam(required = false) Integer resourceType,
                                                 @RequestParam(required = false) String knowledgePoint,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<LearningResource> query = new LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getStatus, Constants.STATUS_NORMAL)
                .eq(StringUtils.hasText(subject), LearningResource::getSubject, subject)
                .eq(resourceType != null, LearningResource::getResourceType, resourceType)
                .like(StringUtils.hasText(knowledgePoint), LearningResource::getKnowledgePoint, knowledgePoint)
                .orderByDesc(LearningResource::getCreateTime);
        Page<LearningResource> page = learningResourceService.page(PageUtils.page(pageNum, pageSize), query);
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/search")
    public Result<PageVO<LearningResource>> search(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String subject,
                                                   @RequestParam(required = false) Integer resourceType,
                                                   @RequestParam(required = false) Integer pageNum,
                                                   @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<LearningResource> query = new LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getStatus, Constants.STATUS_NORMAL)
                .eq(StringUtils.hasText(subject), LearningResource::getSubject, subject)
                .eq(resourceType != null, LearningResource::getResourceType, resourceType);
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(LearningResource::getResourceName, keyword)
                    .or()
                    .like(LearningResource::getKnowledgePoint, keyword)
                    .or()
                    .like(LearningResource::getTextbookVersion, keyword));
        }
        query.orderByDesc(LearningResource::getCreateTime);
        Page<LearningResource> page = learningResourceService.page(PageUtils.page(pageNum, pageSize), query);
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/categories")
    public Result<Map<String, Object>> categories() {
        return Result.success(Map.of(
                "subjects", List.of("语文", "数学", "英语"),
                "resourceTypes", List.of(
                        Map.of("value", 1, "label", "微课"),
                        Map.of("value", 2, "label", "课件"),
                        Map.of("value", 3, "label", "真题"),
                        Map.of("value", 4, "label", "思维导图"),
                        Map.of("value", 5, "label", "考点手册")
                )
        ));
    }

    @GetMapping("/{resourceId}")
    public Result<LearningResource> detail(@PathVariable Long resourceId) {
        LearningResource resource = learningResourceService.getById(resourceId);
        if (resource == null || !Constants.STATUS_NORMAL.equals(resource.getStatus())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "learning resource not found");
        }
        userProfileService.collectResourceView(SecurityUtils.currentUserId(), resourceId);
        return Result.success(resource);
    }
}
