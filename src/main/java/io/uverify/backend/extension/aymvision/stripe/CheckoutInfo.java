package io.uverify.backend.extension.aymvision.stripe;

public record CheckoutInfo(
        String sessionId,
        String paymentStatus,
        String clientReferenceId,
        String productId) {}
