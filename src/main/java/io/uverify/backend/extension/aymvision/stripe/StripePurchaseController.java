package io.uverify.backend.extension.aymvision.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.extension.aymvision.auth.HandshakeService;
import io.uverify.backend.extension.aymvision.exception.*;
import io.uverify.backend.extension.aymvision.user.AlreadyOwnedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym/purchase")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class StripePurchaseController {

    private static final String HEADER_PUBLIC_KEY = "X-Aym-Public-Key";
    private static final String HEADER_NONCE = "X-Aym-Nonce";
    private static final String HEADER_SIGNATURE = "X-Aym-Signature";

    private final HandshakeService handshakeService;
    private final StripePurchaseService purchaseService;
    private final ObjectMapper objectMapper;

    public StripePurchaseController(HandshakeService handshakeService,
                                    StripePurchaseService purchaseService,
                                    ObjectMapper objectMapper) {
        this.handshakeService = handshakeService;
        this.purchaseService = purchaseService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stripe")
    public ResponseEntity<?> verifyStripe(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            HandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
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

    private HandshakeService.HandshakeResult verifyHandshake(HttpServletRequest request, byte[] body) {
        return handshakeService.verify(
                request.getHeader(HEADER_PUBLIC_KEY),
                request.getHeader(HEADER_NONCE),
                request.getHeader(HEADER_SIGNATURE),
                request.getMethod(),
                request.getRequestURI(),
                body);
    }
}
