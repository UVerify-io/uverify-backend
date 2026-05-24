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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.uverify.backend.config.AsyncConfig;
import io.uverify.backend.dto.CredentialResponse;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.entity.UVerifyCredentialEntity;
import io.uverify.backend.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {AsyncConfig.class, IdentityIndexerService.class, CredentialService.class}
)
@Import(CredentialVerificationTest.TestConfig.class)
@TestPropertySource(properties = {
        "credential.vlei-verifier-url=http://mock-vlei",
        "credential.keria-timeout-ms=500",
        "spring.main.allow-bean-definition-overriding=true"
})
class CredentialVerificationTest {

    private static final String AID = "EKtQ1lymrnrh3qv5S18PBzQ7ukHGFJ7EXkH7B22XEMIL";
    private static final String PAYMENT_CREDENTIAL = "abcdef0123456789abcdef0123456789";
    private static final String AUTH_HASH = "deadbeef1234";
    private static final String VERIFIER_BASE = "http://mock-vlei";
    @Autowired
    private IdentityIndexerService identityIndexerService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    @Qualifier("vleiVerifierRestTemplate")
    private RestTemplate restTemplate;
    @Autowired
    private CacheManager cacheManager;
    @MockBean
    private CredentialRepository credentialRepository;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        cacheManager.getCache("vlei-verifier").clear();
    }

    @Test
    void verifier200_returnsTrue() {
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(identityIndexerService.checkVleiVerifier(AID));
        mockServer.verify();
    }

    // ── checkVleiVerifier ──────────────────────────────────────────────────────

    @Test
    void verifier404_returnsFalse() {
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(identityIndexerService.checkVleiVerifier(AID));
        mockServer.verify();
    }

    @Test
    void verifier401_returnsFalse() {
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(identityIndexerService.checkVleiVerifier(AID));
        mockServer.verify();
    }

    @Test
    void verifierUnreachable_returnsFalseWithoutThrowing() {
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withException(new java.io.IOException("connection refused")));

        assertFalse(identityIndexerService.checkVleiVerifier(AID));
        mockServer.verify();
    }

    @Test
    void secondCallHitsCache_onlyOneHttpRequest() {
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(identityIndexerService.checkVleiVerifier(AID));
        assertTrue(identityIndexerService.checkVleiVerifier(AID)); // cache hit

        mockServer.verify(); // would fail if two requests were made
    }

    // ── caching ───────────────────────────────────────────────────────────────

    @Test
    void differentAids_eachGetSeparateHttpRequest() {
        String aid2 = "EBfdlu8R27Fbx-ehrqwImnK-8Cm79sqbAQ4MmvEAYqao";

        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + aid2))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(identityIndexerService.checkVleiVerifier(AID));
        assertFalse(identityIndexerService.checkVleiVerifier(aid2));
        mockServer.verify();
    }

    @Test
    void revokeCert_evictsCacheEntryForAid() {
        // Seed the cache via the proxy (goes through @Cacheable)
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        identityIndexerService.checkVleiVerifier(AID);

        // Confirm it is cached — second call should NOT hit the server
        identityIndexerService.checkVleiVerifier(AID);
        mockServer.verify();

        // Process a REVOKE cert through the real code path
        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(entity));
        given(credentialRepository.save(any())).willReturn(entity);

        identityIndexerService.processNewCertificates(List.of(
                revokeCert("{\"uverify_template_id\":\"IdentityAuth\",\"t\":\"REVOKE\",\"ct\":\"identity\",\"th\":\"" + AUTH_HASH + "\"}")
        ));

        // After eviction the next call must reach the server again.
        // MockRestServiceServer forbids new expectations after requests have been made,
        // so reset before the second phase.
        mockServer.reset();
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(identityIndexerService.checkVleiVerifier(AID));
        mockServer.verify();
    }

    // ── cache eviction on revoke ───────────────────────────────────────────────

    @Test
    void resolveCredential_callsLiveVerifierAndReturnsResult() {
        given(credentialRepository.findByPaymentCredentialAndCredentialTypeAndRevokedFalse(PAYMENT_CREDENTIAL, "identity"))
                .willReturn(Optional.of(credentialEntity(AUTH_HASH)));

        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        Optional<CredentialResponse> result = credentialService.resolveCredential(PAYMENT_CREDENTIAL, "identity");

        assertTrue(result.isPresent());
        assertTrue(result.get().isKeriVerified());
        mockServer.verify();
    }

    // ── CredentialService integration ─────────────────────────────────────────

    @Test
    void resolveCredential_cacheServesSubsequentCalls() {
        given(credentialRepository.findByPaymentCredentialAndCredentialTypeAndRevokedFalse(PAYMENT_CREDENTIAL, "identity"))
                .willReturn(Optional.of(credentialEntity(AUTH_HASH)));

        // Only one HTTP call expected even though resolve is called twice
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/authorizations/" + AID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        credentialService.resolveCredential(PAYMENT_CREDENTIAL, "identity");
        credentialService.resolveCredential(PAYMENT_CREDENTIAL, "identity");

        mockServer.verify();
    }

    @Test
    void resolveCredential_usesDatabaseValue_whenVerifierNotConfigured() {
        IdentityIndexerService unconfigured = mock(IdentityIndexerService.class);
        given(unconfigured.isVleiVerifierConfigured()).willReturn(false);

        CredentialService svc = new CredentialService(credentialRepository, new ObjectMapper(), unconfigured);

        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        entity.setKeriVerified(true);
        given(credentialRepository.findByPaymentCredentialAndCredentialTypeAndRevokedFalse(PAYMENT_CREDENTIAL, "identity"))
                .willReturn(Optional.of(entity));

        Optional<CredentialResponse> result = svc.resolveCredential(PAYMENT_CREDENTIAL, "identity");

        assertTrue(result.isPresent());
        assertTrue(result.get().isKeriVerified()); // from DB, not live check
        verify(unconfigured, never()).checkVleiVerifier(any());
    }

    @Test
    void resolveByHash_doesNotCallVerifier() {
        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        entity.setKeriVerified(true);
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(entity));

        Optional<CredentialResponse> result = credentialService.resolveCredentialByHash(AUTH_HASH);

        assertTrue(result.isPresent());
        assertTrue(result.get().isKeriVerified()); // plain DB read, no HTTP
        mockServer.verify(); // no requests recorded
    }

    private UVerifyCredentialEntity credentialEntity(String authHash) {
        return UVerifyCredentialEntity.builder()
                .authCertHash(authHash)
                .paymentCredential(PAYMENT_CREDENTIAL)
                .credentialType("identity")
                .keriAid(AID)
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UVerifyCertificateEntity revokeCert(String extra) {
        return UVerifyCertificateEntity.builder()
                .hash("cert-" + System.nanoTime())
                .extra(extra)
                .paymentCredential(PAYMENT_CREDENTIAL)
                .transactionId("tx-hash")
                .slot(200L)
                .build();
    }

    @TestConfiguration
    @EnableCaching
    static class TestConfig {

        @Bean
        public CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager("vlei-verifier");
            manager.setCaffeine(Caffeine.newBuilder()
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(100));
            return manager;
        }

        // Override the RestTemplate so MockRestServiceServer can bind to it
        @Bean
        public RestTemplate vleiVerifierRestTemplate() {
            return new RestTemplate();
        }

        // Make @Async run on the calling thread so tests don't need waits
        @Bean
        public Executor taskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
