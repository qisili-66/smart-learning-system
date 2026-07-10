package com.smartlearning.backend.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("study_task")
public class StudyTask {

    @TableId(type = IdType.AUTO)
    private Long taskId;

    private Long planId;

    private Long userId;

    private LocalDate taskDate;

    private Integer taskType;

    private String stepType;

    private String title;

    private String description;

    private String knowledgePoint;

    private Long resourceId;

    private Integer difficulty;

    private Integer estimatedMinutes;

    private Integer finishStatus;

    private BigDecimal correctRate;

    private BigDecimal targetCorrectRate;

    private String unlockCondition;

    private String actionPath;

    private String aiReason;

    private Integer stepOrder;

    private Integer priority;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
