package io.uverify.backend.extension.aymvision.auth;

import io.uverify.backend.extension.aymvision.dto.ChallengeRequest;
import io.uverify.backend.extension.aymvision.dto.ChallengeResponse;
import io.uverify.backend.extension.aymvision.exception.InvalidHandshakeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym")
public class ChallengeController {

    private final HandshakeService handshakeService;

    public ChallengeController(HandshakeService handshakeService) {
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
