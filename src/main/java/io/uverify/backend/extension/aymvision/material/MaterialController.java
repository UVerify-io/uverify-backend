package io.uverify.backend.extension.aymvision.material;

import io.uverify.backend.extension.aymvision.auth.HandshakeService;
import io.uverify.backend.extension.aymvision.exception.InvalidHandshakeException;
import io.uverify.backend.extension.aymvision.user.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym/material")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class MaterialController {

    private static final String HEADER_PUBLIC_KEY = "X-Aym-Public-Key";
    private static final String HEADER_NONCE = "X-Aym-Nonce";
    private static final String HEADER_SIGNATURE = "X-Aym-Signature";

    private final HandshakeService handshakeService;
    private final ContentService contentService;
    private final MaterialService materialService;

    public MaterialController(HandshakeService handshakeService,
                              ContentService contentService,
                              MaterialService materialService) {
        this.handshakeService = handshakeService;
        this.contentService = contentService;
        this.materialService = materialService;
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getMaterial(@PathVariable String courseId,
                                         @RequestParam String profileId,
                                         HttpServletRequest request) {
        try {
            HandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
            if (!contentService.owns(auth.publicKeyHex(), profileId, courseId)) {
                return ResponseEntity.status(403).body("Access denied: content not owned");
            }
            MaterialService.CachedBundle bundle = materialService.getBundle(courseId);
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
            HandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
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
