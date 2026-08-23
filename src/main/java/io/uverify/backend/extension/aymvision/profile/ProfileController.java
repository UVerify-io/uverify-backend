package io.uverify.backend.extension.aymvision.profile;

import io.uverify.backend.extension.aymvision.anchor.AymCompletionCertEntity;
import io.uverify.backend.extension.aymvision.anchor.AymCompletionCertRepository;
import io.uverify.backend.extension.aymvision.user.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class ProfileController {

    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;
    private final AymCompletionCertRepository completionCertRepo;

    public ProfileController(AymUserProfileRepository profileRepo,
                             AymUserContentRepository contentRepo,
                             AymUserCourseStateRepository courseStateRepo,
                             AymCompletionCertRepository completionCertRepo) {
        this.profileRepo = profileRepo;
        this.contentRepo = contentRepo;
        this.courseStateRepo = courseStateRepo;
        this.completionCertRepo = completionCertRepo;
    }

    @GetMapping("/profile/{profileHash}")
    public ResponseEntity<?> getProfile(@PathVariable String profileHash) {
        return profileRepo.findByProfileHash(profileHash)
                .map(this::buildResponse)
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> buildResponse(AymUserProfileEntity profile) {
        List<String> ownedContent = contentRepo
                .findByPublicKeyAndProfileId(profile.getPublicKey(), profile.getProfileId())
                .stream().map(AymUserContentEntity::getContentId).sorted().toList();

        List<String> finishedCourses = courseStateRepo
                .findByPublicKeyAndProfileId(profile.getPublicKey(), profile.getProfileId())
                .stream()
                .filter(s -> "FINISHED".equalsIgnoreCase(s.getStatus()))
                .map(AymUserCourseStateEntity::getCourseId).sorted().toList();

        Map<String, Object> profileData = Map.of(
                "ownedContent", ownedContent,
                "finishedCourses", finishedCourses,
                "badgeCount", finishedCourses.size()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", profileData);

        Optional<AymCompletionCertEntity> cert = completionCertRepo.findById(
                new AymUserProfileId(profile.getPublicKey(), profile.getProfileId()));
        cert.ifPresent(c -> response.put("completionCertificate", Map.of(
                "hash", c.getCertHash(),
                "verifyUrl", c.getVerifyUrl(),
                "issuedAt", c.getIssuedAt())));

        return ResponseEntity.ok(response);
    }
}
