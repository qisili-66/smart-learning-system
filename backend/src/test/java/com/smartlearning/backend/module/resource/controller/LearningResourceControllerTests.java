package com.smartlearning.backend.module.resource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceControllerTests {

    @Mock
    private LearningResourceService learningResourceService;
    @Mock
    private UserProfileService userProfileService;

    @Test
    void listReplacesPlaceholderResourceUrlWithOfficialSearchUrl() {
        LearningResource resource = new LearningResource();
        resource.setResourceId(1L);
        resource.setResourceName("一次函数基础讲义");
        resource.setSubject("数学");
        resource.setKnowledgePoint("一次函数");
        resource.setResourceType(2);
        resource.setTextbookVersion("通用版");
        resource.setFileUrl("https://example.com/smart-learning/demo/linear-function-notes.pdf");
        resource.setFileSize(524288L);
        resource.setStatus(1);
        Page<LearningResource> page = new Page<>(1, 9);
        page.setRecords(List.of(resource));
        page.setTotal(1);
        when(learningResourceService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        LearningResourceController controller = new LearningResourceController(learningResourceService, userProfileService);

        Result<PageVO<LearningResource>> result = controller.list("数学", null, null, 1, 9);

        LearningResource sanitized = result.getData().getList().get(0);
        assertTrue(sanitized.getFileUrl().startsWith("https://basic.smartedu.cn/search?keyword="));
        assertTrue(sanitized.getFileUrl().contains("%E5%88%9D%E4%B8%AD%E6%95%B0%E5%AD%A6"));
        assertTrue(sanitized.getFileUrl().contains("%E4%B8%80%E6%AC%A1%E5%87%BD%E6%95%B0"));
        assertTrue(sanitized.getFileUrl().contains("%E8%AF%BE%E4%BB%B6"));
        assertEquals("国家中小学智慧教育平台", sanitized.getTextbookVersion());
        assertNull(sanitized.getFileSize());
    }

    @Test
    void listUpgradesBroadSmartEduSubjectSearchToKnowledgePointSearch() {
        LearningResource resource = new LearningResource();
        resource.setResourceId(2L);
        resource.setResourceName("初中数学电子教材 - 国家中小学智慧教育平台搜索");
        resource.setSubject("数学");
        resource.setKnowledgePoint("数与代数、图形与几何、统计概率");
        resource.setResourceType(2);
        resource.setTextbookVersion("官方平台");
        resource.setFileUrl("https://basic.smartedu.cn/search?keyword=%E5%88%9D%E4%B8%AD%E6%95%B0%E5%AD%A6");
        resource.setStatus(1);
        Page<LearningResource> page = new Page<>(1, 9);
        page.setRecords(List.of(resource));
        page.setTotal(1);
        when(learningResourceService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        LearningResourceController controller = new LearningResourceController(learningResourceService, userProfileService);

        Result<PageVO<LearningResource>> result = controller.list("数学", null, null, 1, 9);

        String fileUrl = result.getData().getList().get(0).getFileUrl();
        assertTrue(fileUrl.contains("%E6%95%B0%E4%B8%8E%E4%BB%A3%E6%95%B0"));
        assertTrue(fileUrl.contains("%E8%AF%BE%E4%BB%B6"));
    }
}
