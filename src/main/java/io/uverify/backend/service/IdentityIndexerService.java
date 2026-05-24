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

package io.uverify.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.entity.UVerifyCredentialEntity;
import io.uverify.backend.repository.CredentialRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class IdentityIndexerService {

    private static final String TYPE_AUTH = "AUTH";
    private static final String TYPE_REVOKE = "REVOKE";
    private static final String IDENTITY_AUTH_TEMPLATE_ID = "IdentityAuth";

    private static final String CACHE_NAME = "vlei-verifier";

    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final CacheManager cacheManager;

    @Value("${credential.vlei-verifier-url:}")
    private String vleiVerifierUrl;

    public IdentityIndexerService(CredentialRepository credentialRepository,
                                  ObjectMapper objectMapper,
                                  CacheManager cacheManager,
                                  @Qualifier("vleiVerifierRestTemplate") RestTemplate restTemplate) {
        this.credentialRepository = credentialRepository;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
        this.restTemplate = restTemplate;
    }

    @Async
    public void processNewCertificates(List<UVerifyCertificateEntity> certs) {
        for (UVerifyCertificateEntity cert : certs) {
            try {
                processOneCertificate(cert);
            } catch (Exception e) {
                log.warn("Failed to index credential for cert hash={}: {}", cert.getHash(), e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteCredentialsAfterSlot(long slot) {
        credentialRepository.deleteAllAfterSlot(slot);
    }

    public boolean isVleiVerifierConfigured() {
        return vleiVerifierUrl != null && !vleiVerifierUrl.isBlank();
    }

    private void processOneCertificate(UVerifyCertificateEntity cert) throws Exception {
        String extra = cert.getExtra();
        if (extra == null || extra.isBlank()) {
            return;
        }
        Map<String, Object> fields = objectMapper.readValue(extra, new TypeReference<>() {
        });

        if (!IDENTITY_AUTH_TEMPLATE_ID.equals(fields.get("uverify_template_id"))) {
            return;
        }

        String type = (String) fields.get("t");
        if (type == null) {
            return;
        }

        if (TYPE_AUTH.equals(type)) {
            handleAuthCert(cert, fields);
        } else if (TYPE_REVOKE.equals(type)) {
            handleRevokeCert(fields);
        }
    }

    private void handleAuthCert(UVerifyCertificateEntity cert, Map<String, Object> fields) {
        String credentialType = (String) fields.get("ct");
        String aid = (String) fields.get("i");
        String schema = (String) fields.get("s");
        String oobi = (String) fields.get("o");

        if (credentialType == null || credentialType.isBlank()) {
            log.warn("AUTH cert hash={} missing ct field, skipping", cert.getHash());
            return;
        }

        // Direct call (bypasses proxy/cache) — we want a live result at indexing time.
        boolean verified = checkVleiVerifier(aid);
        // Pre-populate the cache so the first GET does not make a second live call.
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null && aid != null) {
            cache.put(aid, verified);
        }

        Optional<UVerifyCredentialEntity> existing = credentialRepository.findByAuthCertHash(cert.getHash());
        UVerifyCredentialEntity entity;
        entity = existing.orElseGet(() -> UVerifyCredentialEntity.builder()
                .authCertHash(cert.getHash())
                .paymentCredential(cert.getPaymentCredential())
                .credentialType(credentialType)
                .keriAid(aid)
                .keriSchema(schema)
                .keriOobi(oobi)
                .txHash(cert.getTransactionId())
                .slot(cert.getSlot())
                .build());
        entity.setKeriVerified(verified);
        entity.setLastVerifiedAt(Instant.now());
        credentialRepository.save(entity);

        log.info("Indexed credential type={} for wallet={}, keriVerified={}", credentialType,
                cert.getPaymentCredential(), verified);
    }

    private void handleRevokeCert(Map<String, Object> fields) {
        String targetHash = (String) fields.get("th");
        if (targetHash == null || targetHash.isBlank()) {
            log.warn("REVOKE cert missing th field, skipping");
            return;
        }
        credentialRepository.findByAuthCertHash(targetHash).ifPresentOrElse(
                entity -> {
                    entity.setRevoked(true);
                    credentialRepository.save(entity);
                    // Evict so the next GET re-checks the vLEI Verifier live.
                    Cache cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null && entity.getKeriAid() != null) {
                        cache.evict(entity.getKeriAid());
                    }
                    log.info("Revoked credential authHash={}", targetHash);
                },
                () -> log.warn("REVOKE target authHash={} not found in credential table", targetHash));
    }

    @Cacheable(value = "vlei-verifier", key = "#aid", condition = "#aid != null && !#aid.isEmpty()")
    public boolean checkVleiVerifier(String aid) {
        if (vleiVerifierUrl == null || vleiVerifierUrl.isBlank() || aid == null || aid.isBlank()) {
            log.debug("vLEI Verifier URL not configured, skipping verification");
            return false;
        }
        try {
            var response = restTemplate.getForEntity(vleiVerifierUrl + "/authorizations/" + aid, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("vLEI Verifier check failed for aid={}: {}", aid, e.getMessage());
            return false;
        }
    }
}
