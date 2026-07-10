package com.smartlearning.backend.module.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoResponse {

    private Long userId;

    private String username;

    private String realName;

    private Integer role;

    private String grade;

    private String subject;

    private String phone;

    private String createTime;
}
