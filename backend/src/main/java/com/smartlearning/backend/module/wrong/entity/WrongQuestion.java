package com.smartlearning.backend.module.wrong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wrong_question")
public class WrongQuestion {

    @TableId(type = IdType.AUTO)
    private Long wrongId;

    private Long userId;

    private Long questionId;

    private String wrongAnswer;

    private Integer wrongReason;

    private Integer wrongCount;

    private Integer isMastered;

    private LocalDateTime firstWrongTime;

    private LocalDateTime lastReviewTime;
}
