package com.smartlearning.backend.module.profile.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.module.profile.entity.UserProfileCorrectionLog;
import com.smartlearning.backend.module.profile.mapper.UserProfileCorrectionLogMapper;
import com.smartlearning.backend.module.profile.service.UserProfileCorrectionLogService;
import org.springframework.stereotype.Service;

@Service
public class UserProfileCorrectionLogServiceImpl
        extends ServiceImpl<UserProfileCorrectionLogMapper, UserProfileCorrectionLog>
        implements UserProfileCorrectionLogService {
}
