package io.uverify.backend.extension.aymvision.user;

import io.uverify.backend.extension.aymvision.exception.ProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ContentService {

    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    public ContentService(AymUserProfileRepository profileRepo,
                          AymUserContentRepository contentRepo,
                          AymUserCourseStateRepository courseStateRepo) {
        this.profileRepo = profileRepo;
        this.contentRepo = contentRepo;
        this.courseStateRepo = courseStateRepo;
    }

    public List<AymUserContentEntity> getContent(String publicKeyHex, String profileId) {
        if (!profileRepo.existsById(new AymUserProfileId(publicKeyHex, profileId))) {
            throw new ProfileNotFoundException(publicKeyHex, profileId);
        }
        return contentRepo.findByPublicKeyAndProfileId(publicKeyHex, profileId);
    }

    public boolean owns(String publicKeyHex, String profileId, String contentId) {
        return contentRepo.existsByPublicKeyAndProfileIdAndContentId(publicKeyHex, profileId, contentId);
    }

    @Transactional
    public AymUserContentEntity grantContent(String publicKeyHex, String profileId, String contentId, String source) {
        if (contentRepo.existsByPublicKeyAndProfileIdAndContentId(publicKeyHex, profileId, contentId)) {
            throw new AlreadyOwnedException(contentId, profileId);
        }
        ensureProfile(publicKeyHex, profileId);
        return contentRepo.save(new AymUserContentEntity(publicKeyHex, profileId, contentId, source));
    }

    @Transactional
    public AymUserCourseStateEntity reportCourseState(String publicKeyHex, String profileId,
                                                      String courseId, String status) {
        Optional<AymUserCourseStateEntity> existing =
                courseStateRepo.findByPublicKeyAndProfileIdAndCourseId(publicKeyHex, profileId, courseId);
        AymUserCourseStateEntity entity = existing
                .orElse(new AymUserCourseStateEntity(publicKeyHex, profileId, courseId, status));
        entity.setStatus(status);
        entity.setReportedAt(Instant.now());
        return courseStateRepo.save(entity);
    }

    private void ensureProfile(String publicKeyHex, String profileId) {
        if (!profileRepo.existsById(new AymUserProfileId(publicKeyHex, profileId))) {
            byte[] saltBytes = new byte[32];
            secureRandom.nextBytes(saltBytes);
            String salt = HexFormat.of().formatHex(saltBytes);
            profileRepo.save(new AymUserProfileEntity(publicKeyHex, profileId, salt));
        }
    }
}
