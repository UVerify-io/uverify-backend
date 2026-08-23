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

package io.uverify.backend.extension.service;

import io.uverify.backend.extension.config.AymVisionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

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
class AymMaterialServiceTest {

    private AymMaterialService service;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() throws Exception {
        AymVisionProperties props = new AymVisionProperties();
        props.getContentRepo().setUrl("https://content.example.com");
        props.getContentRepo().setToken("test-token");
        props.getContentRepo().setCacheTtl("PT6H");

        service = new AymMaterialService(props);

        // inject mock RestTemplate via reflection
        restTemplate = mock(RestTemplate.class);
        var field = AymMaterialService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(service, restTemplate);
    }

    @Test
    void getBundle_fetchesFromRepoAndCaches() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = ResponseEntity.ok(
                Map.of("version", "1.2.3", "payload", "base64data"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        AymMaterialService.CachedBundle bundle = service.getBundle("s1e01");

        assertThat(bundle.courseId()).isEqualTo("s1e01");
        assertThat(bundle.version()).isEqualTo("1.2.3");
        assertThat(bundle.payload()).isEqualTo("base64data");
    }

    @Test
    void getBundle_cacheHitDoesNotRefetch() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = ResponseEntity.ok(
                Map.of("version", "1.0.0", "payload", "data"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        service.getBundle("s1e01");
        service.getBundle("s1e01"); // second call — should hit cache

        // RestTemplate called only once
        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void getVersion_returnsVersionWithoutPayload() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = ResponseEntity.ok(
                Map.of("version", "2.0.0", "payload", "large-payload"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        String version = service.getVersion("s1e02");

        assertThat(version).isEqualTo("2.0.0");
    }

    @Test
    void getBundle_buildsCorrectUrl() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = ResponseEntity.ok(
                Map.of("version", "1.0", "payload", ""));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        service.getBundle("s1e03");

        verify(restTemplate).exchange(
                eq("https://content.example.com/s1e03/bundle.json"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class));
    }
}
