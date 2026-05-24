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
import io.uverify.backend.dto.CredentialResponse;
import io.uverify.backend.entity.UVerifyCredentialEntity;
import io.uverify.backend.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;
    private final IdentityIndexerService identityIndexerService;

    public List<CredentialResponse> resolveCredentials(String paymentCredential) {
        return credentialRepository
                .findByPaymentCredentialAndRevokedFalse(paymentCredential)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<CredentialResponse> resolveCredential(String paymentCredential, String credentialType) {
        return credentialRepository
                .findByPaymentCredentialAndCredentialTypeAndRevokedFalse(paymentCredential, credentialType)
                .map(this::toResponse);
    }

    public Optional<CredentialResponse> resolveCredentialByHash(String authHash) {
        return credentialRepository
                .findByAuthCertHash(authHash)
                .map(this::toResponseFromDB);
    }

    private CredentialResponse toResponseFromDB(UVerifyCredentialEntity entity) {
        return buildResponse(entity, entity.isKeriVerified());
    }

    private CredentialResponse toResponse(UVerifyCredentialEntity entity) {
        // When the vLEI Verifier is configured, check live (Caffeine-cached, evicted on
        // index/revoke events). Falls back to the DB value when not configured.
        boolean keriVerified = identityIndexerService.isVleiVerifierConfigured()
                ? identityIndexerService.checkVleiVerifier(entity.getKeriAid())
                : entity.isKeriVerified();
        return buildResponse(entity, keriVerified);
    }

    private CredentialResponse buildResponse(UVerifyCredentialEntity entity, boolean keriVerified) {
        Map<String, Object> acdc = null;
        if (entity.getAcdcAttributes() != null && !entity.getAcdcAttributes().isBlank()) {
            try {
                acdc = objectMapper.readValue(entity.getAcdcAttributes(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse acdc_attributes for authHash={}: {}", entity.getAuthCertHash(), e.getMessage());
            }
        }
        return CredentialResponse.builder()
                .authHash(entity.getAuthCertHash())
                .credentialType(entity.getCredentialType())
                .keriAid(entity.getKeriAid())
                .txHash(entity.getTxHash())
                .active(!entity.isRevoked())
                .keriVerified(keriVerified)
                .acdc(acdc)
                .build();
    }
}
