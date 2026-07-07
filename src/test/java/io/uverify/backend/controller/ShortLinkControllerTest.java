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

package io.uverify.backend.controller;

import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.service.ShortLinkService;
import io.uverify.backend.service.UVerifyCertificateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShortLinkControllerTest {

    private static final String HASH = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";
    private static final String CODE = "RXYWODQzXG";

    @Mock
    private ShortLinkService shortLinkService;
    @Mock
    private UVerifyCertificateService certificateService;

    private ShortLinkController controller;

    private UVerifyCertificateEntity certificate(String extra, long slot) {
        return UVerifyCertificateEntity.builder()
                .hash(HASH)
                .paymentCredential("3f9ff01fd67fcf42cb64f004f13306bd4bfd651154aedcb0dd68dd87")
                .extra(extra)
                .slot(slot)
                .creationTime(new Date(1745006677000L))
                .build();
    }

    @BeforeEach
    void setUp() {
        controller = new ShortLinkController("preprod", "https://go.uverify.io",
                "https://app.uverify.io", shortLinkService, certificateService);
    }

    @Test
    void unknownCodeReturnsBrandedNotFoundPage() {
        given(shortLinkService.resolve(CODE)).willReturn(Optional.empty());

        ResponseEntity<String> response = controller.resolve(CODE, new MockHttpServletRequest());

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().contains("UVerify"));
    }

    @Test
    void resolvedCodeRendersOgTagsAndRedirect() {
        given(shortLinkService.resolve(CODE)).willReturn(Optional.of(HASH));
        given(certificateService.getCertificatesByHash(HASH)).willReturn(List.of(
                certificate("{\"uv_tid\":\"diploma\",\"title\":\"Certified Cardano Developer\"}", 100)));

        ResponseEntity<String> response = controller.resolve(CODE, new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        String html = response.getBody();
        assertTrue(html.contains("og:title"));
        assertTrue(html.contains("Certified Cardano Developer"));
        assertTrue(html.contains("https://app.uverify.io/og/diploma.png"));
        assertTrue(html.contains("https://app.uverify.io/verify/" + HASH));
        assertTrue(html.contains("https://go.uverify.io/" + CODE));
        assertEquals("public, max-age=300", response.getHeaders().getCacheControl());
        verify(shortLinkService).registerClick(CODE);
    }

    @Test
    void queryStringIsPassedThroughToTheRedirect() {
        given(shortLinkService.resolve(CODE)).willReturn(Optional.of(HASH));
        given(certificateService.getCertificatesByHash(HASH)).willReturn(List.of(certificate("{}", 100)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("name=Jane%20Doe~x7Rk2p");
        request.setParameter("name", "Jane Doe~x7Rk2p");

        String html = controller.resolve(CODE, request).getBody();
        assertTrue(html.contains("/verify/" + HASH + "?name=Jane%20Doe~x7Rk2p"));
    }

    @Test
    void metadataValuesAreHtmlEscaped() {
        given(shortLinkService.resolve(CODE)).willReturn(Optional.of(HASH));
        given(certificateService.getCertificatesByHash(HASH)).willReturn(List.of(
                certificate("{\"uv_og_title\":\"<script>alert(1)</script>\"}", 100)));

        String html = controller.resolve(CODE, new MockHttpServletRequest()).getBody();
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void policyOverrideSelectsTheLatestContentSubmission() {
        given(shortLinkService.resolve(CODE)).willReturn(Optional.of(HASH));
        given(certificateService.getCertificatesByHash(HASH)).willReturn(List.of(
                certificate("{\"uv_og_title\":\"Corrected Title\",\"v\":\"2\"}", 200),
                certificate("{\"uverify_update_policy\":\"override\",\"uv_og_title\":\"Old Title\",\"v\":\"1\"}", 100)));

        String html = controller.resolve(CODE, new MockHttpServletRequest()).getBody();
        assertTrue(html.contains("Corrected Title"));
        assertFalse(html.contains("Old Title"));
    }
}
