package com.smartlearning.backend.module.qa.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.qa.dto.TextQARequest;
import com.smartlearning.backend.module.qa.entity.QaConversation;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

public interface QaConversationService extends IService<QaConversation> {

    Result<?> textQuestionAnswer(Long userId, TextQARequest request);

    Result<?> imageQuestionAnswer(Long userId, MultipartFile file, String conversationId, String subject, Boolean confirmAnswer);

    Result<?> voiceQuestionAnswer(Long userId,
                                  MultipartFile file,
                                  String conversationId,
                                  String subject,
                                  String recognizedText,
                                  String correctedText,
                                  Boolean confirmAnswer);

    PageVO<Map<String, Object>> conversations(Long userId, Integer pageNum, Integer pageSize);

    Map<String, Object> detail(Long userId, String conversationId);

    void deleteConversation(Long userId, String conversationId);

    Map<String, Object> evaluation(Long userId, Integer days);

    Path audioFile(Long userId, String conversationId, String fileName);
}
