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

import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.repository.CertificateRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UVerifyCertificateService {
    @Autowired
    private final CertificateRepository certificateRepository;

    @Autowired
    public UVerifyCertificateService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    public List<UVerifyCertificateEntity> getCertificatesByHash(String hash) {
        return certificateRepository.findAllByHash(hash);
    }

    public List<UVerifyCertificateEntity> getCertificatesByCredential(String credential) {
        return certificateRepository.findByPaymentCredential(credential);
    }

    @Transactional
    public void deleteAllCertificatesAfterSlot(long slot) {
        certificateRepository.deleteAllAfterSlot(slot);
    }

    public void saveAllCertificates(List<UVerifyCertificateEntity> UVerifyCertificateEntities) {
        certificateRepository.saveAll(UVerifyCertificateEntities);
    }

    public UVerifyCertificateEntity getCertificateByTransactionHash(String transactionHash, String dataHash) {
        return certificateRepository.findByTransactionHashAndDataHash(transactionHash, dataHash);
    }
}
