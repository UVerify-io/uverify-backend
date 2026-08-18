package io.uverify.backend.extension.aymvision.profile;

import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorEntity;
import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorRepository;
import io.uverify.backend.extension.aymvision.mpf.MpfProof;
import io.uverify.backend.extension.aymvision.mpf.MpfService;
import io.uverify.backend.extension.aymvision.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerIT {

    private static final String PROFILE_HASH = "bb".repeat(28); // 56 hex = 28 bytes
    private static final String PUB_KEY      = "aa".repeat(32);
    private static final String PROFILE_ID   = "profile-a";
    private static final String ROOT_HEX     = "cc".repeat(32);
    private static final long   TREE_VERSION = 3L;
    private static final String PROOF_HEX    = "dd".repeat(16);
    private static final String TX_HASH      = "tx_anchor_001";

    @Mock AymUserProfileRepository profileRepo;
    @Mock AymUserContentRepository contentRepo;
    @Mock AymUserCourseStateRepository courseStateRepo;
    @Mock MpfService mpfService;
    @Mock AymMpfAnchorRepository anchorRepo;

    private ProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProfileController(
                profileRepo, contentRepo, courseStateRepo, mpfService, anchorRepo);
    }

    // ── /profile/{profileHash} ────────────────────────────────────────────────

    @Test
    void getProfile_returns404_whenProfileNotFound() {
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getProfile_returnsProfileWithMpfProofAndAnchorInfo_whenAnchored() {
        AymUserProfileEntity profile =
                new AymUserProfileEntity(PUB_KEY, PROFILE_ID, "salt", PROFILE_HASH);
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.of(profile));

        AymUserContentEntity content =
                new AymUserContentEntity(PUB_KEY, PROFILE_ID, "s1e01", "VOUCHER");
        when(contentRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of(content));
        when(courseStateRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of());

        when(mpfService.proofFor(PROFILE_HASH))
                .thenReturn(new MpfProof(PROOF_HEX, ROOT_HEX, TREE_VERSION));

        AymMpfAnchorEntity anchor =
                new AymMpfAnchorEntity(TREE_VERSION, ROOT_HEX, TX_HASH, Instant.now());
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.of(anchor));

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("profile");
        assertThat(body).containsKey("mpf");

        @SuppressWarnings("unchecked")
        Map<String, Object> profileData = (Map<String, Object>) body.get("profile");
        assertThat(profileData.get("ownedContent")).isEqualTo(List.of("s1e01"));
        assertThat(profileData.get("finishedCourses")).isEqualTo(List.of());
        assertThat(profileData.get("badgeCount")).isEqualTo(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> mpf = (Map<String, Object>) body.get("mpf");
        assertThat(mpf.get("proof")).isEqualTo(PROOF_HEX);
        assertThat(mpf.get("root")).isEqualTo(ROOT_HEX);
        assertThat(mpf.get("treeVersion")).isEqualTo(TREE_VERSION);
        assertThat(mpf.get("anchorTxHash")).isEqualTo(TX_HASH);
        assertThat(mpf.get("anchoredAt")).isNotNull();
    }

    @Test
    void getProfile_anchorTxHashNull_whenTreeVersionNewerThanLastAnchor() {
        AymUserProfileEntity profile =
                new AymUserProfileEntity(PUB_KEY, PROFILE_ID, "salt", PROFILE_HASH);
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.of(profile));
        when(contentRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of());
        when(courseStateRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of());

        // Tree is at version 5, but last anchor was at version 3 → pending
        when(mpfService.proofFor(PROFILE_HASH))
                .thenReturn(new MpfProof(PROOF_HEX, ROOT_HEX, 5L));

        AymMpfAnchorEntity oldAnchor =
                new AymMpfAnchorEntity(3L, "old-root", TX_HASH, Instant.now());
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.of(oldAnchor));

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        @SuppressWarnings("unchecked")
        Map<String, Object> mpf =
                (Map<String, Object>) ((Map<?, ?>) response.getBody()).get("mpf");
        assertThat(mpf.get("anchorTxHash")).isNull();
        assertThat(mpf.get("anchoredAt")).isNull();
        assertThat(mpf.get("treeVersion")).isEqualTo(5L);
    }

    @Test
    void getProfile_anchorTxHashNull_whenNoAnchorYet() {
        AymUserProfileEntity profile =
                new AymUserProfileEntity(PUB_KEY, PROFILE_ID, "salt", PROFILE_HASH);
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.of(profile));
        when(contentRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of());
        when(courseStateRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of());
        when(mpfService.proofFor(PROFILE_HASH))
                .thenReturn(new MpfProof(PROOF_HEX, ROOT_HEX, 1L));
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        @SuppressWarnings("unchecked")
        Map<String, Object> mpf =
                (Map<String, Object>) ((Map<?, ?>) response.getBody()).get("mpf");
        assertThat(mpf.get("anchorTxHash")).isNull();
        assertThat(mpf.get("anchoredAt")).isNull();
    }

    // ── /mpf/root ─────────────────────────────────────────────────────────────

    @Test
    void getMpfRoot_returnsCurrentRootAndAnchorInfo() {
        when(mpfService.currentRoot()).thenReturn(ROOT_HEX);
        when(mpfService.currentTreeVersion()).thenReturn(TREE_VERSION);
        AymMpfAnchorEntity anchor =
                new AymMpfAnchorEntity(TREE_VERSION, ROOT_HEX, TX_HASH, Instant.now());
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.of(anchor));

        ResponseEntity<?> response = controller.getMpfRoot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("root")).isEqualTo(ROOT_HEX);
        assertThat(body.get("treeVersion")).isEqualTo(TREE_VERSION);
        assertThat(body.get("anchorTxHash")).isEqualTo(TX_HASH);
        assertThat(body.get("anchoredAt")).isNotNull();
    }

    @Test
    void getMpfRoot_anchorTxHashNull_whenNoAnchorYet() {
        when(mpfService.currentRoot()).thenReturn(ROOT_HEX);
        when(mpfService.currentTreeVersion()).thenReturn(1L);
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getMpfRoot();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("anchorTxHash")).isNull();
        assertThat(body.get("anchoredAt")).isNull();
    }
}
