package com.smartlearning.backend.module.qa.controller;

import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.qa.dto.TextQARequest;
import com.smartlearning.backend.module.qa.service.QaConversationService;
import com.smartlearning.backend.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/qa")
@Tag(name = "智能答疑")
@Slf4j
public class QaController {

    private final QaConversationService qaConversationService;
    private final UserProfileService userProfileService;

    public QaController(QaConversationService qaConversationService, UserProfileService userProfileService) {
        this.qaConversationService = qaConversationService;
        this.userProfileService = userProfileService;
    }

    @Operation(summary = "文本智能答疑")
    @PostMapping("/text")
    public Result<?> textQa(@Valid @RequestBody TextQARequest request) {
        Result<?> result = qaConversationService.textQuestionAnswer(SecurityUtils.currentUserId(), request);
        collectQaEvent();
        return result;
    }

    @Operation(summary = "图片OCR智能答疑")
    @PostMapping("/image")
    public Result<?> imageQa(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Boolean confirmAnswer
    ) {
        Result<?> result = qaConversationService.imageQuestionAnswer(
                SecurityUtils.currentUserId(), file, conversationId, subject, confirmAnswer);
        collectQaEvent();
        return result;
    }

    @Operation(summary = "语音智能答疑")
    @PostMapping("/voice")
    public Result<?> voiceQa(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String recognizedText,
            @RequestParam(required = false) String correctedText,
            @RequestParam(required = false) Boolean confirmAnswer
    ) {
        Result<?> result = qaConversationService.voiceQuestionAnswer(
                SecurityUtils.currentUserId(),
                file,
                conversationId,
                subject,
                recognizedText,
                correctedText,
                confirmAnswer
        );
        collectQaEvent();
        return result;
    }

    @Operation(summary = "答疑会话列表")
    @GetMapping("/conversations")
    public Result<?> conversations(@RequestParam(required = false) Integer pageNum,
                                   @RequestParam(required = false) Integer pageSize) {
        return Result.success(qaConversationService.conversations(SecurityUtils.currentUserId(), pageNum, pageSize));
    }

    @Operation(summary = "答疑会话详情")
    @GetMapping("/conversations/{conversationId}")
    public Result<Map<String, Object>> conversationDetail(@PathVariable String conversationId) {
        return Result.success(qaConversationService.detail(SecurityUtils.currentUserId(), conversationId));
    }

    @Operation(summary = "删除答疑会话")
    @DeleteMapping("/conversations/{conversationId}")
    public Result<?> deleteConversation(@PathVariable String conversationId) {
        qaConversationService.deleteConversation(SecurityUtils.currentUserId(), conversationId);
        return Result.success();
    }

    @Operation(summary = "答疑验收指标")
    @GetMapping("/evaluation")
    public Result<Map<String, Object>> evaluation(@RequestParam(required = false) Integer days) {
        return Result.success(qaConversationService.evaluation(SecurityUtils.currentUserId(), days));
    }

    @Operation(summary = "语音回放文件")
    @GetMapping("/audio/{conversationId}/{fileName}")
    public ResponseEntity<Resource> audio(@PathVariable String conversationId,
                                          @PathVariable String fileName) throws MalformedURLException {
        Path file = qaConversationService.audioFile(SecurityUtils.currentUserId(), conversationId, fileName);
        Resource resource = new UrlResource(file.toUri());
        String contentType = probeContentType(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }

    private void collectQaEvent() {
        try {
            userProfileService.collectQaInteraction(SecurityUtils.currentUserId());
        } catch (RuntimeException e) {
            log.warn("Collect QA interaction for profile failed.", e);
        }
    }

    private String probeContentType(Path file) {
        try {
            String contentType = Files.probeContentType(file);
            return contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
