package com.smartlearning.backend.module.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.qa.dto.TextQARequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiService {

    private static final String PROVIDER_BACKEND = "spring-backend";
    private static final String ERROR_TIMEOUT = "AI_TIMEOUT";
    private static final String ERROR_CONNECTION = "AI_CONNECTION_FAILED";
    private static final String ERROR_HTTP = "AI_HTTP_ERROR";
    private static final String ERROR_INVALID_RESPONSE = "AI_INVALID_RESPONSE";
    private static final String ERROR_SERVICE = "AI_SERVICE_ERROR";
    private static final String ERROR_CALL_FAILED = "AI_CALL_FAILED";

    @Value("${ai.service.base-url}")
    private String aiBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Result<?> textQuestionAnswer(TextQARequest request) {
        HttpHeaders headers = jsonHeaders();
        return exchangeForResult(
                "qa_text",
                "/qa/text",
                new HttpEntity<>(request, headers)
        );
    }

    public Result<?> imageQuestionAnswer(MultipartFile file, String conversationId, String subject) {
        return imageQuestionAnswer(file, conversationId, subject, List.of(), false);
    }

    public Result<?> imageQuestionAnswer(MultipartFile file,
                                         String conversationId,
                                         String subject,
                                         List<Map<String, Object>> history,
                                         Boolean confirmAnswer) {
        if (file == null || file.isEmpty()) {
            return Result.fail(Constants.CODE_BAD_REQUEST, "图片文件不能为空");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", toResource(file));
        } catch (IOException e) {
            return Result.fail(Constants.CODE_BAD_REQUEST, "读取图片文件失败");
        }

        if (conversationId != null && !conversationId.isBlank()) {
            body.add("conversationId", conversationId);
        }
        if (subject != null && !subject.isBlank()) {
            body.add("subject", subject);
        }
        addHistory(body, history);
        if (confirmAnswer != null) {
            body.add("confirmAnswer", String.valueOf(confirmAnswer));
        }

        HttpHeaders headers = multipartHeaders();
        return exchangeForResult(
                "qa_image",
                "/qa/image",
                new HttpEntity<>(body, headers)
        );
    }

    public Result<?> voiceQuestionAnswer(MultipartFile file,
                                         String conversationId,
                                         String subject,
                                         String recognizedText,
                                         String correctedText,
                                         List<Map<String, Object>> history,
                                         Boolean confirmAnswer) {
        if (file == null || file.isEmpty()) {
            return Result.fail(Constants.CODE_BAD_REQUEST, "语音文件不能为空");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", toResource(file));
        } catch (IOException e) {
            return Result.fail(Constants.CODE_BAD_REQUEST, "读取语音文件失败");
        }

        if (conversationId != null && !conversationId.isBlank()) {
            body.add("conversationId", conversationId);
        }
        if (subject != null && !subject.isBlank()) {
            body.add("subject", subject);
        }
        if (recognizedText != null && !recognizedText.isBlank()) {
            body.add("recognizedText", recognizedText);
        }
        if (correctedText != null && !correctedText.isBlank()) {
            body.add("correctedText", correctedText);
        }
        addHistory(body, history);
        if (confirmAnswer != null) {
            body.add("confirmAnswer", String.valueOf(confirmAnswer));
        }

        HttpHeaders headers = multipartHeaders();
        return exchangeForResult(
                "qa_voice",
                "/qa/voice",
                new HttpEntity<>(body, headers)
        );
    }

    public Map<String, Object> subjectiveScore(Map<String, Object> request) {
        return postJsonForMap("subjective_score", "/assessment/subjective-score", request);
    }

    public Map<String, Object> learningPath(Map<String, Object> request) {
        return postJsonForMap("learning_path", "/study-plan/path", request);
    }

    private Map<String, Object> postJsonForMap(String operation, String path, Map<String, Object> request) {
        Result<?> result = exchangeForResult(operation, path, new HttpEntity<>(request, jsonHeaders()));
        if (result != null && Constants.CODE_SUCCESS.equals(result.getCode()) && result.getData() instanceof Map<?, ?> raw) {
            Map<String, Object> data = new LinkedHashMap<>();
            raw.forEach((key, value) -> data.put(String.valueOf(key), value));
            data.put("available", true);
            data.putIfAbsent("provider", PROVIDER_BACKEND);
            return data;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (result != null && result.getData() instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> data.put(String.valueOf(key), value));
        }
        data.put("available", false);
        data.putIfAbsent("message", result == null ? "AI service call failed" : result.getMessage());
        data.putIfAbsent("errorCode", ERROR_CALL_FAILED);
        data.putIfAbsent("failureCategory", "unknown");
        data.putIfAbsent("provider", PROVIDER_BACKEND);
        data.put("fallback", true);
        return data;
    }

    private Result<?> exchangeForResult(String operation, String path, HttpEntity<?> entity) {
        String url = buildUrl(path);
        long started = System.currentTimeMillis();
        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Result.class
            );
            long latencyMs = System.currentTimeMillis() - started;
            Result body = response.getBody();
            if (body == null) {
                Map<String, Object> meta = failureMetadata(operation, path, latencyMs,
                        "invalid_response", ERROR_INVALID_RESPONSE, "AI service returned empty response");
                logAiFailure(meta, null);
                return failureResult(Constants.CODE_ERROR, "AI service returned empty response", meta);
            }

            Map<String, Object> data = copyDataMap(body.getData());
            enrichSuccessMetadata(operation, path, latencyMs, data);
            if (Constants.CODE_SUCCESS.equals(body.getCode())) {
                log.info(
                        "AI call success operation={} endpoint={} latencyMs={} provider={} model={} fallback={} failureCategory={}",
                        operation,
                        path,
                        latencyMs,
                        data.getOrDefault("provider", PROVIDER_BACKEND),
                        data.getOrDefault("model", ""),
                        data.getOrDefault("fallback", false),
                        data.getOrDefault("failureCategory", "")
                );
                return successResult(body, data);
            }

            putIfBlank(data, "errorCode", String.valueOf(body.getCode()));
            putIfBlank(data, "failureCategory", "ai_service_error");
            putIfBlank(data, "errorMessage", body.getMessage());
            if (body.getCode() == null) {
                data.put("errorCode", ERROR_SERVICE);
            }
            data.put("fallback", true);
            logAiFailure(data, null);
            return failureResult(body.getCode(), body.getMessage(), data);
        } catch (ResourceAccessException e) {
            long latencyMs = System.currentTimeMillis() - started;
            String category = isTimeout(e) ? "timeout" : "connection";
            String errorCode = isTimeout(e) ? ERROR_TIMEOUT : ERROR_CONNECTION;
            Map<String, Object> meta = failureMetadata(operation, path, latencyMs, category, errorCode,
                    category.equals("timeout")
                            ? "AI service call timed out"
                            : "AI service is unavailable");
            logAiFailure(meta, e);
            return failureResult(Constants.CODE_ERROR, meta.get("errorMessage").toString(), meta);
        } catch (RestClientResponseException e) {
            long latencyMs = System.currentTimeMillis() - started;
            Map<String, Object> meta = failureMetadata(operation, path, latencyMs,
                    "http_error", ERROR_HTTP + "_" + e.getRawStatusCode(), "AI service returned HTTP " + e.getRawStatusCode());
            logAiFailure(meta, e);
            return failureResult(Constants.CODE_ERROR, meta.get("errorMessage").toString(), meta);
        } catch (RestClientException e) {
            long latencyMs = System.currentTimeMillis() - started;
            Map<String, Object> meta = failureMetadata(operation, path, latencyMs,
                    "call_error", ERROR_CALL_FAILED, "AI service call failed");
            logAiFailure(meta, e);
            return failureResult(Constants.CODE_ERROR, meta.get("errorMessage").toString(), meta);
        }
    }

    private Result<?> successResult(Result<?> body, Map<String, Object> data) {
        if (body.getData() instanceof Map<?, ?>) {
            return Result.success(body.getMessage(), data);
        }
        return body;
    }

    private Result<Map<String, Object>> failureResult(Integer code, String message, Map<String, Object> data) {
        return Result.<Map<String, Object>>builder()
                .code(code == null ? Constants.CODE_ERROR : code)
                .message(message == null ? "AI service call failed" : message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private Map<String, Object> copyDataMap(Object rawData) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (rawData instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> data.put(String.valueOf(key), value));
        }
        return data;
    }

    private void enrichSuccessMetadata(String operation, String path, long latencyMs, Map<String, Object> data) {
        data.put("operation", operation);
        data.put("endpoint", path);
        data.put("latencyMs", latencyMs);
        data.putIfAbsent("provider", PROVIDER_BACKEND);
        data.putIfAbsent("fallback", isFallback(data));
        data.putIfAbsent("failureCategory", "");
        data.putIfAbsent("errorCode", "");
        data.putIfAbsent("errorMessage", "");
    }

    private void putIfBlank(Map<String, Object> data, String key, String value) {
        Object current = data.get(key);
        if (current == null || current.toString().isBlank()) {
            data.put(key, value == null ? "" : value);
        }
    }

    private Map<String, Object> failureMetadata(String operation,
                                                String path,
                                                long latencyMs,
                                                String failureCategory,
                                                String errorCode,
                                                String errorMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation", operation);
        data.put("endpoint", path);
        data.put("latencyMs", latencyMs);
        data.put("provider", PROVIDER_BACKEND);
        data.put("model", "");
        data.put("fallback", true);
        data.put("failureCategory", failureCategory);
        data.put("errorCode", errorCode);
        data.put("errorMessage", errorMessage);
        data.put("available", false);
        data.put("message", errorMessage);
        return data;
    }

    private boolean isFallback(Map<String, Object> data) {
        Object fallback = data.get("fallback");
        if (fallback instanceof Boolean bool) {
            return bool;
        }
        String mode = String.valueOf(data.getOrDefault("scoringMode", ""));
        String provider = String.valueOf(data.getOrDefault("provider", ""));
        return mode.contains("fallback") || provider.contains("fallback") || provider.contains("rule");
    }

    private void logAiFailure(Map<String, Object> meta, Exception e) {
        if (e == null) {
            log.warn(
                    "AI call failed operation={} endpoint={} latencyMs={} category={} errorCode={} message={}",
                    meta.get("operation"),
                    meta.get("endpoint"),
                    meta.get("latencyMs"),
                    meta.get("failureCategory"),
                    meta.get("errorCode"),
                    meta.get("errorMessage")
            );
            return;
        }
        log.warn(
                "AI call failed operation={} endpoint={} latencyMs={} category={} errorCode={} message={}",
                meta.get("operation"),
                meta.get("endpoint"),
                meta.get("latencyMs"),
                meta.get("failureCategory"),
                meta.get("errorCode"),
                meta.get("errorMessage"),
                e
        );
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        String message = throwable.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpHeaders multipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private ByteArrayResource toResource(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String safeFilename = (filename == null || filename.isBlank()) ? "question-file" : filename;
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return safeFilename;
            }
        };
    }

    private void addHistory(MultiValueMap<String, Object> body, List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        try {
            body.add("history", objectMapper.writeValueAsString(history));
        } catch (JsonProcessingException ignored) {
            // History is an optimization for persistent memory; the AI service can still answer without it.
        }
    }

    private String buildUrl(String path) {
        return aiBaseUrl.replaceAll("/+$", "") + path;
    }
}
