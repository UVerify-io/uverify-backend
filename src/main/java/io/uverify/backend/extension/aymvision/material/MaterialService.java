package io.uverify.backend.extension.aymvision.material;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
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
public class MaterialService {

    private final AymVisionProperties properties;
    private final RestTemplate restTemplate;
    private final Cache<String, CachedBundle> bundleCache;

    public MaterialService(AymVisionProperties properties) {
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
