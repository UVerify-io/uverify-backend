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

import io.uverify.backend.extension.service.AymStripePurchaseService;
import io.uverify.backend.extension.exception.InvalidHandshakeException;
import io.uverify.backend.extension.exception.KeyMismatchException;
import io.uverify.backend.extension.exception.PaymentRequiredException;
import io.uverify.backend.extension.exception.SessionAlreadyUsedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.extension.service.AymHandshakeService;
import io.uverify.backend.extension.exception.AlreadyOwnedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/extension/aym-vision/purchase")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymStripePurchaseController {

    private static final String HEADER_PUBLIC_KEY = "X-Aym-Public-Key";
    private static final String HEADER_NONCE = "X-Aym-Nonce";
    private static final String HEADER_SIGNATURE = "X-Aym-Signature";

    private final AymHandshakeService handshakeService;
    private final AymStripePurchaseService purchaseService;
    private final ObjectMapper objectMapper;

    public AymStripePurchaseController(AymHandshakeService handshakeService,
                                    AymStripePurchaseService purchaseService,
                                    ObjectMapper objectMapper) {
        this.handshakeService = handshakeService;
        this.purchaseService = purchaseService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stripe")
    public ResponseEntity<?> verifyStripe(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            AymHandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
            @SuppressWarnings("unchecked")
            Map<String, String> body = objectMapper.readValue(rawBody, Map.class);
            String sessionId = body.get("sessionId");
            String profileId = body.get("profileId");

            var result = purchaseService.verifyAndGrant(auth.publicKeyHex(), profileId, sessionId);
            return ResponseEntity.ok(result);
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (PaymentRequiredException e) {
            return ResponseEntity.status(402).body(e.getMessage());
        } catch (AlreadyOwnedException | SessionAlreadyUsedException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (KeyMismatchException e) {
            return ResponseEntity.status(422).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read request body");
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
