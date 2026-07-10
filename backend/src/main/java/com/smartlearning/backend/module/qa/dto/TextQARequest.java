package com.smartlearning.backend.module.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TextQARequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    private String conversationId;

    private String subject;

    private Boolean confirmAnswer;

    private List<Map<String, Object>> history;
}
