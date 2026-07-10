package com.smartlearning.backend.module.plan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.plan.entity.StudyTask;
import com.smartlearning.backend.module.plan.mapper.StudyTaskMapper;
import com.smartlearning.backend.module.plan.service.StudyTaskService;
import org.springframework.stereotype.Service;

@Service
public class StudyTaskServiceImpl extends ServiceImpl<StudyTaskMapper, StudyTask> implements StudyTaskService {
}
