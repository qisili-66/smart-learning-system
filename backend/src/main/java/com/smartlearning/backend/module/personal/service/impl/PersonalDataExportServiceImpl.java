package com.smartlearning.backend.module.personal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.ResponseUtils;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.entity.AssessmentAnswer;
import com.smartlearning.backend.module.assessment.service.AssessmentAnswerService;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import com.smartlearning.backend.module.personal.entity.PersonalDataExportLog;
import com.smartlearning.backend.module.personal.mapper.PersonalDataExportLogMapper;
import com.smartlearning.backend.module.personal.service.PersonalDataExportService;
import com.smartlearning.backend.module.plan.entity.StudyPlan;
import com.smartlearning.backend.module.plan.entity.StudyTask;
import com.smartlearning.backend.module.plan.service.StudyPlanService;
import com.smartlearning.backend.module.plan.service.StudyTaskService;
import com.smartlearning.backend.module.profile.entity.UserProfile;
import com.smartlearning.backend.module.profile.entity.UserProfileCorrectionLog;
import com.smartlearning.backend.module.profile.service.UserProfileCorrectionLogService;
import com.smartlearning.backend.module.profile.service.UserProfileService;
import com.smartlearning.backend.module.qa.entity.QaConversation;
import com.smartlearning.backend.module.qa.entity.QaMessage;
import com.smartlearning.backend.module.qa.service.QaConversationService;
import com.smartlearning.backend.module.qa.service.QaMessageService;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import com.smartlearning.backend.module.user.entity.SysUser;
import com.smartlearning.backend.module.user.service.SysUserService;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PersonalDataExportServiceImpl extends ServiceImpl<PersonalDataExportLogMapper, PersonalDataExportLog>
        implements PersonalDataExportService {

    private static final int QA_CONTENT_PREVIEW_LIMIT = 300;
    private static final int EXPORT_TOKEN_BYTES = 32;
    private static final int EXPORT_EXPIRE_HOURS = 24;
    private static final int EXPORT_MAX_DOWNLOAD_COUNT = 3;
    private static final int EXPORT_STATUS_ACTIVE = 1;
    private static final int EXPORT_STATUS_EXPIRED = 2;
    private static final int EXPORT_STATUS_CONSUMED = 3;
    private static final int EXPORT_STATUS_DELETED = 4;

    private final ObjectMapper objectMapper;
    private final SysUserService sysUserService;
    private final UserProfileService userProfileService;
    private final UserProfileCorrectionLogService correctionLogService;
    private final StudyRecordService studyRecordService;
    private final WrongQuestionService wrongQuestionService;
    private final AssessmentService assessmentService;
    private final AssessmentAnswerService assessmentAnswerService;
    private final StudyPlanService studyPlanService;
    private final StudyTaskService studyTaskService;
    private final QaConversationService qaConversationService;
    private final QaMessageService qaMessageService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalDataExportServiceImpl(ObjectMapper objectMapper,
                                         SysUserService sysUserService,
                                         UserProfileService userProfileService,
                                         UserProfileCorrectionLogService correctionLogService,
                                         StudyRecordService studyRecordService,
                                         WrongQuestionService wrongQuestionService,
                                         AssessmentService assessmentService,
                                         AssessmentAnswerService assessmentAnswerService,
                                         StudyPlanService studyPlanService,
                                         StudyTaskService studyTaskService,
                                         QaConversationService qaConversationService,
                                         QaMessageService qaMessageService) {
        this.objectMapper = objectMapper;
        this.sysUserService = sysUserService;
        this.userProfileService = userProfileService;
        this.correctionLogService = correctionLogService;
        this.studyRecordService = studyRecordService;
        this.wrongQuestionService = wrongQuestionService;
        this.assessmentService = assessmentService;
        this.assessmentAnswerService = assessmentAnswerService;
        this.studyPlanService = studyPlanService;
        this.studyTaskService = studyTaskService;
        this.qaConversationService = qaConversationService;
        this.qaMessageService = qaMessageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> exportData(Long userId) {
        cleanupExpiredExports();

        Map<String, Object> payload = buildPayload(userId);
        String fileName = "personal-data-" + userId + "-"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                + ".zip";
        Path filePath = writeZip(fileName, payload);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(EXPORT_EXPIRE_HOURS);
        String token = generateToken();

        PersonalDataExportLog exportLog = new PersonalDataExportLog();
        exportLog.setUserId(userId);
        exportLog.setFileName(fileName);
        exportLog.setFilePath(filePath.toAbsolutePath().toString());
        exportLog.setFileSize(fileSize(filePath));
        exportLog.setTokenHash(hashToken(token));
        exportLog.setExpiresAt(expiresAt);
        exportLog.setMaxDownloadCount(EXPORT_MAX_DOWNLOAD_COUNT);
        exportLog.setDownloadCount(0);
        exportLog.setStatus(EXPORT_STATUS_ACTIVE);
        exportLog.setCreateTime(now);
        exportLog.setUpdateTime(now);
        save(exportLog);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", "zip");
        data.put("exportId", exportLog.getExportId());
        data.put("fileName", fileName);
        data.put("downloadUrl", "/api/personal-data/export-files/" + fileName + "?token=" + token);
        data.put("filePath", filePath.toAbsolutePath().toString());
        data.put("fileSize", exportLog.getFileSize());
        data.put("generatedAt", payload.get("generatedAt"));
        data.put("expiresAt", ResponseUtils.format(expiresAt));
        data.put("maxDownloadCount", EXPORT_MAX_DOWNLOAD_COUNT);
        data.put("downloadCount", 0);
        data.put("summary", payload.get("summary"));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Path exportFile(Long userId, String fileName, String token) {
        validateFileName(fileName);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "下载链接缺少签名token");
        }

        LocalDateTime now = LocalDateTime.now();
        PersonalDataExportLog exportLog = lambdaQuery()
                .eq(PersonalDataExportLog::getUserId, userId)
                .eq(PersonalDataExportLog::getFileName, fileName)
                .eq(PersonalDataExportLog::getTokenHash, hashToken(token))
                .one();
        if (exportLog == null) {
            throw new BusinessException(Constants.CODE_FORBIDDEN, "下载链接无效");
        }
        if (safeInt(exportLog.getStatus()) != EXPORT_STATUS_ACTIVE) {
            throw new BusinessException(Constants.CODE_FORBIDDEN, "导出文件已失效");
        }
        if (exportLog.getExpiresAt() == null || !exportLog.getExpiresAt().isAfter(now)) {
            markExpired(exportLog, now);
            throw new BusinessException(Constants.CODE_FORBIDDEN, "导出文件已过期，请重新导出");
        }
        if (exportLog.getDownloadCount() != null
                && exportLog.getMaxDownloadCount() != null
                && exportLog.getDownloadCount() >= exportLog.getMaxDownloadCount()) {
            markConsumed(exportLog, now);
            throw new BusinessException(Constants.CODE_FORBIDDEN, "导出文件下载次数已用完，请重新导出");
        }

        Path file = exportPath(exportLog.getFilePath(), fileName);
        int nextDownloadCount = safeInt(exportLog.getDownloadCount()) + 1;
        exportLog.setDownloadCount(nextDownloadCount);
        exportLog.setLastDownloadTime(now);
        exportLog.setUpdateTime(now);
        if (exportLog.getMaxDownloadCount() != null && nextDownloadCount >= exportLog.getMaxDownloadCount()) {
            exportLog.setStatus(EXPORT_STATUS_CONSUMED);
        }
        updateById(exportLog);
        return file;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listExportLogs(Long userId) {
        try {
            List<Map<String, Object>> logs = lambdaQuery()
                    .eq(PersonalDataExportLog::getUserId, userId)
                    .orderByDesc(PersonalDataExportLog::getCreateTime)
                    .list()
                    .stream()
                    .map(this::toExportLogView)
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", logs.size());
            data.put("items", logs);
            return data;
        } catch (DataAccessException e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", 0);
            data.put("items", List.of());
            data.put("warning", "个人数据导出审计表暂不可用，请确认已执行最新 initial_schema.sql");
            return data;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<Map<String, Object>> adminExportLogs(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<PersonalDataExportLog> query = new LambdaQueryWrapper<PersonalDataExportLog>()
                .eq(userId != null, PersonalDataExportLog::getUserId, userId)
                .eq(status != null, PersonalDataExportLog::getStatus, status)
                .orderByDesc(PersonalDataExportLog::getCreateTime);
        Page<PersonalDataExportLog> page = page(PageUtils.page(pageNum, pageSize), query);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(this::toAdminExportLogView)
                .toList();
        return PageVO.<Map<String, Object>>builder()
                .list(records)
                .total(page.getTotal())
                .pageNum((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .pages((int) page.getPages())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cleanupExpiredExports() {
        LocalDateTime now = LocalDateTime.now();
        List<PersonalDataExportLog> expiredLogs = list(new LambdaQueryWrapper<PersonalDataExportLog>()
                .in(PersonalDataExportLog::getStatus, List.of(EXPORT_STATUS_ACTIVE, EXPORT_STATUS_CONSUMED))
                .le(PersonalDataExportLog::getExpiresAt, now));
        long deletedFiles = 0;
        for (PersonalDataExportLog log : expiredLogs) {
            if (deleteExportFile(log)) {
                deletedFiles++;
            }
            log.setStatus(EXPORT_STATUS_EXPIRED);
            log.setUpdateTime(now);
            updateById(log);
        }

        deleteOrphanExpiredFiles(now.minusHours(EXPORT_EXPIRE_HOURS));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expiredLogs", expiredLogs.size());
        data.put("deletedFiles", deletedFiles);
        data.put("cleanedAt", ResponseUtils.format(now));
        return data;
    }

    private Map<String, Object> buildPayload(Long userId) {
        List<StudyPlan> plans = studyPlanService.lambdaQuery()
                .eq(StudyPlan::getUserId, userId)
                .orderByDesc(StudyPlan::getCreateTime)
                .list();
        List<StudyTask> tasks = studyTaskService.lambdaQuery()
                .eq(StudyTask::getUserId, userId)
                .orderByDesc(StudyTask::getTaskDate)
                .orderByAsc(StudyTask::getStepOrder)
                .list();
        List<StudyRecord> records = studyRecordService.lambdaQuery()
                .eq(StudyRecord::getUserId, userId)
                .orderByDesc(StudyRecord::getStudyTime)
                .list();
        List<WrongQuestion> wrongQuestions = wrongQuestionService.lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .orderByDesc(WrongQuestion::getFirstWrongTime)
                .list();
        List<Assessment> assessments = assessmentService.lambdaQuery()
                .eq(Assessment::getUserId, userId)
                .orderByDesc(Assessment::getCreateTime)
                .list();
        List<AssessmentAnswer> answers = assessmentAnswerService.lambdaQuery()
                .eq(AssessmentAnswer::getUserId, userId)
                .orderByDesc(AssessmentAnswer::getCreateTime)
                .list();
        List<QaConversation> conversations = qaConversationService.lambdaQuery()
                .eq(QaConversation::getUserId, userId)
                .orderByDesc(QaConversation::getUpdateTime)
                .list();
        List<QaMessage> messages = qaMessageService.lambdaQuery()
                .eq(QaMessage::getUserId, userId)
                .orderByDesc(QaMessage::getCreateTime)
                .list();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportVersion", "1.1");
        payload.put("generatedAt", ResponseUtils.format(LocalDateTime.now()));
        payload.put("user", userMap(sysUserService.getById(userId)));
        payload.put("summary", summary(plans, tasks, records, wrongQuestions, assessments, answers, conversations, messages));
        payload.put("profile", userProfileService.lambdaQuery().eq(UserProfile::getUserId, userId).one());
        payload.put("profileCorrections", correctionLogService.lambdaQuery()
                .eq(UserProfileCorrectionLog::getUserId, userId)
                .orderByDesc(UserProfileCorrectionLog::getCreateTime)
                .list());
        payload.put("studyPlans", plans);
        payload.put("studyTasks", tasks);
        payload.put("studyRecords", records);
        payload.put("wrongQuestions", wrongQuestions);
        payload.put("assessments", assessments);
        payload.put("assessmentAnswers", answers);
        payload.put("qaConversations", conversations);
        payload.put("qaMessages", messages.stream().map(this::qaMessageSummary).collect(Collectors.toList()));
        return payload;
    }

    private Map<String, Object> userMap(SysUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (user == null) {
            return data;
        }
        data.put("userId", user.getUserId());
        data.put("username", ResponseUtils.safe(user.getUsername()));
        data.put("realName", ResponseUtils.safe(user.getRealName()));
        data.put("role", user.getRole());
        data.put("grade", ResponseUtils.safe(user.getGrade()));
        data.put("subject", ResponseUtils.safe(user.getSubject()));
        data.put("phone", ResponseUtils.safe(user.getPhone()));
        data.put("status", user.getStatus());
        data.put("createTime", ResponseUtils.format(user.getCreateTime()));
        data.put("updateTime", ResponseUtils.format(user.getUpdateTime()));
        return data;
    }

    private Map<String, Object> summary(List<StudyPlan> plans,
                                        List<StudyTask> tasks,
                                        List<StudyRecord> records,
                                        List<WrongQuestion> wrongQuestions,
                                        List<Assessment> assessments,
                                        List<AssessmentAnswer> answers,
                                        List<QaConversation> conversations,
                                        List<QaMessage> messages) {
        long totalDuration = records.stream()
                .map(StudyRecord::getStudyDuration)
                .filter(duration -> duration != null)
                .mapToLong(Integer::longValue)
                .sum();
        long completedTasks = tasks.stream()
                .filter(task -> Constants.STATUS_NORMAL.equals(task.getFinishStatus()))
                .count();
        long masteredWrongQuestions = wrongQuestions.stream()
                .filter(wrong -> Constants.IS_MASTERED.equals(wrong.getIsMastered()))
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studyPlanCount", plans.size());
        data.put("studyTaskCount", tasks.size());
        data.put("completedTaskCount", completedTasks);
        data.put("studyRecordCount", records.size());
        data.put("totalStudyDurationMinutes", totalDuration);
        data.put("wrongQuestionCount", wrongQuestions.size());
        data.put("masteredWrongQuestionCount", masteredWrongQuestions);
        data.put("assessmentCount", assessments.size());
        data.put("assessmentAnswerCount", answers.size());
        data.put("qaConversationCount", conversations.size());
        data.put("qaMessageCount", messages.size());
        return data;
    }

    private Map<String, Object> qaMessageSummary(QaMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("conversationId", ResponseUtils.safe(message.getConversationId()));
        data.put("role", ResponseUtils.safe(message.getRole()));
        data.put("contentType", ResponseUtils.safe(message.getContentType()));
        data.put("contentPreview", truncate(message.getContent()));
        data.put("recognizedText", truncate(message.getRecognizedText()));
        data.put("correctedText", truncate(message.getCorrectedText()));
        data.put("requiresConfirmation", message.getRequiresConfirmation());
        data.put("confirmed", message.getConfirmed());
        data.put("latencyMs", message.getLatencyMs());
        data.put("qaQualityStatus", message.getQaQualityStatus());
        data.put("model", ResponseUtils.safe(message.getModel()));
        data.put("createTime", ResponseUtils.format(message.getCreateTime()));
        return data;
    }

    private Path writeZip(String fileName, Map<String, Object> payload) {
        try {
            Path directory = exportDirectory();
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName).normalize();
            if (!file.startsWith(directory)) {
                throw new BusinessException(Constants.CODE_BAD_REQUEST, "导出文件路径错误");
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            String markdown = buildMarkdown(payload);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                writeEntry(zip, "personal-data.json", json);
                writeEntry(zip, "personal-data.md", markdown);
            }
            return file;
        } catch (JsonProcessingException e) {
            throw new BusinessException(Constants.CODE_ERROR, "生成个人数据JSON失败");
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "写入个人数据导出文件失败");
        }
    }

    private void writeEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildMarkdown(Map<String, Object> payload) {
        Map<?, ?> user = (Map<?, ?>) payload.get("user");
        Map<?, ?> summary = (Map<?, ?>) payload.get("summary");
        StringBuilder builder = new StringBuilder();
        builder.append("# 个人学习数据导出\n\n");
        builder.append("- 导出时间：").append(payload.get("generatedAt")).append("\n");
        builder.append("- 用户ID：").append(user.get("userId")).append("\n");
        builder.append("- 用户名：").append(user.get("username")).append("\n");
        builder.append("- 姓名：").append(user.get("realName")).append("\n\n");
        builder.append("## 数据摘要\n\n");
        for (Map.Entry<?, ?> entry : summary.entrySet()) {
            builder.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
        }
        builder.append("\n## 文件说明\n\n");
        builder.append("- `personal-data.json`：完整结构化数据，包含画像、学习记录、错题、测评、计划、任务和答疑摘要。\n");
        builder.append("- `personal-data.md`：当前摘要说明，便于人工快速查看。\n");
        builder.append("\n注意：导出的问答消息仅包含内容预览，避免一次性暴露过长的原始答疑内容。\n");
        return builder.toString();
    }

    private String truncate(String value) {
        String safe = ResponseUtils.safe(value);
        if (safe.length() <= QA_CONTENT_PREVIEW_LIMIT) {
            return safe;
        }
        return safe.substring(0, QA_CONTENT_PREVIEW_LIMIT) + "...";
    }

    private void validateFileName(String fileName) {
        if (!StringUtils.hasText(fileName) || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "fileName参数错误");
        }
    }

    private Path exportPath(String savedPath, String fileName) {
        try {
            Path directory = exportDirectory();
            Path file = StringUtils.hasText(savedPath)
                    ? Paths.get(savedPath).toAbsolutePath().normalize()
                    : directory.resolve(fileName).normalize();
            if (!file.startsWith(directory) || !Files.exists(file)) {
                throw new BusinessException(Constants.CODE_NOT_FOUND, "导出文件不存在");
            }
            return file;
        } catch (InvalidPathException e) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "导出文件路径错误");
        }
    }

    private long fileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "读取导出文件大小失败");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[EXPORT_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ResponseUtils.safe(token).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(Constants.CODE_ERROR, "生成下载签名失败");
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void markExpired(PersonalDataExportLog exportLog, LocalDateTime now) {
        exportLog.setStatus(EXPORT_STATUS_EXPIRED);
        exportLog.setUpdateTime(now);
        updateById(exportLog);
        deleteExportFile(exportLog);
    }

    private void markConsumed(PersonalDataExportLog exportLog, LocalDateTime now) {
        exportLog.setStatus(EXPORT_STATUS_CONSUMED);
        exportLog.setUpdateTime(now);
        updateById(exportLog);
    }

    private boolean deleteExportFile(PersonalDataExportLog exportLog) {
        try {
            Path file = exportPath(exportLog.getFilePath(), exportLog.getFileName());
            return Files.deleteIfExists(file);
        } catch (BusinessException e) {
            return false;
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "删除过期个人数据导出文件失败");
        }
    }

    private void deleteOrphanExpiredFiles(LocalDateTime cutoff) {
        Path directory = exportDirectory();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> targets = paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith("personal-data-"))
                    .filter(file -> file.getFileName().toString().endsWith(".zip"))
                    .filter(file -> isOlderThan(file, cutoff))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "清理过期个人数据导出文件失败");
        }
    }

    private boolean isOlderThan(Path file, LocalDateTime cutoff) {
        try {
            LocalDateTime modifiedTime = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), java.time.ZoneId.systemDefault());
            return modifiedTime.isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, Object> toExportLogView(PersonalDataExportLog log) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("exportId", log.getExportId());
        item.put("fileName", log.getFileName());
        item.put("fileSize", log.getFileSize());
        item.put("expiresAt", ResponseUtils.format(log.getExpiresAt()));
        item.put("maxDownloadCount", log.getMaxDownloadCount());
        item.put("downloadCount", log.getDownloadCount());
        item.put("lastDownloadTime", ResponseUtils.format(log.getLastDownloadTime()));
        item.put("status", statusText(log.getStatus()));
        item.put("createTime", ResponseUtils.format(log.getCreateTime()));
        item.put("updateTime", ResponseUtils.format(log.getUpdateTime()));
        return item;
    }

    private Map<String, Object> toAdminExportLogView(PersonalDataExportLog log) {
        Map<String, Object> item = toExportLogView(log);
        item.put("userId", log.getUserId());
        return item;
    }

    private String statusText(Integer status) {
        if (EXPORT_STATUS_ACTIVE == safeInt(status)) {
            return "active";
        }
        if (EXPORT_STATUS_EXPIRED == safeInt(status)) {
            return "expired";
        }
        if (EXPORT_STATUS_CONSUMED == safeInt(status)) {
            return "consumed";
        }
        if (EXPORT_STATUS_DELETED == safeInt(status)) {
            return "deleted";
        }
        return "unknown";
    }

    private Path exportDirectory() {
        return Paths.get("exports", "personal-data").toAbsolutePath().normalize();
    }
}
