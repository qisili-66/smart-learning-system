package com.smartlearning.backend.module.assessment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.assessment.entity.Assessment;
import com.smartlearning.backend.module.assessment.mapper.AssessmentMapper;
import com.smartlearning.backend.module.assessment.service.AssessmentService;
import org.springframework.stereotype.Service;

@Service
public class AssessmentServiceImpl extends ServiceImpl<AssessmentMapper, Assessment> implements AssessmentService {
}
