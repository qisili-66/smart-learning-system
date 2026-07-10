package com.smartlearning.backend.module.question.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.question.entity.QuestionBank;
import com.smartlearning.backend.module.question.mapper.QuestionBankMapper;
import com.smartlearning.backend.module.question.service.QuestionBankService;
import org.springframework.stereotype.Service;

@Service
public class QuestionBankServiceImpl extends ServiceImpl<QuestionBankMapper, QuestionBank> implements QuestionBankService {
}
