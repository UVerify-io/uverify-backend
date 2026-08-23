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

package io.uverify.backend.extension.controller;

import io.uverify.backend.extension.entity.AymUserContentEntity;
import io.uverify.backend.extension.repository.AymUserContentRepository;
import io.uverify.backend.extension.repository.AymUserCourseStateRepository;
import io.uverify.backend.extension.entity.AymUserProfileEntity;
import io.uverify.backend.extension.repository.AymUserProfileRepository;
import io.uverify.backend.extension.entity.AymCompletionCertEntity;
import io.uverify.backend.extension.repository.AymCompletionCertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;
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
@SpringBootTest
@EnabledIf(
        expression = "${extensions.aym-vision.enabled}",
        loadContext = true,
        reason = "AYM Vision extension must be enabled for this test"
)
class AymProfileControllerIT {

    private static final String PROFILE_HASH = "bb".repeat(28);
    private static final String PUB_KEY      = "aa".repeat(32);
    private static final String PROFILE_ID   = "profile-a";

    @Mock AymUserProfileRepository profileRepo;
    @Mock AymUserContentRepository contentRepo;
    @Mock AymUserCourseStateRepository courseStateRepo;
    @Mock AymCompletionCertRepository completionCertRepo;

    private AymProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new AymProfileController(profileRepo, contentRepo, courseStateRepo, completionCertRepo);
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
