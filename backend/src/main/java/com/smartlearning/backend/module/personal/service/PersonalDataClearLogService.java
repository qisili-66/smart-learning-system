package com.smartlearning.backend.module.personal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.module.personal.entity.PersonalDataClearLog;

import java.util.Map;

public interface PersonalDataClearLogService extends IService<PersonalDataClearLog> {

    Map<String, Object> clearPersonalData(Long userId, Map<String, String> request);

    Map<String, Object> listClearLogs(Long userId);

    PageVO<Map<String, Object>> adminClearLogs(Long userId, Integer pageNum, Integer pageSize);
}
