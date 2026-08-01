package com.gotogether.auth.service;

import java.util.Optional;

/** Verifies a Google Sign-In ID token and extracts the caller's identity. */
public interface GoogleTokenVerifier {

    Optional<GoogleIdentity> verify(String idToken);

    record GoogleIdentity(String googleId, String email) {}
}
