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
import org.mockito.ArgumentCaptor;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
    private static final String OTHER_PAYMENT_CREDENTIAL = "99999999999999999999999999999999";
    private static final String PROOF = "0BDx3R1qKpZ8oVnbUeJ3nH9wT4sY7cLmA2fG6iE0dQ5kXjPzWv1uNrB8tSyMhC4gOaF7lI2eKqD9pR3sV6wY0zXcJ";
    private static final String AUTH_HASH = "deadbeef1234";
    private static final String VERIFIER_BASE = "http://mock-vlei";
    private static final String AUTHORIZATION_URL = VERIFIER_BASE + "/authorizations/" + AID;
    private static final String PROOF_URL = VERIFIER_BASE + "/request/verify/" + AID
            + "?data=cardano:" + PAYMENT_CREDENTIAL + "&sig=" + PROOF;

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

    // ── verifyKeriBinding ─────────────────────────────────────────────────────

    @Test
    void authorizedAidWithValidProof_returnsTrue() {
        expectAuthorized();
        expectProofAccepted();

        assertTrue(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    @Test
    void authorizedAidWithRejectedProof_returnsFalse() {
        expectAuthorized();
        mockServer.expect(once(), requestTo(PROOF_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    @Test
    void proofForAnotherWallet_isCheckedAgainstTheAnnouncingWallet() {
        String otherWalletProofUrl = VERIFIER_BASE + "/request/verify/" + AID
                + "?data=cardano:" + OTHER_PAYMENT_CREDENTIAL + "&sig=" + PROOF;
        expectAuthorized();
        mockServer.expect(once(), requestTo(otherWalletProofUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(identityIndexerService.verifyKeriBinding(AID, OTHER_PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    @Test
    void unauthorizedAid_returnsFalseWithoutCheckingProof() {
        mockServer.expect(once(), requestTo(AUTHORIZATION_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    @Test
    void missingProof_returnsFalseWithoutHttpRequest() {
        assertFalse(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, null));
        assertFalse(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, ""));
        mockServer.verify();
    }

    @Test
    void aidThatIsNotQb64_returnsFalseWithoutHttpRequest() {
        assertFalse(identityIndexerService.verifyKeriBinding("../presentations/x", PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    @Test
    void verifierUnreachable_returnsFalseWithoutThrowing() {
        mockServer.expect(once(), requestTo(AUTHORIZATION_URL))
                .andRespond(withException(new java.io.IOException("connection refused")));

        assertFalse(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    // ── caching ───────────────────────────────────────────────────────────────

    @Test
    void secondCallHitsCache_onlyOneVerification() {
        expectAuthorized();
        expectProofAccepted();

        assertTrue(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        assertTrue(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF)); // cache hit

        mockServer.verify(); // would fail if more requests were made
    }

    @Test
    void cachedResultForOneWallet_isNotReusedForAnotherWallet() {
        expectAuthorized();
        expectProofAccepted();
        assertTrue(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();

        mockServer.reset();
        expectAuthorized();
        mockServer.expect(once(), requestTo(VERIFIER_BASE + "/request/verify/" + AID
                        + "?data=cardano:" + OTHER_PAYMENT_CREDENTIAL + "&sig=" + PROOF))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(identityIndexerService.verifyKeriBinding(AID, OTHER_PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    // ── AUTH indexing ─────────────────────────────────────────────────────────

    @Test
    void authCert_storesProofAndVerificationResult() {
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.empty());
        expectAuthorized();
        expectProofAccepted();

        identityIndexerService.processNewCertificates(List.of(
                certificate(AUTH_HASH, PAYMENT_CREDENTIAL, authExtra(PROOF))));

        ArgumentCaptor<UVerifyCredentialEntity> saved = ArgumentCaptor.forClass(UVerifyCredentialEntity.class);
        verify(credentialRepository).save(saved.capture());
        assertEquals(PROOF, saved.getValue().getKeriProof());
        assertTrue(saved.getValue().isKeriVerified());
        mockServer.verify();
    }

    @Test
    void authCertWithoutProof_isIndexedUnverified() {
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.empty());

        identityIndexerService.processNewCertificates(List.of(
                certificate(AUTH_HASH, PAYMENT_CREDENTIAL, authExtra(null))));

        ArgumentCaptor<UVerifyCredentialEntity> saved = ArgumentCaptor.forClass(UVerifyCredentialEntity.class);
        verify(credentialRepository).save(saved.capture());
        assertFalse(saved.getValue().isKeriVerified());
        mockServer.verify(); // no requests recorded
    }

    @Test
    void authCertReusingAnotherWalletsHash_isSkipped() {
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(credentialEntity(AUTH_HASH)));

        identityIndexerService.processNewCertificates(List.of(
                certificate(AUTH_HASH, OTHER_PAYMENT_CREDENTIAL, authExtra(PROOF))));

        verify(credentialRepository, never()).save(any());
        mockServer.verify(); // no requests recorded
    }

    // ── REVOKE indexing ───────────────────────────────────────────────────────

    @Test
    void revokeCert_evictsCacheEntryForBinding() {
        expectAuthorized();
        expectProofAccepted();
        identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF);
        identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF); // cached
        mockServer.verify();

        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(entity));
        given(credentialRepository.save(any())).willReturn(entity);

        identityIndexerService.processNewCertificates(List.of(
                certificate("revoke-" + System.nanoTime(), PAYMENT_CREDENTIAL, revokeExtra("uverify_template_id"))));

        // MockRestServiceServer forbids new expectations after requests have been made,
        // so reset before the second phase.
        mockServer.reset();
        expectAuthorized();
        expectProofAccepted();

        assertTrue(identityIndexerService.verifyKeriBinding(AID, PAYMENT_CREDENTIAL, PROOF));
        mockServer.verify();
    }

    @Test
    void revokeCert_withShortTemplateIdKey_isProcessed() {
        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(entity));
        given(credentialRepository.save(any())).willReturn(entity);

        identityIndexerService.processNewCertificates(List.of(
                certificate("revoke-" + System.nanoTime(), PAYMENT_CREDENTIAL, revokeExtra("uv_tid"))));

        verify(credentialRepository).save(any());
        assertTrue(entity.isRevoked());
    }

    @Test
    void revokeCertFromAnotherWallet_isIgnored() {
        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(entity));

        identityIndexerService.processNewCertificates(List.of(
                certificate("revoke-" + System.nanoTime(), OTHER_PAYMENT_CREDENTIAL, revokeExtra("uverify_template_id"))));

        verify(credentialRepository, never()).save(any());
        assertFalse(entity.isRevoked());
    }

    // ── CredentialService integration ─────────────────────────────────────────

    @Test
    void resolveCredential_callsLiveVerifierAndReturnsResult() {
        given(credentialRepository.findByPaymentCredentialAndCredentialTypeAndRevokedFalse(PAYMENT_CREDENTIAL, "identity"))
                .willReturn(Optional.of(credentialEntity(AUTH_HASH)));
        expectAuthorized();
        expectProofAccepted();

        Optional<CredentialResponse> result = credentialService.resolveCredential(PAYMENT_CREDENTIAL, "identity");

        assertTrue(result.isPresent());
        assertTrue(result.get().isKeriVerified());
        mockServer.verify();
    }

    @Test
    void resolveCredential_withoutProof_isUnverifiedEvenForAuthorizedAid() {
        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        entity.setKeriProof(null);
        given(credentialRepository.findByPaymentCredentialAndCredentialTypeAndRevokedFalse(PAYMENT_CREDENTIAL, "identity"))
                .willReturn(Optional.of(entity));

        Optional<CredentialResponse> result = credentialService.resolveCredential(PAYMENT_CREDENTIAL, "identity");

        assertTrue(result.isPresent());
        assertFalse(result.get().isKeriVerified());
        mockServer.verify(); // no requests recorded
    }

    @Test
    void resolveCredential_cacheServesSubsequentCalls() {
        given(credentialRepository.findByPaymentCredentialAndCredentialTypeAndRevokedFalse(PAYMENT_CREDENTIAL, "identity"))
                .willReturn(Optional.of(credentialEntity(AUTH_HASH)));
        expectAuthorized();
        expectProofAccepted();

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
        verify(unconfigured, never()).verifyKeriBinding(any(), any(), any());
    }

    @Test
    void resolveByHash_activeCredential_callsLiveVerifier() {
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(credentialEntity(AUTH_HASH)));
        expectAuthorized();
        expectProofAccepted();

        Optional<CredentialResponse> result = credentialService.resolveCredentialByHash(AUTH_HASH);

        assertTrue(result.isPresent());
        assertTrue(result.get().isKeriVerified());
        mockServer.verify();
    }

    @Test
    void resolveByHash_revokedCredential_doesNotCallVerifier() {
        UVerifyCredentialEntity entity = credentialEntity(AUTH_HASH);
        entity.setRevoked(true);
        entity.setKeriVerified(true);
        given(credentialRepository.findByAuthCertHash(AUTH_HASH)).willReturn(Optional.of(entity));

        Optional<CredentialResponse> result = credentialService.resolveCredentialByHash(AUTH_HASH);

        assertTrue(result.isPresent());
        assertFalse(result.get().isActive());
        assertTrue(result.get().isKeriVerified()); // plain DB read, no HTTP
        mockServer.verify(); // no requests recorded
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void expectAuthorized() {
        mockServer.expect(once(), requestTo(AUTHORIZATION_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private void expectProofAccepted() {
        mockServer.expect(once(), requestTo(PROOF_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
    }

    private UVerifyCredentialEntity credentialEntity(String authHash) {
        return UVerifyCredentialEntity.builder()
                .authCertHash(authHash)
                .paymentCredential(PAYMENT_CREDENTIAL)
                .credentialType("identity")
                .keriAid(AID)
                .keriProof(PROOF)
                .build();
    }

    private String authExtra(String proof) {
        String proofField = proof == null ? "" : ",\"p\":\"" + proof + "\"";
        return "{\"uverify_template_id\":\"IdentityAuth\",\"t\":\"AUTH\",\"ct\":\"identity\",\"i\":\"" + AID + "\""
                + proofField + "}";
    }

    private String revokeExtra(String templateIdKey) {
        return "{\"" + templateIdKey + "\":\"IdentityAuth\",\"t\":\"REVOKE\",\"ct\":\"identity\",\"th\":\"" + AUTH_HASH + "\"}";
    }

    private UVerifyCertificateEntity certificate(String hash, String paymentCredential, String extra) {
        return UVerifyCertificateEntity.builder()
                .hash(hash)
                .extra(extra)
                .paymentCredential(paymentCredential)
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
                    .expireAfterWrite(Duration.ofHours(1))
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
