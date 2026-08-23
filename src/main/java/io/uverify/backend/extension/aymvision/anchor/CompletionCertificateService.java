package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateEntity;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateRepository;
import io.uverify.backend.extension.aymvision.user.AymUserProfileId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class CompletionCertificateService {

    private static final List<String> S1_EPISODES = List.of("s1e01", "s1e02", "s1e03", "s1e04", "s1e05");

    private final AymUserCourseStateRepository courseStateRepo;
    private final AymCompletionCertRepository certRepo;
    private final UVerifyIssuer issuer;
    private final AymVisionProperties properties;

    public CompletionCertificateService(AymUserCourseStateRepository courseStateRepo,
                                        AymCompletionCertRepository certRepo,
                                        UVerifyIssuer issuer,
                                        AymVisionProperties properties) {
        this.courseStateRepo = courseStateRepo;
        this.certRepo = certRepo;
        this.issuer = issuer;
        this.properties = properties;
    }

    /**
     * Returns the S1 completion certificate if all 5 episodes are finished; null otherwise.
     * Idempotent: if already issued, returns the existing certificate without re-issuing.
     */
    @Transactional
    public CompletionCertificate checkAndIssue(String publicKeyHex, String profileId) {
        AymUserProfileId id = new AymUserProfileId(publicKeyHex, profileId);

        return certRepo.findById(id)
                .map(e -> new CompletionCertificate(e.getCertHash(), e.getVerifyUrl()))
                .orElseGet(() -> issueIfComplete(publicKeyHex, profileId, id));
    }

    private CompletionCertificate issueIfComplete(String publicKeyHex, String profileId, AymUserProfileId id) {
        Set<String> finished = courseStateRepo.findByPublicKeyAndProfileId(publicKeyHex, profileId)
                .stream()
                .filter(s -> "FINISHED".equalsIgnoreCase(s.getStatus()))
                .map(AymUserCourseStateEntity::getCourseId)
                .collect(Collectors.toSet());

        if (!finished.containsAll(S1_EPISODES)) {
            return null;
        }

        String hash = sha256Hex((publicKeyHex + profileId + "s1-complete").getBytes(StandardCharsets.UTF_8));
        String verifyUrl = properties.getUiBaseUrl() + "/verify/" + hash
                + "?pk=" + publicKeyHex + "&profileId=" + profileId + "&season=s1";

        issuer.issue(hash, Map.of("profileId", profileId, "season", "s1", "type", "completion"));
        log.info("Issued S1 completion certificate for profile {}", profileId);

        certRepo.save(new AymCompletionCertEntity(publicKeyHex, profileId, hash, verifyUrl));
        return new CompletionCertificate(hash, verifyUrl);
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
