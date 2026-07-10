package com.smartlearning.backend.module.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.ResponseUtils;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.qa.dto.TextQARequest;
import com.smartlearning.backend.module.qa.entity.QaConversation;
import com.smartlearning.backend.module.qa.entity.QaMessage;
import com.smartlearning.backend.module.qa.mapper.QaConversationMapper;
import com.smartlearning.backend.module.qa.service.AiService;
import com.smartlearning.backend.module.qa.service.QaConversationService;
import com.smartlearning.backend.module.qa.service.QaMessageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QaConversationServiceImpl extends ServiceImpl<QaConversationMapper, QaConversation>
        implements QaConversationService {

    private static final int CONVERSATION_ACTIVE = 1;
    private static final int HISTORY_LIMIT = 12;
    private static final int QUALITY_ACCEPTABLE = 1;
    private static final int QUALITY_RISK = 2;

    private final QaMessageService qaMessageService;
    private final AiService aiService;

    public QaConversationServiceImpl(QaMessageService qaMessageService, AiService aiService) {
        this.qaMessageService = qaMessageService;
        this.aiService = aiService;
    }

    @Override
    public Result<?> textQuestionAnswer(Long userId, TextQARequest request) {
        String conversationId = ensureConversation(userId, request.getConversationId(), request.getSubject(), request.getQuestion());
        List<Map<String, Object>> history = loadHistory(userId, conversationId);
        request.setConversationId(conversationId);
        request.setHistory(history);

        long started = System.currentTimeMillis();
        Result<?> result = aiService.textQuestionAnswer(request);
        long latency = System.currentTimeMillis() - started;

        Map<String, Object> data = responseData(result);
        QaMessage userMessage = saveMessage(userId, conversationId, "user", "text", request.getQuestion(),
                null, null, null, false, Boolean.TRUE.equals(request.getConfirmAnswer()), null, null, "");
        QaMessage assistantMessage = saveAssistantMessage(userId, conversationId, "text", result, data, latency,
                Boolean.TRUE.equals(request.getConfirmAnswer()));
        fillResponseData(data, conversationId, userMessage, assistantMessage);
        touchConversation(userId, conversationId, request.getSubject(), request.getQuestion());
        return successOrOriginal(result, data);
    }

    @Override
    public Result<?> imageQuestionAnswer(Long userId, MultipartFile file, String conversationId, String subject, Boolean confirmAnswer) {
        if (file == null || file.isEmpty()) {
            return Result.fail(Constants.CODE_BAD_REQUEST, "图片文件不能为空");
        }
        String finalConversationId = ensureConversation(userId, conversationId, subject, safeFilePrompt(file, "上传图片"));
        List<Map<String, Object>> history = loadHistory(userId, finalConversationId);

        long started = System.currentTimeMillis();
        Result<?> result = aiService.imageQuestionAnswer(file, finalConversationId, subject, history, confirmAnswer);
        long latency = System.currentTimeMillis() - started;

        Map<String, Object> data = responseData(result);
        String questionText = firstText(data.get("ocrText"), safeFilePrompt(file, "上传图片"));
        QaMessage userMessage = saveMessage(userId, finalConversationId, "user", "image", questionText,
                null, null, null, false, Boolean.TRUE.equals(confirmAnswer), null, null, "");
        QaMessage assistantMessage = saveAssistantMessage(userId, finalConversationId, "image", result, data, latency,
                Boolean.TRUE.equals(confirmAnswer));
        fillResponseData(data, finalConversationId, userMessage, assistantMessage);
        touchConversation(userId, finalConversationId, subject, questionText);
        return successOrOriginal(result, data);
    }

    @Override
    public Result<?> voiceQuestionAnswer(Long userId,
                                         MultipartFile file,
                                         String conversationId,
                                         String subject,
                                         String recognizedText,
                                         String correctedText,
                                         Boolean confirmAnswer) {
        if (file == null || file.isEmpty()) {
            return Result.fail(Constants.CODE_BAD_REQUEST, "语音文件不能为空");
        }
        String effectiveText = firstText(correctedText, recognizedText);
        String finalConversationId = ensureConversation(userId, conversationId, subject,
                StringUtils.hasText(effectiveText) ? effectiveText : safeFilePrompt(file, "语音提问"));
        List<Map<String, Object>> history = loadHistory(userId, finalConversationId);

        long started = System.currentTimeMillis();
        Result<?> result = aiService.voiceQuestionAnswer(file, finalConversationId, subject, recognizedText, correctedText, history, confirmAnswer);
        long latency = System.currentTimeMillis() - started;

        Map<String, Object> data = responseData(result);
        String fileName = saveAudioFile(userId, finalConversationId, file);
        String answerText = firstText(data.get("correctedText"), data.get("recognizedText"), effectiveText, safeFilePrompt(file, "语音提问"));
        QaMessage userMessage = saveMessage(userId, finalConversationId, "user", "voice", answerText,
                fileName, firstText(data.get("recognizedText"), recognizedText), firstText(data.get("correctedText"), correctedText),
                false, Boolean.TRUE.equals(confirmAnswer), null, null, "");
        QaMessage assistantMessage = saveAssistantMessage(userId, finalConversationId, "voice", result, data, latency,
                Boolean.TRUE.equals(confirmAnswer));
        fillResponseData(data, finalConversationId, userMessage, assistantMessage);
        data.put("audioUrl", audioUrl(finalConversationId, fileName));
        touchConversation(userId, finalConversationId, subject, answerText);
        return successOrOriginal(result, data);
    }

    @Override
    public PageVO<Map<String, Object>> conversations(Long userId, Integer pageNum, Integer pageSize) {
        Page<QaConversation> page = page(PageUtils.page(pageNum, pageSize), new LambdaQueryWrapper<QaConversation>()
                .eq(QaConversation::getUserId, userId)
                .eq(QaConversation::getStatus, CONVERSATION_ACTIVE)
                .orderByDesc(QaConversation::getUpdateTime));
        List<Map<String, Object>> rows = page.getRecords().stream()
                .map(item -> conversationMap(item, lastMessage(userId, item.getConversationId())))
                .toList();
        return PageVO.<Map<String, Object>>builder()
                .list(rows)
                .total(page.getTotal())
                .pageNum((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .pages((int) page.getPages())
                .build();
    }

    @Override
    public Map<String, Object> detail(Long userId, String conversationId) {
        QaConversation conversation = ownedConversation(userId, conversationId);
        List<Map<String, Object>> messages = qaMessageService.lambdaQuery()
                .eq(QaMessage::getUserId, userId)
                .eq(QaMessage::getConversationId, conversationId)
                .orderByAsc(QaMessage::getMessageId)
                .list()
                .stream()
                .map(this::messageMap)
                .toList();
        Map<String, Object> data = conversationMap(conversation, lastOrNull(messages));
        data.put("messages", messages);
        return data;
    }

    @Override
    public void deleteConversation(Long userId, String conversationId) {
        ownedConversation(userId, conversationId);
        qaMessageService.remove(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getUserId, userId)
                .eq(QaMessage::getConversationId, conversationId));
        removeById(conversationId);
    }

    @Override
    public Map<String, Object> evaluation(Long userId, Integer days) {
        int safeDays = days == null ? 7 : Math.max(1, Math.min(days, 180));
        LocalDateTime startTime = LocalDateTime.now().minusDays(safeDays);
        List<QaMessage> messages = qaMessageService.lambdaQuery()
                .eq(QaMessage::getUserId, userId)
                .ge(QaMessage::getCreateTime, startTime)
                .list();
        List<QaMessage> userMessages = messages.stream()
                .filter(item -> "user".equals(item.getRole()))
                .toList();
        List<QaMessage> assistantMessages = messages.stream()
                .filter(item -> "assistant".equals(item.getRole()))
                .toList();
        List<Long> latencies = assistantMessages.stream()
                .map(QaMessage::getLatencyMs)
                .filter(Objects::nonNull)
                .filter(value -> value >= 0)
                .sorted()
                .toList();
        long acceptable = assistantMessages.stream()
                .filter(item -> Integer.valueOf(QUALITY_ACCEPTABLE).equals(item.getQaQualityStatus()))
                .count();
        long guardrailTriggered = assistantMessages.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getRequiresConfirmation()))
                .count();
        long confirmed = assistantMessages.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getConfirmed()))
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", safeDays);
        data.put("conversationCount", count(new LambdaQueryWrapper<QaConversation>()
                .eq(QaConversation::getUserId, userId)
                .eq(QaConversation::getStatus, CONVERSATION_ACTIVE)
                .ge(QaConversation::getUpdateTime, startTime)));
        data.put("questionCount", userMessages.size());
        data.put("answerCount", assistantMessages.size());
        data.put("textQuestionCount", countContentType(userMessages, "text"));
        data.put("imageQuestionCount", countContentType(userMessages, "image"));
        data.put("voiceQuestionCount", countContentType(userMessages, "voice"));
        data.put("avgFirstResponseMs", average(latencies));
        data.put("p95FirstResponseMs", percentile(latencies, 0.95D));
        data.put("heuristicAccuracyRate", percent(acceptable, assistantMessages.size()));
        data.put("accuracySampleCount", assistantMessages.size());
        data.put("guardrailTriggeredCount", guardrailTriggered);
        data.put("confirmedAnswerCount", confirmed);
        data.put("persistentMemory", true);
        data.put("metricNote", "准确率为自动验收启发式指标：AI 返回非空且未命中失败/异常关键词即计为通过。");
        return data;
    }

    @Override
    public Path audioFile(Long userId, String conversationId, String fileName) {
        ownedConversation(userId, conversationId);
        if (!StringUtils.hasText(fileName) || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "fileName参数错误");
        }
        boolean exists = qaMessageService.count(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getUserId, userId)
                .eq(QaMessage::getConversationId, conversationId)
                .eq(QaMessage::getAudioFileName, fileName)) > 0;
        if (!exists) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "语音文件不存在");
        }
        try {
            Path directory = audioDirectory(userId, conversationId);
            Path file = directory.resolve(fileName).normalize();
            if (!file.startsWith(directory) || !Files.exists(file)) {
                throw new BusinessException(Constants.CODE_NOT_FOUND, "语音文件不存在");
            }
            return file;
        } catch (InvalidPathException e) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "fileName参数错误");
        }
    }

    private String ensureConversation(Long userId, String conversationId, String subject, String firstQuestion) {
        String safeConversationId = StringUtils.hasText(conversationId)
                ? conversationId.trim()
                : "conv-" + UUID.randomUUID();
        QaConversation existing = getById(safeConversationId);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new BusinessException(Constants.CODE_NOT_FOUND, "conversation not found");
            }
            return safeConversationId;
        }

        LocalDateTime now = LocalDateTime.now();
        QaConversation conversation = new QaConversation();
        conversation.setConversationId(safeConversationId);
        conversation.setUserId(userId);
        conversation.setTitle(title(firstQuestion));
        conversation.setSubject(ResponseUtils.safe(subject));
        conversation.setMessageCount(0);
        conversation.setStatus(CONVERSATION_ACTIVE);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        save(conversation);
        return safeConversationId;
    }

    private QaConversation ownedConversation(Long userId, String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "conversationId不能为空");
        }
        QaConversation conversation = getById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "conversation not found");
        }
        return conversation;
    }

    private List<Map<String, Object>> loadHistory(Long userId, String conversationId) {
        List<QaMessage> recent = qaMessageService.lambdaQuery()
                .eq(QaMessage::getUserId, userId)
                .eq(QaMessage::getConversationId, conversationId)
                .in(QaMessage::getRole, List.of("user", "assistant"))
                .orderByDesc(QaMessage::getMessageId)
                .last("LIMIT " + HISTORY_LIMIT)
                .list();
        Collections.reverse(recent);
        return recent.stream()
                .map(this::historyMap)
                .filter(item -> StringUtils.hasText(Objects.toString(item.get("content"), "")))
                .toList();
    }

    private Map<String, Object> historyMap(QaMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", message.getRole());
        data.put("content", firstText(message.getCorrectedText(), message.getRecognizedText(), message.getContent()));
        return data;
    }

    private QaMessage saveAssistantMessage(Long userId,
                                           String conversationId,
                                           String contentType,
                                           Result<?> result,
                                           Map<String, Object> data,
                                           long latency,
                                           boolean confirmedAnswer) {
        boolean requiresConfirmation = toBoolean(data.get("requiresConfirmation"));
        boolean confirmed = confirmedAnswer || toBoolean(data.get("confirmedAnswer"));
        String answer = extractAnswer(result, data);
        return saveMessage(userId, conversationId, "assistant", contentType, answer, null, null, null,
                requiresConfirmation, confirmed, latency, qualityStatus(result, answer), ResponseUtils.safe(Objects.toString(data.get("model"), "")));
    }

    private QaMessage saveMessage(Long userId,
                                  String conversationId,
                                  String role,
                                  String contentType,
                                  String content,
                                  String audioFileName,
                                  String recognizedText,
                                  String correctedText,
                                  boolean requiresConfirmation,
                                  boolean confirmed,
                                  Long latencyMs,
                                  Integer qualityStatus,
                                  String model) {
        QaMessage message = new QaMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContentType(contentType);
        message.setContent(ResponseUtils.safe(content));
        message.setAudioFileName(ResponseUtils.safe(audioFileName));
        message.setRecognizedText(ResponseUtils.safe(recognizedText));
        message.setCorrectedText(ResponseUtils.safe(correctedText));
        message.setRequiresConfirmation(requiresConfirmation ? 1 : 0);
        message.setConfirmed(confirmed ? 1 : 0);
        message.setLatencyMs(latencyMs);
        message.setQaQualityStatus(qualityStatus);
        message.setModel(ResponseUtils.safe(model));
        message.setCreateTime(LocalDateTime.now());
        qaMessageService.save(message);
        return message;
    }

    private void touchConversation(Long userId, String conversationId, String subject, String question) {
        QaConversation conversation = getById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return;
        }
        if (StringUtils.hasText(subject)) {
            conversation.setSubject(subject.trim());
        }
        if (!StringUtils.hasText(conversation.getTitle()) || "新的答疑会话".equals(conversation.getTitle())) {
            conversation.setTitle(title(question));
        }
        conversation.setMessageCount((int) qaMessageService.count(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getUserId, userId)
                .eq(QaMessage::getConversationId, conversationId)));
        conversation.setStatus(CONVERSATION_ACTIVE);
        conversation.setUpdateTime(LocalDateTime.now());
        updateById(conversation);
    }

    private Map<String, Object> conversationMap(QaConversation conversation, Object lastMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversation.getConversationId());
        data.put("id", conversation.getConversationId());
        data.put("title", ResponseUtils.safe(conversation.getTitle()));
        data.put("subject", ResponseUtils.safe(conversation.getSubject()));
        data.put("messageCount", conversation.getMessageCount() == null ? 0 : conversation.getMessageCount());
        data.put("createTime", ResponseUtils.format(conversation.getCreateTime()));
        data.put("updateTime", ResponseUtils.format(conversation.getUpdateTime()));
        data.put("updatedAt", ResponseUtils.format(conversation.getUpdateTime()));
        data.put("lastMessage", lastMessage == null ? Map.of() : lastMessage);
        return data;
    }

    private Map<String, Object> messageMap(QaMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("conversationId", message.getConversationId());
        data.put("role", message.getRole());
        data.put("contentType", message.getContentType());
        data.put("content", ResponseUtils.safe(message.getContent()));
        data.put("recognizedText", ResponseUtils.safe(message.getRecognizedText()));
        data.put("correctedText", ResponseUtils.safe(message.getCorrectedText()));
        data.put("requiresConfirmation", Integer.valueOf(1).equals(message.getRequiresConfirmation()));
        data.put("confirmed", Integer.valueOf(1).equals(message.getConfirmed()));
        data.put("latencyMs", message.getLatencyMs());
        data.put("qaQualityStatus", message.getQaQualityStatus());
        data.put("model", ResponseUtils.safe(message.getModel()));
        data.put("createTime", ResponseUtils.format(message.getCreateTime()));
        data.put("time", ResponseUtils.format(message.getCreateTime()));
        if (StringUtils.hasText(message.getAudioFileName())) {
            data.put("audioUrl", audioUrl(message.getConversationId(), message.getAudioFileName()));
        }
        return data;
    }

    private Map<String, Object> lastMessage(Long userId, String conversationId) {
        QaMessage message = qaMessageService.lambdaQuery()
                .eq(QaMessage::getUserId, userId)
                .eq(QaMessage::getConversationId, conversationId)
                .orderByDesc(QaMessage::getMessageId)
                .last("LIMIT 1")
                .one();
        return message == null ? Map.of() : messageMap(message);
    }

    @SuppressWarnings("unchecked")
    private Object lastOrNull(List<Map<String, Object>> messages) {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    private Map<String, Object> responseData(Result<?> result) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (result == null || result.getData() == null) {
            return data;
        }
        if (result.getData() instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            data.put("content", result.getData());
        }
        return data;
    }

    private Result<?> successOrOriginal(Result<?> result, Map<String, Object> data) {
        if (result == null) {
            return Result.fail(Constants.CODE_ERROR, "AI service returned empty response");
        }
        if (!Constants.CODE_SUCCESS.equals(result.getCode())) {
            return result;
        }
        return Result.success(result.getMessage(), data);
    }

    private void fillResponseData(Map<String, Object> data, String conversationId, QaMessage userMessage, QaMessage assistantMessage) {
        data.put("conversationId", conversationId);
        data.put("userMessageId", userMessage.getMessageId());
        data.put("assistantMessageId", assistantMessage.getMessageId());
        data.put("answerMessageId", assistantMessage.getMessageId());
    }

    private String extractAnswer(Result<?> result, Map<String, Object> data) {
        String answer = firstText(data.get("answer"), data.get("content"));
        if (StringUtils.hasText(answer)) {
            return answer;
        }
        return result == null ? "" : ResponseUtils.safe(result.getMessage());
    }

    private int qualityStatus(Result<?> result, String answer) {
        if (result == null || !Constants.CODE_SUCCESS.equals(result.getCode()) || !StringUtils.hasText(answer)) {
            return QUALITY_RISK;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        if (lower.contains("失败") || lower.contains("异常") || lower.contains("unavailable") || lower.contains("timeout")) {
            return QUALITY_RISK;
        }
        return QUALITY_ACCEPTABLE;
    }

    private String saveAudioFile(Long userId, String conversationId, MultipartFile file) {
        try {
            Path directory = audioDirectory(userId, conversationId);
            Files.createDirectories(directory);
            String extension = extension(file.getOriginalFilename());
            String fileName = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                    + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + extension;
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) {
                throw new BusinessException(Constants.CODE_BAD_REQUEST, "语音文件名错误");
            }
            Files.write(target, file.getBytes());
            return fileName;
        } catch (IOException e) {
            throw new BusinessException(Constants.CODE_ERROR, "保存语音文件失败");
        }
    }

    private Path audioDirectory(Long userId, String conversationId) {
        return Paths.get("uploads", "qa-audio", String.valueOf(userId), conversationId).toAbsolutePath().normalize();
    }

    private String audioUrl(String conversationId, String fileName) {
        return "/api/qa/audio/" + conversationId + "/" + fileName;
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return ".webm";
        }
        String safe = Paths.get(fileName).getFileName().toString();
        int dot = safe.lastIndexOf('.');
        if (dot < 0 || dot == safe.length() - 1) {
            return ".webm";
        }
        String extension = safe.substring(dot).toLowerCase(Locale.ROOT);
        if (extension.length() > 12 || !extension.matches("\\.[a-z0-9]+")) {
            return ".webm";
        }
        return extension;
    }

    private String safeFilePrompt(MultipartFile file, String prefix) {
        String filename = file == null ? "" : ResponseUtils.safe(file.getOriginalFilename());
        return StringUtils.hasText(filename) ? prefix + "：" + filename : prefix;
    }

    private String title(String value) {
        String text = firstText(value, "新的答疑会话").replaceAll("\\s+", " ").trim();
        return text.length() <= 28 ? text : text.substring(0, 28);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private long countContentType(List<QaMessage> messages, String contentType) {
        return messages.stream()
                .filter(item -> contentType.equals(item.getContentType()))
                .count();
    }

    private Long average(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0D));
    }

    private Long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(values.size() * percentile) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
