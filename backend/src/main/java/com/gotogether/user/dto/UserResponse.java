package com.gotogether.user.dto;

import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String phoneNumber,
        String email,
        UserStatus status,
        VerificationLevel verificationLevel,
        AccountRole role,
        OffsetDateTime createdAt) {}
