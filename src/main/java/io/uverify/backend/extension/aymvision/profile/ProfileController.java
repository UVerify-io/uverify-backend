package io.uverify.backend.extension.aymvision.profile;

import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorEntity;
import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorRepository;
import io.uverify.backend.extension.aymvision.mpf.MpfProof;
import io.uverify.backend.extension.aymvision.mpf.MpfService;
import io.uverify.backend.extension.aymvision.user.AymUserContentEntity;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateEntity;
import io.uverify.backend.extension.aymvision.user.AymUserProfileEntity;
import io.uverify.backend.extension.aymvision.user.AymUserProfileRepository;
import io.uverify.backend.extension.aymvision.user.AymUserContentRepository;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym")
public class ProfileController {

    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;
    private final MpfService mpfService;
    private final AymMpfAnchorRepository anchorRepo;

    public ProfileController(AymUserProfileRepository profileRepo,
                             AymUserContentRepository contentRepo,
                             AymUserCourseStateRepository courseStateRepo,
                             MpfService mpfService,
                             AymMpfAnchorRepository anchorRepo) {
        this.profileRepo = profileRepo;
        this.contentRepo = contentRepo;
        this.courseStateRepo = courseStateRepo;
        this.mpfService = mpfService;
        this.anchorRepo = anchorRepo;
    }

    @GetMapping("/profile/{profileHash}")
    public ResponseEntity<?> getProfile(@PathVariable String profileHash) {
        return profileRepo.findByProfileHash(profileHash)
                .map(profile -> buildResponse(profile))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mpf/root")
    public ResponseEntity<?> getMpfRoot() {
        AymMpfAnchorEntity lastAnchor = anchorRepo.findTopByOrderByTreeVersionDesc().orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("root", mpfService.currentRoot());
        response.put("treeVersion", mpfService.currentTreeVersion());
        response.put("anchorTxHash", lastAnchor != null ? lastAnchor.getUverifyTxHash() : null);
        response.put("anchoredAt", lastAnchor != null ? lastAnchor.getAnchoredAt() : null);
        return ResponseEntity.ok(response);
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

        MpfProof proof = mpfService.proofFor(profile.getProfileHash());

        // anchorTxHash is null when the tree has advanced beyond the last anchored version
        AymMpfAnchorEntity lastAnchor = anchorRepo.findTopByOrderByTreeVersionDesc().orElse(null);
        String anchorTxHash = null;
        Object anchoredAt = null;
        if (lastAnchor != null && lastAnchor.getTreeVersion() >= proof.treeVersion()) {
            anchorTxHash = lastAnchor.getUverifyTxHash();
            anchoredAt = lastAnchor.getAnchoredAt();
        }

        Map<String, Object> mpf = new LinkedHashMap<>();
        mpf.put("proof", proof.proofHex());
        mpf.put("root", proof.root());
        mpf.put("treeVersion", proof.treeVersion());
        mpf.put("anchorTxHash", anchorTxHash);
        mpf.put("anchoredAt", anchoredAt);

        return ResponseEntity.ok(Map.of("profile", profileData, "mpf", mpf));
    }
}
