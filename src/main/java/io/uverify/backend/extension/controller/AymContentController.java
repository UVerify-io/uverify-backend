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

import io.uverify.backend.extension.service.AymContentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.extension.dto.aymvision.ChapterProgressRequest;
import io.uverify.backend.extension.dto.aymvision.CompletionCertificate;
import io.uverify.backend.extension.service.AymHandshakeService;
import io.uverify.backend.extension.dto.aymvision.ContentItemDto;
import io.uverify.backend.extension.dto.aymvision.ContentOwnershipDto;
import io.uverify.backend.extension.dto.aymvision.CourseStateRequest;
import io.uverify.backend.extension.entity.AymChapterProgressEntity;
import io.uverify.backend.extension.exception.InvalidHandshakeException;
import io.uverify.backend.extension.exception.ProfileNotFoundException;
import io.uverify.backend.extension.repository.AymChapterProgressRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/extension/aym-vision")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymContentController {

    static final String HEADER_PUBLIC_KEY = "X-Aym-Public-Key";
    static final String HEADER_NONCE = "X-Aym-Nonce";
    static final String HEADER_SIGNATURE = "X-Aym-Signature";

    private final AymHandshakeService handshakeService;
    private final AymContentService contentService;
    private final AymChapterProgressRepository chapterProgressRepository;
    private final ObjectMapper objectMapper;

    public AymContentController(AymHandshakeService handshakeService,
                             AymContentService contentService,
                             AymChapterProgressRepository chapterProgressRepository,
                             ObjectMapper objectMapper) {
        this.handshakeService = handshakeService;
        this.contentService = contentService;
        this.chapterProgressRepository = chapterProgressRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/content")
    public ResponseEntity<?> listContent(@RequestParam String profileId,
                                         HttpServletRequest request) {
        try {
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
            List<ContentItemDto> items = contentService.getContent(auth.publicKeyHex(), profileId)
                    .stream().map(ContentItemDto::from).toList();
            return ResponseEntity.ok(items);
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (ProfileNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/content/{contentId}")
    public ResponseEntity<ContentOwnershipDto> checkOwnership(@PathVariable String contentId,
                                                              @RequestParam String profileId,
                                                              HttpServletRequest request) {
        try {
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
            boolean owned = contentService.owns(auth.publicKeyHex(), profileId, contentId);
            return ResponseEntity.ok(new ContentOwnershipDto(owned));
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/course-state")
    public ResponseEntity<?> reportCourseState(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
            CourseStateRequest body = objectMapper.readValue(rawBody, CourseStateRequest.class);
            CompletionCertificate cert = contentService.reportCourseState(
                    auth.publicKeyHex(), body.profileId(), body.courseId(), body.status());
            if (cert != null) {
                return ResponseEntity.ok(Map.of(
                        "completionCertificate", Map.of("hash", cert.hash(), "verifyUrl", cert.verifyUrl())));
            }
            return ResponseEntity.ok().build();
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read request body");
        }
    }

    @PostMapping("/chapter-progress")
    public ResponseEntity<?> reportChapterProgress(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
            ChapterProgressRequest body = objectMapper.readValue(rawBody, ChapterProgressRequest.class);
            chapterProgressRepository
                    .findByPublicKeyAndProfileIdAndCourseId(auth.publicKeyHex(), body.profileId(), body.courseId())
                    .ifPresentOrElse(existing -> {
                        if (body.chapterIndex() > existing.getChapterIndex()) {
                            existing.setChapterIndex(body.chapterIndex());
                            existing.setUpdatedAt(Instant.now());
                            chapterProgressRepository.save(existing);
                        }
                    }, () -> chapterProgressRepository.save(
                            new AymChapterProgressEntity(auth.publicKeyHex(), body.profileId(), body.courseId(), body.chapterIndex())));
            return ResponseEntity.ok().build();
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read request body");
        }
    }

    @GetMapping("/chapter-progress")
    public ResponseEntity<?> getChapterProgress(@RequestParam String profileId,
                                                HttpServletRequest request) {
        try {
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
            List<Map<String, Object>> result = chapterProgressRepository
                    .findByPublicKeyAndProfileId(auth.publicKeyHex(), profileId)
                    .stream()
                    .map(e -> Map.<String, Object>of("courseId", e.getCourseId(), "chapterIndex", e.getChapterIndex()))
                    .toList();
            return ResponseEntity.ok(result);
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    private AymHandshakeService.HandshakeResult verifyHandshake(HttpServletRequest request, byte[] body) {
        String publicKey = request.getHeader(HEADER_PUBLIC_KEY);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }
        return handshakeService.verify(publicKey, nonce, signature, method, path, body);
    }
}
