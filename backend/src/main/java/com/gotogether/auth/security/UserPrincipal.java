package com.gotogether.auth.security;

import com.gotogether.user.entity.AccountRole;
import java.util.UUID;

/** The authenticated caller, set as the {@code Authentication} principal by {@link JwtAuthenticationFilter}. */
public record UserPrincipal(UUID userId, AccountRole role) {}
