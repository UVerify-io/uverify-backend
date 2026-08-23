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
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymContentService {

    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;
    private final AymCompletionCertificateService completionCertService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AymContentService(AymUserProfileRepository profileRepo,
                          AymUserContentRepository contentRepo,
                          AymUserCourseStateRepository courseStateRepo,
                          AymCompletionCertificateService completionCertService) {
        this.profileRepo = profileRepo;
        this.contentRepo = contentRepo;
        this.courseStateRepo = courseStateRepo;
        this.completionCertService = completionCertService;
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
    public CompletionCertificate reportCourseState(String publicKeyHex, String profileId,
                                                    String courseId, String status) {
        Optional<AymUserCourseStateEntity> existing =
                courseStateRepo.findByPublicKeyAndProfileIdAndCourseId(publicKeyHex, profileId, courseId);
        AymUserCourseStateEntity entity = existing
                .orElse(new AymUserCourseStateEntity(publicKeyHex, profileId, courseId, status));
        entity.setStatus(status);
        entity.setReportedAt(Instant.now());
        courseStateRepo.save(entity);
        return completionCertService.checkAndIssue(publicKeyHex, profileId);
    }

    private void ensureProfile(String publicKeyHex, String profileId) {
        if (!profileRepo.existsById(new AymUserProfileId(publicKeyHex, profileId))) {
            byte[] saltBytes = new byte[32];
            secureRandom.nextBytes(saltBytes);
            String salt = HexFormat.of().formatHex(saltBytes);
            String hash = blake2b224Hex((publicKeyHex + profileId).getBytes(StandardCharsets.UTF_8));
            profileRepo.save(new AymUserProfileEntity(publicKeyHex, profileId, salt, hash));
        }
    }

    static String blake2b224Hex(byte[] data) {
        Blake2bDigest digest = new Blake2bDigest(224);
        digest.update(data, 0, data.length);
        byte[] result = new byte[28];
        digest.doFinal(result, 0);
        return HexFormat.of().formatHex(result);
    }
}
