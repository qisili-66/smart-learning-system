package com.smartlearning.backend.module.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("question_bank")
public class QuestionBank {

    @TableId(type = IdType.AUTO)
    private Long questionId;

    private String subject;

    private String knowledgePoint;

    private Integer difficulty;

    private Integer questionType;

    private String questionText;

    private String options;

    private String answer;

    private String analysis;

    private String scoringPoints;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
