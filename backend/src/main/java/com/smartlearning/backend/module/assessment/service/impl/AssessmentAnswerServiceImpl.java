package com.smartlearning.backend.module.assessment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.assessment.entity.AssessmentAnswer;
import com.smartlearning.backend.module.assessment.mapper.AssessmentAnswerMapper;
import com.smartlearning.backend.module.assessment.service.AssessmentAnswerService;
import org.springframework.stereotype.Service;

@Service
public class AssessmentAnswerServiceImpl extends ServiceImpl<AssessmentAnswerMapper, AssessmentAnswer>
        implements AssessmentAnswerService {
}
