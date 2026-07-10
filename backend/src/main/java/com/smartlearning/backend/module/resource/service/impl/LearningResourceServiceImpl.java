package com.smartlearning.backend.module.resource.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.resource.entity.LearningResource;
import com.smartlearning.backend.module.resource.mapper.LearningResourceMapper;
import com.smartlearning.backend.module.resource.service.LearningResourceService;
import org.springframework.stereotype.Service;

@Service
public class LearningResourceServiceImpl extends ServiceImpl<LearningResourceMapper, LearningResource> implements LearningResourceService {
}
