package com.gotogether.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleSignInRequest(@NotBlank String idToken) {}
