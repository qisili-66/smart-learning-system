package com.smartlearning.backend.module.profile.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlearning.backend.module.profile.entity.UserProfile;

import java.util.List;
import java.util.Map;

public interface UserProfileService extends IService<UserProfile> {

    Map<String, Object> overview(Long userId, boolean refresh);

    Map<String, Object> metrics(Long userId);

    UserProfile refreshProfile(Long userId);

    void refreshAfterLearningEvent(Long userId);

    List<String> weakPoints(Long userId, int limit);

    Map<String, Object> correctProfile(Long userId, Map<String, Object> request);

    List<Map<String, Object>> correctionLogs(Long userId, int limit);

    Map<String, Object> collectBehavior(Long userId, Map<String, Object> request);

    void collectResourceView(Long userId, Long resourceId);

    void collectQaInteraction(Long userId);
}
