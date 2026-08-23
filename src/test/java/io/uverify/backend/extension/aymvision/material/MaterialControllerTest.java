package io.uverify.backend.extension.aymvision.material;

import io.uverify.backend.extension.aymvision.auth.HandshakeService;
import io.uverify.backend.extension.aymvision.user.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialControllerTest {

    private static final String PUB_KEY   = "aa".repeat(32);
    private static final String PROFILE_ID = "profile-a";

    @Mock HandshakeService handshakeService;
    @Mock ContentService contentService;
    @Mock MaterialService materialService;
    @Mock HttpServletRequest request;

    private MaterialController controller;

    @BeforeEach
    void setUp() {
        controller = new MaterialController(handshakeService, contentService, materialService);
        when(request.getHeader("X-Aym-Public-Key")).thenReturn(PUB_KEY);
        when(request.getHeader("X-Aym-Nonce")).thenReturn("nonce");
        when(request.getHeader("X-Aym-Signature")).thenReturn("sig");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/aym/material/s1e01");
        when(handshakeService.verify(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HandshakeService.HandshakeResult(PUB_KEY));
    }

    @Test
    void getMaterial_returns403_whenProfileDoesNotOwnContent() {
        when(contentService.owns(PUB_KEY, PROFILE_ID, "s1e01")).thenReturn(false);

        ResponseEntity<?> response = controller.getMaterial("s1e01", PROFILE_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(materialService, never()).getBundle(any());
    }

    @Test
    void getMaterial_returnsBundle_whenOwned() {
        when(contentService.owns(PUB_KEY, PROFILE_ID, "s1e01")).thenReturn(true);
        when(materialService.getBundle("s1e01"))
                .thenReturn(new MaterialService.CachedBundle("s1e01", "1.0.0", "base64data"));

        ResponseEntity<?> response = controller.getMaterial("s1e01", PROFILE_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getVersion_returns403_whenProfileDoesNotOwnContent() {
        when(request.getRequestURI()).thenReturn("/api/v1/aym/material/s1e01/version");
        when(contentService.owns(PUB_KEY, PROFILE_ID, "s1e01")).thenReturn(false);

        ResponseEntity<?> response = controller.getVersion("s1e01", PROFILE_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(materialService, never()).getVersion(any());
    }
}
