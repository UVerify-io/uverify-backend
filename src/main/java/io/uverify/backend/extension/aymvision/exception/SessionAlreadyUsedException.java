package io.uverify.backend.extension.aymvision.exception;

public class SessionAlreadyUsedException extends RuntimeException {
    public SessionAlreadyUsedException(String sessionId) {
        super("Session already used: " + sessionId);
    }
}
