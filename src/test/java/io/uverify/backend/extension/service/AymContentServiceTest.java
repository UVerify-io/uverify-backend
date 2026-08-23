/*
 * UVerify Backend
 * Copyright (C) 2025 Fabian Bormann
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package io.uverify.backend.extension.service;

import io.uverify.backend.extension.exception.AlreadyOwnedException;
import io.uverify.backend.extension.entity.AymUserContentEntity;
import io.uverify.backend.extension.repository.AymUserContentRepository;
import io.uverify.backend.extension.entity.AymUserCourseStateEntity;
import io.uverify.backend.extension.repository.AymUserCourseStateRepository;
import io.uverify.backend.extension.entity.AymUserProfileEntity;
import io.uverify.backend.extension.entity.AymUserProfileId;
import io.uverify.backend.extension.repository.AymUserProfileRepository;
import io.uverify.backend.extension.dto.aymvision.CompletionCertificate;
import io.uverify.backend.extension.exception.ProfileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;
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
@SpringBootTest
@EnabledIf(
        expression = "${extensions.aym-vision.enabled}",
        loadContext = true,
        reason = "AYM Vision extension must be enabled for this test"
)
class AymContentServiceTest {

    private static final String PUB_KEY = "aa".repeat(32);
    private static final String PROFILE_A = "profile-a";
    private static final String PROFILE_B = "profile-b";
    private static final String CONTENT_ID = "season-1";
    private static final String SOURCE = "voucher";

    @Mock AymUserProfileRepository profileRepo;
    @Mock AymUserContentRepository contentRepo;
    @Mock AymUserCourseStateRepository courseStateRepo;
    @Mock AymCompletionCertificateService completionCertService;

    private AymContentService service;

    @BeforeEach
    void setUp() {
        service = new AymContentService(profileRepo, contentRepo, courseStateRepo, completionCertService);
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
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_A, CONTENT_ID))
                .thenReturn(false);
        when(contentRepo.existsByPublicKeyAndProfileIdAndContentId(PUB_KEY, PROFILE_B, CONTENT_ID))
                .thenReturn(false);
        when(profileRepo.existsById(any())).thenReturn(false);
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantContent(PUB_KEY, PROFILE_A, CONTENT_ID, SOURCE);

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
        when(completionCertService.checkAndIssue(PUB_KEY, PROFILE_A)).thenReturn(null);

        CompletionCertificate result = service.reportCourseState(PUB_KEY, PROFILE_A, "course-1", "FINISHED");

        assertThat(result).isNull();
        verify(courseStateRepo).save(any());
    }

    @Test
    void reportCourseState_updatesExistingRecord() {
        AymUserCourseStateEntity existing = new AymUserCourseStateEntity(PUB_KEY, PROFILE_A, "course-1", "STARTED");
        when(courseStateRepo.findByPublicKeyAndProfileIdAndCourseId(PUB_KEY, PROFILE_A, "course-1"))
                .thenReturn(Optional.of(existing));
        when(courseStateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(completionCertService.checkAndIssue(PUB_KEY, PROFILE_A)).thenReturn(null);

        service.reportCourseState(PUB_KEY, PROFILE_A, "course-1", "FINISHED");

        assertThat(existing.getStatus()).isEqualTo("FINISHED");
    }

    @Test
    void reportCourseState_returnsCompletionCert_whenAllEpisodesFinished() {
        when(courseStateRepo.findByPublicKeyAndProfileIdAndCourseId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(courseStateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompletionCertificate cert = new CompletionCertificate("abc123", "https://example.com/verify/abc123");
        when(completionCertService.checkAndIssue(PUB_KEY, PROFILE_A)).thenReturn(cert);

        CompletionCertificate result = service.reportCourseState(PUB_KEY, PROFILE_A, "s1e05", "FINISHED");

        assertThat(result).isNotNull();
        assertThat(result.hash()).isEqualTo("abc123");
        assertThat(result.verifyUrl()).contains("https://example.com/verify/");
    }
}
