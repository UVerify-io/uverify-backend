package io.uverify.backend.extension.aymvision.user;

import io.uverify.backend.extension.aymvision.exception.ProfileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    private static final String PUB_KEY = "aa".repeat(32);
    private static final String PROFILE_A = "profile-a";
    private static final String PROFILE_B = "profile-b";
    private static final String CONTENT_ID = "season-1";
    private static final String SOURCE = "voucher";

    @Mock AymUserProfileRepository profileRepo;
    @Mock AymUserContentRepository contentRepo;
    @Mock AymUserCourseStateRepository courseStateRepo;

    private ContentService service;

    @BeforeEach
    void setUp() {
        service = new ContentService(profileRepo, contentRepo, courseStateRepo);
    }

    // ── getContent ────────────────────────────────────────────────────────────

    @Test
    void getContent_throwsProfileNotFound_whenNoProfileExists() {
        when(profileRepo.existsById(new AymUserProfileId(PUB_KEY, PROFILE_A))).thenReturn(false);

        assertThatThrownBy(() -> service.getContent(PUB_KEY, PROFILE_A))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void getContent_returnsContentList_whenProfileExists() {
        when(profileRepo.existsById(new AymUserProfileId(PUB_KEY, PROFILE_A))).thenReturn(true);
        AymUserContentEntity item = new AymUserContentEntity(PUB_KEY, PROFILE_A, CONTENT_ID, SOURCE);
        when(contentRepo.findByPublicKeyAndProfileId(PUB_KEY, PROFILE_A)).thenReturn(List.of(item));

        List<AymUserContentEntity> result = service.getContent(PUB_KEY, PROFILE_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContentId()).isEqualTo(CONTENT_ID);
    }

    // ── owns ──────────────────────────────────────────────────────────────────

    @Test
    void owns_returnsFalse_whenNotOwned() {
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_A, CONTENT_ID))
                .thenReturn(false);

        assertThat(service.owns(PUB_KEY, PROFILE_A, CONTENT_ID)).isFalse();
    }

    @Test
    void owns_returnsTrue_afterGrant() {
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_A, CONTENT_ID))
                .thenReturn(true);

        assertThat(service.owns(PUB_KEY, PROFILE_A, CONTENT_ID)).isTrue();
    }

    // ── grantContent ──────────────────────────────────────────────────────────

    @Test
    void grantContent_throwsAlreadyOwned_onDoubleGrant() {
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_A, CONTENT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.grantContent(PUB_KEY, PROFILE_A, CONTENT_ID, SOURCE))
                .isInstanceOf(AlreadyOwnedException.class);
    }

    @Test
    void grantContent_createsProfileRowWithSalt_whenNoProfile() {
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(any(), any(), any())).thenReturn(false);
        when(profileRepo.existsById(any())).thenReturn(false);
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantContent(PUB_KEY, PROFILE_A, CONTENT_ID, SOURCE);

        ArgumentCaptor<AymUserProfileEntity> profileCaptor = ArgumentCaptor.forClass(AymUserProfileEntity.class);
        verify(profileRepo).save(profileCaptor.capture());
        AymUserProfileEntity saved = profileCaptor.getValue();

        assertThat(saved.getPublicKey()).isEqualTo(PUB_KEY);
        assertThat(saved.getProfileId()).isEqualTo(PROFILE_A);
        assertThat(saved.getSalt()).matches("[0-9a-f]{64}"); // 32 bytes = 64 hex chars
    }

    @Test
    void grantContent_doesNotCreateProfile_whenAlreadyExists() {
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(any(), any(), any())).thenReturn(false);
        when(profileRepo.existsById(any())).thenReturn(true);
        when(contentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantContent(PUB_KEY, PROFILE_A, CONTENT_ID, SOURCE);

        verify(profileRepo, never()).save(any());
    }

    // ── sibling profiles ──────────────────────────────────────────────────────

    @Test
    void grantContent_twoProfilesUnderSameKey_areIndependent() {
        // profile-a owns content, profile-b does not
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_A, CONTENT_ID))
                .thenReturn(false);
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_B, CONTENT_ID))
                .thenReturn(false);
        when(profileRepo.existsById(any())).thenReturn(false);
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantContent(PUB_KEY, PROFILE_A, CONTENT_ID, SOURCE);

        // profile-b NOT granted
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_A, CONTENT_ID))
                .thenReturn(true);
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_B, CONTENT_ID))
                .thenReturn(false);

        assertThat(service.owns(PUB_KEY, PROFILE_A, CONTENT_ID)).isTrue();
        assertThat(service.owns(PUB_KEY, PROFILE_B, CONTENT_ID)).isFalse();
    }

    // ── reportCourseState ─────────────────────────────────────────────────────

    @Test
    void reportCourseState_createsNewRecord_whenNoneExists() {
        when(courseStateRepo.findByPublicKeyAndProfileIdAndCourseId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(courseStateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AymUserCourseStateEntity result = service.reportCourseState(PUB_KEY, PROFILE_A, "course-1", "completed");

        assertThat(result.getStatus()).isEqualTo("completed");
        verify(courseStateRepo).save(any());
    }

    @Test
    void reportCourseState_updatesExistingRecord() {
        AymUserCourseStateEntity existing = new AymUserCourseStateEntity(PUB_KEY, PROFILE_A, "course-1", "started");
        when(courseStateRepo.findByPublicKeyAndProfileIdAndCourseId(PUB_KEY, PROFILE_A, "course-1"))
                .thenReturn(Optional.of(existing));
        when(courseStateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AymUserCourseStateEntity result = service.reportCourseState(PUB_KEY, PROFILE_A, "course-1", "completed");

        assertThat(result.getStatus()).isEqualTo("completed");
    }
}
