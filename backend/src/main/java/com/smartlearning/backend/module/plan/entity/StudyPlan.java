package com.smartlearning.backend.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("study_plan")
public class StudyPlan {

    @TableId(type = IdType.AUTO)
    private Long planId;

    private Long userId;

    private String planName;

    private String subject;

    private String targetDesc;

    private BigDecimal currentScore;

    private BigDecimal targetScore;

    private Integer dailyMinutes;

    private String aiProvider;

    private String aiPlanSummary;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer planStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
