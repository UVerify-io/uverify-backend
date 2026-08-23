package io.uverify.backend.extension.aymvision.profile;

import io.uverify.backend.extension.aymvision.anchor.AymCompletionCertEntity;
import io.uverify.backend.extension.aymvision.anchor.AymCompletionCertRepository;
import io.uverify.backend.extension.aymvision.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerIT {

    private static final String PROFILE_HASH = "bb".repeat(28);
    private static final String PUB_KEY      = "aa".repeat(32);
    private static final String PROFILE_ID   = "profile-a";

    @Mock AymUserProfileRepository profileRepo;
    @Mock AymUserContentRepository contentRepo;
    @Mock AymUserCourseStateRepository courseStateRepo;
    @Mock AymCompletionCertRepository completionCertRepo;

    private ProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProfileController(profileRepo, contentRepo, courseStateRepo, completionCertRepo);
    }

    @Test
    void getProfile_returns404_whenProfileNotFound() {
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getProfile_returnsProfileData_whenFound() {
        AymUserProfileEntity profile =
                new AymUserProfileEntity(PUB_KEY, PROFILE_ID, "salt", PROFILE_HASH);
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.of(profile));

        AymUserContentEntity content =
                new AymUserContentEntity(PUB_KEY, PROFILE_ID, "s1", "VOUCHER");
        when(contentRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of(content));
        when(courseStateRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID))
                .thenReturn(List.of());
        when(completionCertRepo.findById(any())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("profile");
        assertThat(body).doesNotContainKey("completionCertificate");

        @SuppressWarnings("unchecked")
        Map<String, Object> profileData = (Map<String, Object>) body.get("profile");
        assertThat(profileData.get("ownedContent")).isEqualTo(List.of("s1"));
        assertThat(profileData.get("finishedCourses")).isEqualTo(List.of());
        assertThat(profileData.get("badgeCount")).isEqualTo(0);
    }

    @Test
    void getProfile_includesCompletionCertificate_whenPresent() {
        AymUserProfileEntity profile =
                new AymUserProfileEntity(PUB_KEY, PROFILE_ID, "salt", PROFILE_HASH);
        when(profileRepo.findByProfileHash(PROFILE_HASH)).thenReturn(Optional.of(profile));
        when(contentRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID)).thenReturn(List.of());
        when(courseStateRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_ID)).thenReturn(List.of());

        AymCompletionCertEntity cert =
                new AymCompletionCertEntity(PUB_KEY, PROFILE_ID, "a".repeat(64), "https://example.com/verify/" + "a".repeat(64));
        when(completionCertRepo.findById(any())).thenReturn(Optional.of(cert));

        ResponseEntity<?> response = controller.getProfile(PROFILE_HASH);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("completionCertificate");

        @SuppressWarnings("unchecked")
        Map<String, Object> certData = (Map<String, Object>) body.get("completionCertificate");
        assertThat(certData.get("hash")).isEqualTo("a".repeat(64));
        assertThat(certData.get("verifyUrl").toString()).contains("https://example.com/verify/");
    }
}
