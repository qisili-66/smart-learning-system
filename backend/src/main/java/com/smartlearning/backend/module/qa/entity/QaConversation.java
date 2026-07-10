package com.smartlearning.backend.module.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_conversation")
public class QaConversation {

    @TableId(type = IdType.INPUT)
    private String conversationId;

    private Long userId;

    private String title;

    private String subject;

    private Integer messageCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
