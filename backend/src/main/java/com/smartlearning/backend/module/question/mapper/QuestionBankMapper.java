package com.smartlearning.backend.module.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionBankMapper extends BaseMapper<QuestionBank> {
}
