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
import io.uverify.backend.extension.entity.AymUserCourseStateEntity;
import io.uverify.backend.extension.repository.AymUserCourseStateRepository;
import io.uverify.backend.extension.entity.AymUserProfileEntity;
import io.uverify.backend.extension.entity.AymUserProfileId;
import io.uverify.backend.extension.repository.AymUserProfileRepository;
import io.uverify.backend.extension.entity.AymCompletionCertEntity;
import io.uverify.backend.extension.repository.AymCompletionCertRepository;
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
@RequestMapping("/api/v1/extension/aym-vision")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymProfileController {

    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;
    private final AymCompletionCertRepository completionCertRepo;

    public AymProfileController(AymUserProfileRepository profileRepo,
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
