package com.smartlearning.backend.module.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("learning_resource")
public class LearningResource {

    @TableId(type = IdType.AUTO)
    private Long resourceId;

    private String resourceName;

    private Integer resourceType;

    private String subject;

    private String knowledgePoint;

    private String textbookVersion;

    private String fileUrl;

    private Long fileSize;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
