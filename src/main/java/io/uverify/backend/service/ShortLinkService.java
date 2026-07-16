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

import io.uverify.backend.entity.ShortLinkEntity;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.ShortLinkRepository;
import io.uverify.backend.util.ShortCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class ShortLinkService {

    private final ShortLinkRepository shortLinkRepository;
    private final CertificateRepository certificateRepository;

    public ShortLinkService(ShortLinkRepository shortLinkRepository, CertificateRepository certificateRepository) {
        this.shortLinkRepository = shortLinkRepository;
        this.certificateRepository = certificateRepository;
    }

    public Optional<String> resolve(String code) {
        if (!ShortCode.isValid(code)) {
            return Optional.empty();
        }
        Optional<ShortLinkEntity> existing = shortLinkRepository.findById(code);
        if (existing.isPresent()) {
            return existing.map(ShortLinkEntity::getCertificateHash);
        }
        return certificateRepository.findByHashStartingWith(ShortCode.hexPrefix(code)).stream()
                .map(certificate -> certificate.getHash().toLowerCase())
                .filter(hash -> matchesShortCode(hash, code))
                .findFirst()
                .flatMap(hash -> {
                    try {
                        return Optional.of(shortLinkRepository.save(ShortLinkEntity.builder()
                                .shortCode(code)
                                .certificateHash(hash)
                                .clickCount(0L)
                                .createdAt(Date.from(Instant.now()))
                                .build()).getCertificateHash());
                    } catch (DataIntegrityViolationException ignored) {
                        // Another request created the mapping concurrently.
                        return shortLinkRepository.findById(code).map(ShortLinkEntity::getCertificateHash);
                    }
                });
    }

    public void registerClick(String code) {
        shortLinkRepository.incrementClickCount(code);
    }

    // Certificate hashes come from on-chain data and are not guaranteed to be
    // long enough for the short code derivation. Treat non-decodable hashes as
    // non-matches instead of failing the whole resolution.
    private static boolean matchesShortCode(String certificateHash, String code) {
        try {
            return ShortCode.fromHash(certificateHash).equals(code);
        } catch (RuntimeException malformedHash) {
            return false;
        }
    }
}
