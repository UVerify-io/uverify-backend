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
import io.uverify.backend.extension.service.AymContentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@EnabledIf(
        expression = "${extensions.aym-vision.enabled}",
        loadContext = true,
        reason = "AYM Vision extension must be enabled for this test"
)
class AymMaterialControllerTest {

    private static final String PUB_KEY   = "aa".repeat(32);
    private static final String PROFILE_ID = "profile-a";

    @Mock AymHandshakeService handshakeService;
    @Mock AymContentService contentService;
    @Mock AymMaterialService materialService;
    @Mock HttpServletRequest request;

    private AymMaterialController controller;

    @BeforeEach
    void setUp() {
        controller = new AymMaterialController(handshakeService, contentService, materialService);
        when(request.getHeader("X-Aym-Public-Key")).thenReturn(PUB_KEY);
        when(request.getHeader("X-Aym-Nonce")).thenReturn("nonce");
        when(request.getHeader("X-Aym-Signature")).thenReturn("sig");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/extension/aym-vision/material/s1e01");
        when(handshakeService.verify(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AymHandshakeService.HandshakeResult(PUB_KEY));
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
                .thenReturn(new AymMaterialService.CachedBundle("s1e01", "1.0.0", "base64data"));

        ResponseEntity<?> response = controller.getMaterial("s1e01", PROFILE_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getVersion_returns403_whenProfileDoesNotOwnContent() {
        when(request.getRequestURI()).thenReturn("/api/v1/extension/aym-vision/material/s1e01/version");
        when(contentService.owns(PUB_KEY, PROFILE_ID, "s1e01")).thenReturn(false);

        ResponseEntity<?> response = controller.getVersion("s1e01", PROFILE_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(materialService, never()).getVersion(any());
    }
}
