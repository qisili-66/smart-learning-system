package com.smartlearning.backend.module.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_message")
public class QaMessage {

    @TableId(type = IdType.AUTO)
    private Long messageId;

    private String conversationId;

    private Long userId;

    private String role;

    private String contentType;

    private String content;

    private String audioFileName;

    private String recognizedText;

    private String correctedText;

    private Integer requiresConfirmation;

    private Integer confirmed;

    private Long latencyMs;

    private Integer qaQualityStatus;

    private String model;

    private LocalDateTime createTime;
}
