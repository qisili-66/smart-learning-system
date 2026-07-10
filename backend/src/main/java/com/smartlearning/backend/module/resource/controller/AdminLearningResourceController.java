package com.smartlearning.backend.module.resource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "管理员学习资源管理模块")
@RestController
@RequestMapping("/admin/learning-resources")
public class AdminLearningResourceController {

    private final LearningResourceService learningResourceService;

    public AdminLearningResourceController(LearningResourceService learningResourceService) {
        this.learningResourceService = learningResourceService;
    }

    @GetMapping
    public Result<PageVO<LearningResource>> list(@RequestParam(required = false) String subject,
                                                 @RequestParam(required = false) Integer resourceType,
                                                 @RequestParam(required = false) String knowledgePoint,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<LearningResource> query = new LambdaQueryWrapper<LearningResource>()
                .eq(StringUtils.hasText(subject), LearningResource::getSubject, subject)
                .eq(resourceType != null, LearningResource::getResourceType, resourceType)
                .like(StringUtils.hasText(knowledgePoint), LearningResource::getKnowledgePoint, knowledgePoint)
                .eq(status != null, LearningResource::getStatus, status)
                .orderByDesc(LearningResource::getCreateTime);
        Page<LearningResource> page = learningResourceService.page(PageUtils.page(pageNum, pageSize), query);
        return Result.success(PageVO.of(page));
    }

    @PostMapping
    public Result<LearningResource> upload(@RequestPart(required = false) MultipartFile file,
                                           @RequestParam(required = false) String resourceName,
                                           @RequestParam(required = false) Integer resourceType,
                                           @RequestParam(required = false) String subject,
                                           @RequestParam(required = false) String knowledgePoint,
                                           @RequestParam(required = false) String textbookVersion,
                                           @RequestParam(required = false) String fileUrl,
                                           @RequestParam(required = false) Integer status) {
        if (!StringUtils.hasText(resourceName) && file == null) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "resourceName不能为空");
        }
        if (resourceType == null) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "resourceType不能为空");
        }
        LearningResource resource = new LearningResource();
        resource.setResourceName(!StringUtils.hasText(resourceName) && file != null ? file.getOriginalFilename() : resourceName);
        resource.setResourceType(resourceType);
        resource.setSubject(safe(subject));
        resource.setKnowledgePoint(safe(knowledgePoint));
        resource.setTextbookVersion(safe(textbookVersion));
        resource.setFileUrl(safe(fileUrl));
        resource.setFileSize(file == null ? null : file.getSize());
        resource.setStatus(status == null ? Constants.STATUS_NORMAL : status);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());
        learningResourceService.save(resource);
        return Result.success(resource);
    }

    @PutMapping("/{resourceId}")
    public Result<LearningResource> update(@PathVariable Long resourceId, @RequestBody LearningResource request) {
        LearningResource resource = getResource(resourceId);
        request.setResourceId(resource.getResourceId());
        request.setUpdateTime(LocalDateTime.now());
        learningResourceService.updateById(request);
        return Result.success(learningResourceService.getById(resourceId));
    }

    @PutMapping("/{resourceId}/status")
    public Result<Void> status(@PathVariable Long resourceId, @RequestBody Map<String, Integer> request) {
        LearningResource resource = getResource(resourceId);
        resource.setStatus(request.get("status"));
        resource.setUpdateTime(LocalDateTime.now());
        learningResourceService.updateById(resource);
        return Result.success();
    }

    @DeleteMapping("/{resourceId}")
    public Result<Void> delete(@PathVariable Long resourceId) {
        getResource(resourceId);
        learningResourceService.removeById(resourceId);
        return Result.success();
    }

    private LearningResource getResource(Long resourceId) {
        LearningResource resource = learningResourceService.getById(resourceId);
        if (resource == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "learning resource not found");
        }
        return resource;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
