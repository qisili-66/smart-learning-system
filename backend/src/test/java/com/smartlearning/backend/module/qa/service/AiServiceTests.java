package com.smartlearning.backend.module.qa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceTests {

    private AiService aiService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        aiService = new AiService(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(aiService, "aiBaseUrl", "http://ai-service.test");
    }

    @Test
    void subjectiveScoreKeepsObservationMetadataOnSuccess() {
        server.expect(requestTo("http://ai-service.test/assessment/subjective-score"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "message": "ok",
                          "data": {
                            "score": 8.5,
                            "confidence": 88,
                            "provider": "openai-compatible",
                            "model": "qwen3.7-max",
                            "fallback": false,
                            "failureCategory": "",
                            "errorCode": ""
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = aiService.subjectiveScore(Map.of("studentAnswer", "demo"));

        assertEquals(Boolean.TRUE, result.get("available"));
        assertEquals("subjective_score", result.get("operation"));
        assertEquals("/assessment/subjective-score", result.get("endpoint"));
        assertEquals("openai-compatible", result.get("provider"));
        assertEquals("qwen3.7-max", result.get("model"));
        assertEquals(false, result.get("fallback"));
        assertTrue(result.containsKey("latencyMs"));
        server.verify();
    }

    @Test
    void subjectiveScoreClassifiesAiServiceError() {
        server.expect(requestTo("http://ai-service.test/assessment/subjective-score"))
                .andRespond(withSuccess("""
                        {
                          "code": 500,
                          "message": "model failed",
                          "data": {
                            "model": "qwen3.7-max",
                            "failureCategory": "invalid_response",
                            "errorCode": "MODEL_JSON_PARSE_FAILED"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = aiService.subjectiveScore(Map.of("studentAnswer", "demo"));

        assertEquals(Boolean.FALSE, result.get("available"));
        assertEquals(Boolean.TRUE, result.get("fallback"));
        assertEquals("invalid_response", result.get("failureCategory"));
        assertEquals("MODEL_JSON_PARSE_FAILED", result.get("errorCode"));
        assertEquals("model failed", result.get("message"));
        assertEquals("qwen3.7-max", result.get("model"));
        server.verify();
    }

    @Test
    void subjectiveScoreClassifiesInvalidTransportResponse() {
        server.expect(requestTo("http://ai-service.test/assessment/subjective-score"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = aiService.subjectiveScore(Map.of("studentAnswer", "demo"));

        assertEquals(Boolean.FALSE, result.get("available"));
        assertEquals(Boolean.TRUE, result.get("fallback"));
        assertEquals("ai_service_error", result.get("failureCategory"));
        assertEquals("AI_SERVICE_ERROR", result.get("errorCode"));
        assertTrue(result.containsKey("latencyMs"));
        server.verify();
    }

    @Test
    void subjectiveScoreClassifiesUnavailableAiServiceAsConnectionFallback() {
        server.expect(requestTo("http://ai-service.test/assessment/subjective-score"))
                .andRespond(withException(new IOException("connection refused")));

        Map<String, Object> result = aiService.subjectiveScore(Map.of("studentAnswer", "demo"));

        assertEquals(Boolean.FALSE, result.get("available"));
        assertEquals(Boolean.TRUE, result.get("fallback"));
        assertEquals("connection", result.get("failureCategory"));
        assertEquals("AI_CONNECTION_FAILED", result.get("errorCode"));
        assertEquals("/assessment/subjective-score", result.get("endpoint"));
        assertTrue(result.containsKey("latencyMs"));
        server.verify();
    }

    @Test
    void subjectiveScoreClassifiesTimeoutAsTimeoutFallback() {
        server.expect(requestTo("http://ai-service.test/assessment/subjective-score"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        Map<String, Object> result = aiService.subjectiveScore(Map.of("studentAnswer", "demo"));

        assertEquals(Boolean.FALSE, result.get("available"));
        assertEquals(Boolean.TRUE, result.get("fallback"));
        assertEquals("timeout", result.get("failureCategory"));
        assertEquals("AI_TIMEOUT", result.get("errorCode"));
        assertEquals("subjective_score", result.get("operation"));
        assertTrue(result.containsKey("latencyMs"));
        server.verify();
    }
}
