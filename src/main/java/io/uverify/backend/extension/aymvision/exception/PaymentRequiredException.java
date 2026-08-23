package io.uverify.backend.extension.aymvision.exception;

public class PaymentRequiredException extends RuntimeException {
    public PaymentRequiredException(String sessionId) {
        super("Payment not completed for session: " + sessionId);
    }
}
