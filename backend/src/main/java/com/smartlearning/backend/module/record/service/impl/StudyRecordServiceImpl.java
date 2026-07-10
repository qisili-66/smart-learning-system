package com.smartlearning.backend.module.record.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.record.entity.StudyRecord;
import com.smartlearning.backend.module.record.mapper.StudyRecordMapper;
import com.smartlearning.backend.module.record.service.StudyRecordService;
import org.springframework.stereotype.Service;

@Service
public class StudyRecordServiceImpl extends ServiceImpl<StudyRecordMapper, StudyRecord> implements StudyRecordService {
}
