package com.smartlearning.backend.module.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("assessment_answer")
public class AssessmentAnswer {

    @TableId(type = IdType.AUTO)
    private Long answerId;

    private Long assessmentId;

    private Long userId;

    private Long questionId;

    private String userAnswer;

    private String correctAnswer;

    private Integer isCorrect;

    private BigDecimal score;

    private BigDecimal maxScore;

    private Integer scoreStatus;

    private Integer reviewStatus;

    private String reviewComment;

    private String scoringDetail;

    private BigDecimal aiScore;

    private BigDecimal aiConfidence;

    private String scoringPointsSnapshot;

    private Integer questionUseSeconds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
