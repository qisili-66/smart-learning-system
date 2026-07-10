package com.smartlearning.backend.module.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlearning.backend.module.qa.entity.QaMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QaMessageMapper extends BaseMapper<QaMessage> {
}
