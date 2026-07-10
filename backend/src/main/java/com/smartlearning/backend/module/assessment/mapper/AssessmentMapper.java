package com.smartlearning.backend.module.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssessmentMapper extends BaseMapper<Assessment> {
}
