package com.smartlearning.backend.module.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long profileId;

    private Long userId;

    private BigDecimal abilityScore;

    private BigDecimal knowledgeMastery;

    private String studyHabit;

    private String weakPoints;

    private String preference;

    private LocalDateTime updateTime;
}
