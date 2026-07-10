package com.smartlearning.backend.module.wrong.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.wrong.entity.WrongQuestionReviewPlan;
import com.smartlearning.backend.module.wrong.mapper.WrongQuestionReviewPlanMapper;
import com.smartlearning.backend.module.wrong.service.WrongQuestionReviewPlanService;
import org.springframework.stereotype.Service;

@Service
public class WrongQuestionReviewPlanServiceImpl
        extends ServiceImpl<WrongQuestionReviewPlanMapper, WrongQuestionReviewPlan>
        implements WrongQuestionReviewPlanService {
}
