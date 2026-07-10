package com.smartlearning.backend.module.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("assessment")
public class Assessment {

    @TableId(type = IdType.AUTO)
    private Long assessmentId;

    private Long userId;

    private Integer assessmentType;

    private String subject;

    private String knowledgeScope;

    private Integer difficulty;

    private BigDecimal totalScore;

    private BigDecimal userScore;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer assessmentStatus;

    private LocalDateTime createTime;
}
