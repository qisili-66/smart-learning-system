package com.smartlearning.backend.module.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("study_record")
public class StudyRecord {

    @TableId(type = IdType.AUTO)
    private Long recordId;

    private Long userId;

    private Long resourceId;

    private Integer studyType;

    private Integer studyDuration;

    private Integer finishStatus;

    private LocalDateTime studyTime;
}
