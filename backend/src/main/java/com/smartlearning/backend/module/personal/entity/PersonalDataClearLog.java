package com.smartlearning.backend.module.personal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("personal_data_clear_log")
public class PersonalDataClearLog {

    @TableId(type = IdType.AUTO)
    private Long logId;

    private Long userId;

    private String clearScope;

    private String confirmationText;

    private String countsJson;

    private LocalDateTime createTime;
}
