package com.gotogether.auth.dto;

import java.util.UUID;

public record AuthResponse(String accessToken, String refreshToken, UUID userId, boolean newUser) {}
