package io.uverify.backend.extension.aymvision.exception;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(String publicKeyHex, String profileId) {
        super("No profile found for key=" + publicKeyHex + " profileId=" + profileId);
    }
}
