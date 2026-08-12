package io.uverify.backend.extension.aymvision.stripe;

public interface StripeGateway {
    CheckoutInfo retrieveSession(String sessionId);
}
