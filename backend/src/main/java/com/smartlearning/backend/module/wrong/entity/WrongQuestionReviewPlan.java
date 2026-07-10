package com.smartlearning.backend.module.wrong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wrong_question_review_plan")
public class WrongQuestionReviewPlan {

    @TableId(type = IdType.AUTO)
    private Long planId;

    private Long wrongId;

    private Long userId;

    private String personalNote;

    private Integer reviewCycleDays;

    private LocalDateTime nextReviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
