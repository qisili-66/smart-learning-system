package com.smartlearning.backend.module.personal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("personal_data_export_log")
public class PersonalDataExportLog {

    @TableId(type = IdType.AUTO)
    private Long exportId;

    private Long userId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private Integer maxDownloadCount;

    private Integer downloadCount;

    private LocalDateTime lastDownloadTime;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
