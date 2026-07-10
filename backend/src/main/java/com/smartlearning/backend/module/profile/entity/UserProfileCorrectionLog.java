package com.smartlearning.backend.module.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile_correction_log")
public class UserProfileCorrectionLog {

    @TableId(type = IdType.AUTO)
    private Long logId;

    private Long userId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    private Integer operatorType;

    private String reason;

    private LocalDateTime createTime;
}
