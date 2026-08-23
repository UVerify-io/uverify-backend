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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.uverify.backend.extension.config.AymVisionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymMaterialService {

    private final AymVisionProperties properties;
    private final RestTemplate restTemplate;
    private final Cache<String, CachedBundle> bundleCache;

    public AymMaterialService(AymVisionProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();

        Duration ttl = Duration.parse(properties.getContentRepo().getCacheTtl());
        this.bundleCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(200)
                .build();
    }

    public CachedBundle getBundle(String courseId) {
        return bundleCache.get(courseId, this::fetchFromRepo);
    }

    public String getVersion(String courseId) {
        CachedBundle bundle = bundleCache.getIfPresent(courseId);
        if (bundle != null) return bundle.version();
        return fetchFromRepo(courseId).version();
    }

    private CachedBundle fetchFromRepo(String courseId) {
        String url = properties.getContentRepo().getUrl().replaceAll("/$", "")
                + "/" + courseId + "/bundle.json";
        HttpHeaders headers = new HttpHeaders();
        String token = properties.getContentRepo().getToken();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<?, ?> body = response.getBody();
        String version = body != null && body.containsKey("version")
                ? String.valueOf(body.get("version")) : "unknown";
        String payload = body != null && body.containsKey("payload")
                ? String.valueOf(body.get("payload")) : "";
        return new CachedBundle(courseId, version, payload);
    }

    public record CachedBundle(String courseId, String version, String payload) {}
}
