package com.smartlearning.backend.module.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.qa.dto.TextQARequest;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${ai.service.base-url}")
    private String aiBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Result<?> textQuestionAnswer(TextQARequest request) {
        String url = buildUrl("/qa/text");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    Result.class
            );
            return response.getBody() == null
                    ? Result.fail(Constants.CODE_ERROR, "AI service returned empty response")
                    : response.getBody();
        } catch (ResourceAccessException e) {
            return Result.fail(Constants.CODE_ERROR, "AI service is unavailable or timed out. Please start FastAPI service.");
        } catch (RestClientException e) {
            return Result.fail(Constants.CODE_ERROR, "AI service call failed: " + e.getMessage());
        }
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

        String url = buildUrl("/qa/image");
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Result.class
            );
            return response.getBody() == null
                    ? Result.fail(Constants.CODE_ERROR, "AI service returned empty response")
                    : response.getBody();
        } catch (ResourceAccessException e) {
            return Result.fail(Constants.CODE_ERROR, "AI service is unavailable or timed out. Please start FastAPI service.");
        } catch (RestClientException e) {
            return Result.fail(Constants.CODE_ERROR, "AI service call failed: " + e.getMessage());
        }
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

        String url = buildUrl("/qa/voice");
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Result.class
            );
            return response.getBody() == null
                    ? Result.fail(Constants.CODE_ERROR, "AI service returned empty response")
                    : response.getBody();
        } catch (ResourceAccessException e) {
            return Result.fail(Constants.CODE_ERROR, "AI service is unavailable or timed out. Please start FastAPI service.");
        } catch (RestClientException e) {
            return Result.fail(Constants.CODE_ERROR, "AI service call failed: " + e.getMessage());
        }
    }

    public Map<String, Object> subjectiveScore(Map<String, Object> request) {
        String url = buildUrl("/assessment/subjective-score");
        return postJsonForMap(url, request);
    }

    public Map<String, Object> learningPath(Map<String, Object> request) {
        String url = buildUrl("/study-plan/path");
        return postJsonForMap(url, request);
    }

    private Map<String, Object> postJsonForMap(String url, Map<String, Object> request) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    Result.class
            );
            Result body = response.getBody();
            if (body != null && Constants.CODE_SUCCESS.equals(body.getCode()) && body.getData() instanceof Map<?, ?> raw) {
                Map<String, Object> data = new LinkedHashMap<>();
                raw.forEach((key, value) -> data.put(String.valueOf(key), value));
                data.put("available", true);
                return data;
            }
            return unavailableScore(body == null ? "AI service returned empty response" : body.getMessage());
        } catch (ResourceAccessException e) {
            return unavailableScore("AI service is unavailable or timed out. Please start FastAPI service.");
        } catch (RestClientException e) {
            return unavailableScore("AI service call failed: " + e.getMessage());
        }
    }

    private ByteArrayResource toResource(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String safeFilename = (filename == null || filename.isBlank()) ? "question-image" : filename;
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

    private Map<String, Object> unavailableScore(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("message", message == null ? "AI service call failed" : message);
        return data;
    }

    private String buildUrl(String path) {
        return aiBaseUrl.replaceAll("/+$", "") + path;
    }
}
