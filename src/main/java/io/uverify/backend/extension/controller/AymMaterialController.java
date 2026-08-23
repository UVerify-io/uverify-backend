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

import io.uverify.backend.extension.service.AymMaterialService;
import io.uverify.backend.extension.service.AymHandshakeService;
import io.uverify.backend.extension.exception.InvalidHandshakeException;
import io.uverify.backend.extension.service.AymContentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/extension/aym-vision/material")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymMaterialController {

    private static final String HEADER_PUBLIC_KEY = "X-Aym-Public-Key";
    private static final String HEADER_NONCE = "X-Aym-Nonce";
    private static final String HEADER_SIGNATURE = "X-Aym-Signature";

    private final AymHandshakeService handshakeService;
    private final AymContentService contentService;
    private final AymMaterialService materialService;

    public AymMaterialController(AymHandshakeService handshakeService,
                              AymContentService contentService,
                              AymMaterialService materialService) {
        this.handshakeService = handshakeService;
        this.contentService = contentService;
        this.materialService = materialService;
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getMaterial(@PathVariable String courseId,
                                         @RequestParam String profileId,
                                         HttpServletRequest request) {
        try {
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
            if (!contentService.owns(auth.publicKeyHex(), profileId, courseId)) {
                return ResponseEntity.status(403).body("Access denied: content not owned");
            }
            AymMaterialService.CachedBundle bundle = materialService.getBundle(courseId);
            return ResponseEntity.ok(Map.of(
                    "courseId", bundle.courseId(),
                    "version", bundle.version(),
                    "payload", bundle.payload()
            ));
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to fetch material for {}", courseId, e);
            return ResponseEntity.status(502).body("Could not retrieve material");
        }
    }

    @GetMapping("/{courseId}/version")
    public ResponseEntity<?> getVersion(@PathVariable String courseId,
                                         @RequestParam String profileId,
                                         HttpServletRequest request) {
        try {
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
            if (!contentService.owns(auth.publicKeyHex(), profileId, courseId)) {
                return ResponseEntity.status(403).body("Access denied: content not owned");
            }
            return ResponseEntity.ok(Map.of("version", materialService.getVersion(courseId)));
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to fetch version for {}", courseId, e);
            return ResponseEntity.status(502).body("Could not retrieve version");
        }
    }

    private AymHandshakeService.HandshakeResult verifyHandshake(HttpServletRequest request, byte[] body) {
        return handshakeService.verify(
                request.getHeader(HEADER_PUBLIC_KEY),
                request.getHeader(HEADER_NONCE),
                request.getHeader(HEADER_SIGNATURE),
                request.getMethod(),
                request.getRequestURI(),
                body);
    }
}
