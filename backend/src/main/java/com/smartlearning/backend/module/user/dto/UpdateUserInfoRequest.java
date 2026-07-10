package com.smartlearning.backend.module.user.dto;

import lombok.Data;

@Data
public class UpdateUserInfoRequest {

    private String realName;

    private String grade;

    private String subject;

    private String phone;
}
