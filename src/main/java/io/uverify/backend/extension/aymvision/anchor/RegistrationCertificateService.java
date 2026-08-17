package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.user.AymUserProfileId;
import io.uverify.backend.extension.aymvision.user.AymUserProfileRepository;
import io.uverify.backend.extension.aymvision.voucher.RegistrationCertificate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class RegistrationCertificateService {

    private final AymUserProfileRepository profileRepo;
    private final AymVisionProperties properties;

    public RegistrationCertificateService(AymUserProfileRepository profileRepo,
                                          AymVisionProperties properties) {
        this.profileRepo = profileRepo;
        this.properties = properties;
    }

    /**
     * Builds the registration certificate for the first ownership event of (publicKey, profileId).
     * hash = sha256(publicKeyHex + profileId + saltHex)
     * verifyUrl = {ui-base-url}/verify/{hash}?pk={pk}&profileId={profileId}&salt={salt}
     */
    public RegistrationCertificate register(String publicKeyHex, String profileId) {
        var profile = profileRepo.findById(new AymUserProfileId(publicKeyHex, profileId))
                .orElseThrow(() -> new IllegalStateException(
                        "Profile not found for registration: " + publicKeyHex + "/" + profileId));
        String salt = profile.getSalt();
        String hash = sha256Hex((publicKeyHex + profileId + salt).getBytes(StandardCharsets.UTF_8));
        String verifyUrl = properties.getUiBaseUrl() + "/verify/" + hash
                + "?pk=" + publicKeyHex + "&profileId=" + profileId + "&salt=" + salt;
        return new RegistrationCertificate(hash, salt, verifyUrl);
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
