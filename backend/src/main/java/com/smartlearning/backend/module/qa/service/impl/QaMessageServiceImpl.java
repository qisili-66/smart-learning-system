package com.smartlearning.backend.module.qa.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.qa.entity.QaMessage;
import com.smartlearning.backend.module.qa.mapper.QaMessageMapper;
import com.smartlearning.backend.module.qa.service.QaMessageService;
import org.springframework.stereotype.Service;

@Service
public class QaMessageServiceImpl extends ServiceImpl<QaMessageMapper, QaMessage> implements QaMessageService {
}
