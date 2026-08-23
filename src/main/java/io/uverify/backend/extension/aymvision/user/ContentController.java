package io.uverify.backend.extension.aymvision.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.extension.aymvision.anchor.CompletionCertificate;
import io.uverify.backend.extension.aymvision.auth.HandshakeService;
import io.uverify.backend.extension.aymvision.dto.content.ContentItemDto;
import io.uverify.backend.extension.aymvision.dto.content.ContentOwnershipDto;
import io.uverify.backend.extension.aymvision.dto.content.CourseStateRequest;
import io.uverify.backend.extension.aymvision.exception.InvalidHandshakeException;
import io.uverify.backend.extension.aymvision.exception.ProfileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/aym")
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class ContentController {

    static final String HEADER_PUBLIC_KEY = "X-Aym-Public-Key";
    static final String HEADER_NONCE = "X-Aym-Nonce";
    static final String HEADER_SIGNATURE = "X-Aym-Signature";

    private final HandshakeService handshakeService;
    private final ContentService contentService;
    private final ObjectMapper objectMapper;

    public ContentController(HandshakeService handshakeService,
                             ContentService contentService,
                             ObjectMapper objectMapper) {
        this.handshakeService = handshakeService;
        this.contentService = contentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/content")
    public ResponseEntity<?> listContent(@RequestParam String profileId,
                                         HttpServletRequest request) {
        try {
            HandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
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
            HandshakeService.HandshakeResult auth = verifyHandshake(request, new byte[0]);
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
            HandshakeService.HandshakeResult auth = verifyHandshake(request, rawBody);
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

    private HandshakeService.HandshakeResult verifyHandshake(HttpServletRequest request, byte[] body) {
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
