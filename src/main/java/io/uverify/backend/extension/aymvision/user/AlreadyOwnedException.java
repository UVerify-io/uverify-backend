package io.uverify.backend.extension.aymvision.user;

public class AlreadyOwnedException extends RuntimeException {
    public AlreadyOwnedException(String contentId, String profileId) {
        super("Content " + contentId + " already owned by profile " + profileId);
    }
}
