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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "学习资源模块")
@RestController
@RequestMapping("/learning-resources")
public class LearningResourceController {

    private static final String SMARTEDU_SEARCH_BASE = "https://basic.smartedu.cn/search?keyword=";
    private static final Set<String> PLACEHOLDER_RESOURCE_HOSTS = Set.of("example.com", "www.example.com", "localhost", "127.0.0.1");
    private static final Map<Integer, String> RESOURCE_TYPE_KEYWORDS = Map.of(
            1, "微课",
            2, "课件",
            3, "练习",
            4, "思维导图",
            5, "考点手册"
    );

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
        sanitizeResources(page);
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
        sanitizeResources(page);
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/categories")
    public Result<Map<String, Object>> categories() {
        return Result.success(Map.of(
                "subjects", List.of("语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理", "道德与法治"),
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
        sanitizeResource(resource);
        return Result.success(resource);
    }

    private void sanitizeResources(Page<LearningResource> page) {
        if (page == null || page.getRecords() == null) {
            return;
        }
        page.getRecords().forEach(this::sanitizeResource);
    }

    private void sanitizeResource(LearningResource resource) {
        if (resource == null || !shouldReplaceWithSmartEduSearch(resource)) {
            return;
        }
        resource.setFileUrl(smartEduResourceUrl(resource));
        resource.setTextbookVersion("国家中小学智慧教育平台");
        resource.setFileSize(null);
    }

    private boolean isPlaceholderResourceUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && PLACEHOLDER_RESOURCE_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean shouldReplaceWithSmartEduSearch(LearningResource resource) {
        String url = resource.getFileUrl();
        if (isPlaceholderResourceUrl(url)) {
            return true;
        }
        if (!StringUtils.hasText(url) || !url.startsWith("https://basic.smartedu.cn")) {
            return false;
        }
        if (!url.contains("/search?keyword=")) {
            return true;
        }
        String keyword = normalizedText(searchKeyword(url));
        String point = normalizedText(firstKnowledgePoint(resource.getKnowledgePoint()));
        if (StringUtils.hasText(point) && !keyword.contains(point)) {
            return true;
        }
        return keyword.equals(normalizedText(smartEduSubjectKeyword(resource.getSubject())));
    }

    private String smartEduResourceUrl(LearningResource resource) {
        String keyword = smartEduSubjectKeyword(resource.getSubject());
        String point = firstKnowledgePoint(resource.getKnowledgePoint());
        if (StringUtils.hasText(point)) {
            keyword += point;
        }
        String typeKeyword = RESOURCE_TYPE_KEYWORDS.get(resource.getResourceType());
        if (StringUtils.hasText(typeKeyword)) {
            keyword += typeKeyword;
        }
        return SMARTEDU_SEARCH_BASE + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    private String smartEduSubjectKeyword(String subject) {
        return "初中" + (StringUtils.hasText(subject) ? subject.trim() : "学习资源");
    }

    private String firstKnowledgePoint(String knowledgePoint) {
        if (!StringUtils.hasText(knowledgePoint)) {
            return "";
        }
        return List.of(knowledgePoint.split("[、,，;；|/\\s]+"))
                .stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String searchKeyword(String url) {
        try {
            String query = URI.create(url).getRawQuery();
            if (!StringUtils.hasText(query)) {
                return "";
            }
            for (String part : query.split("&")) {
                int index = part.indexOf('=');
                if (index > 0 && "keyword".equals(part.substring(0, index))) {
                    return java.net.URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private String normalizedText(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s+", "") : "";
    }
}
