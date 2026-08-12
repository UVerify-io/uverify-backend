package io.uverify.backend.extension.aymvision.voucher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.extension.aymvision.auth.HandshakeService;
import io.uverify.backend.extension.aymvision.exception.InvalidHandshakeException;
import io.uverify.backend.extension.aymvision.user.AlreadyOwnedException;
import io.uverify.backend.extension.aymvision.exception.VoucherNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym/voucher")
public class VoucherController {

    private static final String HEADER_PUBLIC_KEY = "X-AYM-PublicKey";
    private static final String HEADER_NONCE = "X-AYM-Nonce";
    private static final String HEADER_SIGNATURE = "X-AYM-Signature";

    private final HandshakeService handshakeService;
    private final VoucherService voucherService;
    private final ObjectMapper objectMapper;

    public VoucherController(HandshakeService handshakeService,
                             VoucherService voucherService,
                             ObjectMapper objectMapper) {
        this.handshakeService = handshakeService;
        this.voucherService = voucherService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            HandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
            if (!handshakeService.isMasterKey(auth.publicKeyHex())) {
                return ResponseEntity.status(403).body("Master key required");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
            String contentId = (String) body.get("contentId");
            int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 1;

            List<VoucherEntity> vouchers = voucherService.create(contentId, count);
            List<Map<String, String>> result = vouchers.stream()
                    .map(v -> Map.of("id", v.getId().toString(), "contentId", v.getContentId()))
                    .toList();
            return ResponseEntity.ok(Map.of("vouchers", result));
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read request body");
        }
    }

    @PostMapping("/redeem")
    public ResponseEntity<?> redeem(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            HandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
            @SuppressWarnings("unchecked")
            Map<String, String> body = objectMapper.readValue(rawBody, Map.class);
            UUID voucherId = UUID.fromString(body.get("voucherId"));
            String profileId = body.get("profileId");

            RedeemResult result = voucherService.redeem(auth.publicKeyHex(), profileId, voucherId);
            return ResponseEntity.ok(result);
        } catch (InvalidHandshakeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (VoucherNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AlreadyOwnedException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read request body");
        }
    }

    private HandshakeService.HandshakeResult verifyHandshake(HttpServletRequest request, byte[] body) {
        String publicKey = request.getHeader(HEADER_PUBLIC_KEY);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);
        return handshakeService.verify(publicKey, nonce, signature,
                request.getMethod(), request.getRequestURI(), body);
    }
}
