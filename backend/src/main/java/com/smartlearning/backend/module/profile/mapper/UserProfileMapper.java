package com.smartlearning.backend.module.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlearning.backend.module.profile.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
