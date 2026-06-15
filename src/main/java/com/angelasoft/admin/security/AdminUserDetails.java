package com.angelasoft.admin.security;

import com.angelasoft.admin.user.UserRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AdminUserDetails {
    private final Long userId;
    private final String email;
    private final String name;
    private final UserRole role;
}
