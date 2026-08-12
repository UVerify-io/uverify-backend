package io.uverify.backend.extension.aymvision.profile;

import io.uverify.backend.extension.aymvision.user.AymUserContentEntity;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateEntity;
import io.uverify.backend.extension.aymvision.user.AymUserProfileEntity;
import io.uverify.backend.extension.aymvision.user.AymUserProfileRepository;
import io.uverify.backend.extension.aymvision.user.AymUserContentRepository;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym")
public class ProfileController {

    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;

    public ProfileController(AymUserProfileRepository profileRepo,
                             AymUserContentRepository contentRepo,
                             AymUserCourseStateRepository courseStateRepo) {
        this.profileRepo = profileRepo;
        this.contentRepo = contentRepo;
        this.courseStateRepo = courseStateRepo;
    }

    @GetMapping("/profile/{profileHash}")
    public ResponseEntity<?> getProfile(@PathVariable String profileHash) {
        return profileRepo.findByProfileHash(profileHash)
                .map(profile -> buildResponse(profile))
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

        // MPF proof fields — null until B5 is implemented
        Map<String, Object> mpf = new java.util.LinkedHashMap<>();
        mpf.put("proof", null);
        mpf.put("root", null);
        mpf.put("treeVersion", null);
        mpf.put("anchorTxHash", null);
        mpf.put("anchoredAt", null);

        return ResponseEntity.ok(Map.of("profile", profileData, "mpf", mpf));
    }
}
