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
import io.uverify.backend.util.TemplateIdResolver;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class IdentityIndexerService {

    private static final String TYPE_AUTH = "AUTH";
    private static final String TYPE_REVOKE = "REVOKE";
    private static final String IDENTITY_AUTH_TEMPLATE_ID = "IdentityAuth";

    private static final String CACHE_NAME = "vlei-verifier";
    private static final String BINDING_PAYLOAD_PREFIX = "cardano:";
    private static final Pattern QB64 = Pattern.compile("^[A-Za-z0-9_-]{1,200}$");

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

        if (!IDENTITY_AUTH_TEMPLATE_ID.equals(TemplateIdResolver.resolveTemplateId(fields))) {
            return;
        }

        String type = (String) fields.get("t");
        if (type == null) {
            return;
        }

        if (TYPE_AUTH.equals(type)) {
            handleAuthCert(cert, fields);
        } else if (TYPE_REVOKE.equals(type)) {
            handleRevokeCert(cert, fields);
        }
    }

    private void handleAuthCert(UVerifyCertificateEntity cert, Map<String, Object> fields) {
        String credentialType = (String) fields.get("ct");
        String aid = (String) fields.get("i");
        String schema = (String) fields.get("s");
        String oobi = (String) fields.get("o");
        String proof = (String) fields.get("p");

        if (credentialType == null || credentialType.isBlank()) {
            log.warn("AUTH cert hash={} missing ct field, skipping", cert.getHash());
            return;
        }

        Optional<UVerifyCredentialEntity> existing = credentialRepository.findByAuthCertHash(cert.getHash());
        // Certificate hashes are not unique across wallets. Without this check a second wallet could
        // reuse a hash and have its proof verified against the first wallet's stored binding.
        if (existing.isPresent() && !existing.get().getPaymentCredential().equals(cert.getPaymentCredential())) {
            log.warn("AUTH cert hash={} is already registered for another wallet, skipping", cert.getHash());
            return;
        }
        UVerifyCredentialEntity entity = existing.orElseGet(() -> UVerifyCredentialEntity.builder()
                .authCertHash(cert.getHash())
                .paymentCredential(cert.getPaymentCredential())
                .credentialType(credentialType)
                .keriAid(aid)
                .keriSchema(schema)
                .keriOobi(oobi)
                .keriProof(proof)
                .txHash(cert.getTransactionId())
                .slot(cert.getSlot())
                .build());

        // Direct call (bypasses proxy/cache) — we want a live result at indexing time.
        boolean verified = verifyKeriBinding(entity.getKeriAid(), entity.getPaymentCredential(), entity.getKeriProof());
        // Pre-populate the cache so the first GET does not make a second live call.
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null && hasBindingFields(entity.getKeriAid(), entity.getKeriProof())) {
            cache.put(bindingCacheKey(entity), verified);
        }

        entity.setKeriVerified(verified);
        entity.setLastVerifiedAt(Instant.now());
        credentialRepository.save(entity);

        log.info("Indexed credential type={} for wallet={}, keriVerified={}", credentialType,
                cert.getPaymentCredential(), verified);
    }

    private void handleRevokeCert(UVerifyCertificateEntity cert, Map<String, Object> fields) {
        String targetHash = (String) fields.get("th");
        if (targetHash == null || targetHash.isBlank()) {
            log.warn("REVOKE cert missing th field, skipping");
            return;
        }
        credentialRepository.findByAuthCertHash(targetHash).ifPresentOrElse(
                entity -> {
                    if (!entity.getPaymentCredential().equals(cert.getPaymentCredential())) {
                        log.warn("REVOKE cert hash={} from wallet={} targets a credential of another wallet, skipping",
                                cert.getHash(), cert.getPaymentCredential());
                        return;
                    }
                    entity.setRevoked(true);
                    credentialRepository.save(entity);
                    // Evict so the next GET re-checks the vLEI Verifier live.
                    Cache cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null && hasBindingFields(entity.getKeriAid(), entity.getKeriProof())) {
                        cache.evict(bindingCacheKey(entity));
                    }
                    log.info("Revoked credential authHash={}", targetHash);
                },
                () -> log.warn("REVOKE target authHash={} not found in credential table", targetHash));
    }

    /**
     * True when the vLEI Verifier authorizes {@code aid} and {@code proof} is a valid signature of
     * {@code cardano:<paymentCredential>} by the AID's current signing key. The proof is what ties
     * the AID to the wallet. Without it any wallet could announce an authorized AID as its own.
     */
    @Cacheable(value = CACHE_NAME, key = "#aid + '|' + #paymentCredential + '|' + #proof",
            condition = "#aid != null && !#aid.isBlank() && #proof != null && !#proof.isBlank()")
    public boolean verifyKeriBinding(String aid, String paymentCredential, String proof) {
        if (!isVleiVerifierConfigured()) {
            log.debug("vLEI Verifier URL not configured, skipping verification");
            return false;
        }
        if (!hasBindingFields(aid, proof) || !QB64.matcher(aid).matches() || !QB64.matcher(proof).matches()) {
            return false;
        }
        return isAuthorized(aid) && isBindingProofValid(aid, paymentCredential, proof);
    }

    private boolean isAuthorized(String aid) {
        URI uri = UriComponentsBuilder.fromUriString(vleiVerifierUrl)
                .pathSegment("authorizations", aid)
                .build().encode().toUri();
        try {
            return restTemplate.getForEntity(uri, Void.class).getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("vLEI Verifier authorization check failed for aid={}: {}", aid, e.getMessage());
            return false;
        }
    }

    // The verifier checks the non-indexed signature against the key state of the AID's KEL.
    private boolean isBindingProofValid(String aid, String paymentCredential, String proof) {
        URI uri = UriComponentsBuilder.fromUriString(vleiVerifierUrl)
                .pathSegment("request", "verify", aid)
                .queryParam("data", BINDING_PAYLOAD_PREFIX + paymentCredential)
                .queryParam("sig", proof)
                .build().encode().toUri();
        try {
            return restTemplate.postForEntity(uri, null, Void.class).getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("vLEI Verifier rejected the binding proof for aid={}: {}", aid, e.getMessage());
            return false;
        }
    }

    private static boolean hasBindingFields(String aid, String proof) {
        return aid != null && !aid.isBlank() && proof != null && !proof.isBlank();
    }

    private static String bindingCacheKey(UVerifyCredentialEntity entity) {
        return entity.getKeriAid() + "|" + entity.getPaymentCredential() + "|" + entity.getKeriProof();
    }
}
