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

import io.uverify.backend.extension.service.AymHandshakeService;
import io.uverify.backend.extension.dto.aymvision.ChallengeRequest;
import io.uverify.backend.extension.dto.aymvision.ChallengeResponse;
import io.uverify.backend.extension.exception.InvalidHandshakeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/extension/aym-vision")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymChallengeController {

    private final AymHandshakeService handshakeService;

    public AymChallengeController(AymHandshakeService handshakeService) {
        this.handshakeService = handshakeService;
    }

    @PostMapping("/challenge")
    public ResponseEntity<?> challenge(@RequestBody ChallengeRequest request) {
        try {
            String nonce = handshakeService.issueNonce(request.publicKey());
            Instant expiresAt = Instant.now().plus(120, ChronoUnit.SECONDS);
            return ResponseEntity.ok(new ChallengeResponse(nonce, expiresAt));
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
