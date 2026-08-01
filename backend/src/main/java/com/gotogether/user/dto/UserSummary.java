package com.gotogether.user.dto;

import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import java.util.UUID;

/**
 * The user module's cross-module-safe view of a user — this, not the
 * {@code User} entity itself, is what other modules (auth, trip, etc.)
 * receive from {@code UserService}. Keeps entity classes genuinely private
 * to their owning module (enforced by {@code ArchitectureTest}).
 */
public record UserSummary(UUID id, AccountRole role, UserStatus status, VerificationLevel verificationLevel) {

    public boolean isActive() {
        return status != UserStatus.SUSPENDED;
    }
}
