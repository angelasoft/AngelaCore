package com.angelasoft.admin.auth;

import java.time.LocalDate;

/** 모바일 활동수첩 회원가입 폼 제출 DTO. */
public record AppSignUpRequest(
        String name,
        String baptismalName,
        String phoneNumber,
        LocalDate birthDate,
        String gender,
        boolean agreedTerms,
        boolean agreedPrivacy,
        boolean agreedMarketing
) {
}
