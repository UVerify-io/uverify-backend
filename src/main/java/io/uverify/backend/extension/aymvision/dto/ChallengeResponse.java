package io.uverify.backend.extension.aymvision.dto;

import java.time.Instant;

public record ChallengeResponse(String nonce, Instant expiresAt) {}
