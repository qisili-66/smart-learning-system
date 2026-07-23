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
import com.smartlearning.backend.module.personal.entity.PersonalDataClearLog;
import com.smartlearning.backend.module.personal.mapper.PersonalDataClearLogMapper;
import com.smartlearning.backend.module.personal.service.PersonalDataClearLogService;
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
import com.smartlearning.backend.module.user.service.SysUserService;
import com.smartlearning.backend.module.wrong.entity.WrongQuestion;
import com.smartlearning.backend.module.wrong.entity.WrongQuestionReviewPlan;
import com.smartlearning.backend.module.wrong.service.WrongQuestionReviewPlanService;
import com.smartlearning.backend.module.wrong.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class PersonalDataClearLogServiceImpl extends ServiceImpl<PersonalDataClearLogMapper, PersonalDataClearLog>
        implements PersonalDataClearLogService {

    private static final String CONFIRM_CLEAR_TEXT = "CLEAR_PERSONAL_DATA";
    private static final String CONFIRM_CLEAR_TEXT_CN = "清空个人数据";
    private static final String CLEAR_SCOPE = "profile,profileCorrections,studyPlans,studyTasks,studyRecords,wrongQuestions,wrongQuestionReviewPlans,assessments,assessmentAnswers,qaConversations,qaMessages,exportFiles,qaAudioFiles";

    private final ObjectMapper objectMapper;
    private final SysUserService sysUserService;
    private final UserProfileService userProfileService;
    private final UserProfileCorrectionLogService correctionLogService;
    private final StudyPlanService studyPlanService;
    private final StudyTaskService studyTaskService;
    private final StudyRecordService studyRecordService;
    private final WrongQuestionService wrongQuestionService;
    private final WrongQuestionReviewPlanService reviewPlanService;
    private final AssessmentService assessmentService;
    private final AssessmentAnswerService assessmentAnswerService;
    private final QaConversationService qaConversationService;
    private final QaMessageService qaMessageService;

    public PersonalDataClearLogServiceImpl(ObjectMapper objectMapper,
                                           SysUserService sysUserService,
                                           UserProfileService userProfileService,
                                           UserProfileCorrectionLogService correctionLogService,
                                           StudyPlanService studyPlanService,
                                           StudyTaskService studyTaskService,
                                           StudyRecordService studyRecordService,
                                           WrongQuestionService wrongQuestionService,
                                           WrongQuestionReviewPlanService reviewPlanService,
                                           AssessmentService assessmentService,
                                           AssessmentAnswerService assessmentAnswerService,
                                           QaConversationService qaConversationService,
                                           QaMessageService qaMessageService) {
        this.objectMapper = objectMapper;
        this.sysUserService = sysUserService;
        this.userProfileService = userProfileService;
        this.correctionLogService = correctionLogService;
        this.studyPlanService = studyPlanService;
        this.studyTaskService = studyTaskService;
        this.studyRecordService = studyRecordService;
        this.wrongQuestionService = wrongQuestionService;
        this.reviewPlanService = reviewPlanService;
        this.assessmentService = assessmentService;
        this.assessmentAnswerService = assessmentAnswerService;
        this.qaConversationService = qaConversationService;
        this.qaMessageService = qaMessageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clearPersonalData(Long userId, Map<String, String> request) {
        validateClearRequest(userId, request);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("exportFiles", deletePersonalDataExportFiles(userId));
        counts.put("qaAudioFiles", deleteQaAudioFiles(userId));

        counts.put("qaMessages", removeQaMessages(userId));
        counts.put("qaConversations", removeQaConversations(userId));
        counts.put("assessmentAnswers", removeAssessmentAnswers(userId));
        counts.put("assessments", removeAssessments(userId));
        counts.put("wrongQuestionReviewPlans", removeWrongQuestionReviewPlans(userId));
        counts.put("wrongQuestions", removeWrongQuestions(userId));
        counts.put("studyRecords", removeStudyRecords(userId));
        counts.put("studyTasks", removeStudyTasks(userId));
        counts.put("studyPlans", removeStudyPlans(userId));
        counts.put("profileCorrections", removeProfileCorrections(userId));
        counts.put("profiles", removeProfiles(userId));

        PersonalDataClearLog auditLog = new PersonalDataClearLog();
        auditLog.setUserId(userId);
        auditLog.setClearScope(CLEAR_SCOPE);
        auditLog.setConfirmationText(ResponseUtils.safe(confirmText(request)));
        auditLog.setCountsJson(toJson(counts));
        auditLog.setCreateTime(LocalDateTime.now());
        save(auditLog);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cleared", true);
        data.put("clearLogId", auditLog.getLogId());
        data.put("clearScope", CLEAR_SCOPE);
        data.put("counts", counts);
        data.put("clearedAt", ResponseUtils.format(auditLog.getCreateTime()));
        data.put("retained", List.of("account", "password", "questionBank", "learningResources", "personalDataClearLogs"));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listClearLogs(Long userId) {
        try {
            List<Map<String, Object>> logs = lambdaQuery()
                    .eq(PersonalDataClearLog::getUserId, userId)
                    .orderByDesc(PersonalDataClearLog::getCreateTime)
                    .list()
                    .stream()
                    .map(this::toLogView)
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", logs.size());
            data.put("items", logs);
            return data;
        } catch (DataAccessException e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", 0);
            data.put("items", List.of());
            data.put("warning", "个人数据清空审计表暂不可用，请确认已执行最新 initial_schema.sql");
            return data;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<Map<String, Object>> adminClearLogs(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<PersonalDataClearLog> query = new LambdaQueryWrapper<PersonalDataClearLog>()
                .eq(userId != null, PersonalDataClearLog::getUserId, userId)
                .orderByDesc(PersonalDataClearLog::getCreateTime);
        Page<PersonalDataClearLog> page = page(PageUtils.page(pageNum, pageSize), query);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(this::toAdminLogView)
                .toList();
        return PageVO.<Map<String, Object>>builder()
                .list(records)
                .total(page.getTotal())
                .pageNum((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .pages((int) page.getPages())
                .build();
    }

    private void validateClearRequest(Long userId, Map<String, String> request) {
        if (request == null) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "清空个人数据需要提交密码和二次确认文本");
        }
        if (!sysUserService.verifyPassword(userId, request.get("password"))) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "密码错误");
        }
        String confirmation = confirmText(request);
        if (!CONFIRM_CLEAR_TEXT.equals(confirmation) && !CONFIRM_CLEAR_TEXT_CN.equals(confirmation)) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST,
                    "请在 confirmText 中输入 CLEAR_PERSONAL_DATA 或 清空个人数据 进行二次确认");
        }
    }

    private String confirmText(Map<String, String> request) {
        return firstText(request.get("confirmText"), request.get("confirmation"), request.get("confirmPhrase"));
    }

    private long removeQaMessages(Long userId) {
        long count = qaMessageService.count(new LambdaQueryWrapper<QaMessage>().eq(QaMessage::getUserId, userId));
        qaMessageService.remove(new LambdaQueryWrapper<QaMessage>().eq(QaMessage::getUserId, userId));
        return count;
    }

    private long removeQaConversations(Long userId) {
        long count = qaConversationService.count(new LambdaQueryWrapper<QaConversation>().eq(QaConversation::getUserId, userId));
        qaConversationService.remove(new LambdaQueryWrapper<QaConversation>().eq(QaConversation::getUserId, userId));
        return count;
    }

    private long removeAssessmentAnswers(Long userId) {
        long count = assessmentAnswerService.count(new LambdaQueryWrapper<AssessmentAnswer>().eq(AssessmentAnswer::getUserId, userId));
        assessmentAnswerService.remove(new LambdaQueryWrapper<AssessmentAnswer>().eq(AssessmentAnswer::getUserId, userId));
        return count;
    }

    private long removeAssessments(Long userId) {
        long count = assessmentService.count(new LambdaQueryWrapper<Assessment>().eq(Assessment::getUserId, userId));
        assessmentService.remove(new LambdaQueryWrapper<Assessment>().eq(Assessment::getUserId, userId));
        return count;
    }

    private long removeWrongQuestionReviewPlans(Long userId) {
        long count = reviewPlanService.count(new LambdaQueryWrapper<WrongQuestionReviewPlan>().eq(WrongQuestionReviewPlan::getUserId, userId));
        reviewPlanService.remove(new LambdaQueryWrapper<WrongQuestionReviewPlan>().eq(WrongQuestionReviewPlan::getUserId, userId));
        return count;
    }

    private long removeWrongQuestions(Long userId) {
        long count = wrongQuestionService.count(new LambdaQueryWrapper<WrongQuestion>().eq(WrongQuestion::getUserId, userId));
        wrongQuestionService.remove(new LambdaQueryWrapper<WrongQuestion>().eq(WrongQuestion::getUserId, userId));
        return count;
    }

    private long removeStudyRecords(Long userId) {
        long count = studyRecordService.count(new LambdaQueryWrapper<StudyRecord>().eq(StudyRecord::getUserId, userId));
        studyRecordService.remove(new LambdaQueryWrapper<StudyRecord>().eq(StudyRecord::getUserId, userId));
        return count;
    }

    private long removeStudyTasks(Long userId) {
        long count = studyTaskService.count(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getUserId, userId));
        studyTaskService.remove(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getUserId, userId));
        return count;
    }

    private long removeStudyPlans(Long userId) {
        long count = studyPlanService.count(new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getUserId, userId));
        studyPlanService.remove(new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getUserId, userId));
        return count;
    }

    private long removeProfileCorrections(Long userId) {
        long count = correctionLogService.count(new LambdaQueryWrapper<UserProfileCorrectionLog>().eq(UserProfileCorrectionLog::getUserId, userId));
        correctionLogService.remove(new LambdaQueryWrapper<UserProfileCorrectionLog>().eq(UserProfileCorrectionLog::getUserId, userId));
        return count;
    }

    private long removeProfiles(Long userId) {
        long count = userProfileService.count(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        userProfileService.remove(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        return count;
    }

    private long deletePersonalDataExportFiles(Long userId) {
        Path directory = Paths.get("exports", "personal-data").toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        String prefix = "personal-data-" + userId + "-";
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> targets = files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith(prefix))
                    .toList();
            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
            return targets.size();
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "删除个人数据导出文件失败");
        }
    }

    private long deleteQaAudioFiles(Long userId) {
        Path root = Paths.get("uploads", "qa-audio").toAbsolutePath().normalize();
        Path directory = root.resolve(String.valueOf(userId)).normalize();
        if (!directory.startsWith(root) || !Files.exists(directory)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> targets = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            long fileCount = targets.stream().filter(Files::isRegularFile).count();
            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
            return fileCount;
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "删除答疑语音文件失败");
        }
    }

    private String toJson(Map<String, Object> counts) {
        try {
            return objectMapper.writeValueAsString(counts);
        } catch (JsonProcessingException e) {
            throw new BusinessException(Constants.CODE_ERROR, "生成清空审计内容失败");
        }
    }

    private Map<String, Object> toLogView(PersonalDataClearLog log) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("logId", log.getLogId());
        item.put("clearScope", log.getClearScope());
        item.put("confirmationText", log.getConfirmationText());
        item.put("counts", parseCounts(log.getCountsJson()));
        item.put("createTime", ResponseUtils.format(log.getCreateTime()));
        return item;
    }

    private Map<String, Object> toAdminLogView(PersonalDataClearLog log) {
        Map<String, Object> item = toLogView(log);
        item.put("userId", log.getUserId());
        return item;
    }

    private Object parseCounts(String countsJson) {
        if (!StringUtils.hasText(countsJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(countsJson, Map.class);
        } catch (JsonProcessingException e) {
            return countsJson;
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return Objects.requireNonNull(value).trim();
            }
        }
        return "";
    }
}
