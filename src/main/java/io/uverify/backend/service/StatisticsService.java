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

import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.transaction.storage.impl.model.TxnEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.dto.UsageStatistics;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.UseCaseCategory;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.TransactionRepository;
import io.uverify.backend.storage.UVerifyStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class StatisticsService {

    @Autowired
    private final CertificateRepository certificateRepository;

    @Autowired
    private final ExtensionManager extensionManager;

    @Autowired
    private final TransactionRepository transactionRepository;

    @Autowired
    public StatisticsService(CertificateRepository certificateRepository, UVerifyStorage uVerifyStorage, ExtensionManager extensionManager, TransactionRepository transactionRepository) {
        this.certificateRepository = certificateRepository;
        this.extensionManager = extensionManager;
        this.transactionRepository = transactionRepository;
    }

    public Map<String, Integer> getTotalUVerifyCertificates() {
        List<UVerifyCertificateEntity> certificates = certificateRepository.findAll();

        final ObjectMapper mapper = new ObjectMapper();
        UsageStatistics usageStatistics = new UsageStatistics();

        for (UVerifyCertificateEntity certificate : certificates) {
            if (certificate.getExtra() == null || certificate.getExtra().isEmpty()) {
                usageStatistics.addCertificateToCategory(UseCaseCategory.NOTARY);
            } else {
                try {
                    JsonNode root = mapper.readTree(certificate.getExtra());
                    JsonNode templateId = root.get("uverify_template_id");

                    if (templateId == null || templateId.isNull()) {
                        usageStatistics.addCertificateToCategory(UseCaseCategory.NOTARY);
                    } else {
                        switch (templateId.asText()) {
                            case "tadamon" -> usageStatistics.addCertificateToCategory(UseCaseCategory.IDENTITY);
                            case "socialHub", "linktree" -> usageStatistics.addCertificateToCategory(UseCaseCategory.CONNECTED_GOODS);
                            case "diploma" -> usageStatistics.addCertificateToCategory(UseCaseCategory.STUDENT_CERTIFICATION);
                            default -> usageStatistics.addCertificateToCategory(UseCaseCategory.NOTARY);
                        }
                    }
                } catch (Exception exception) {
                    log.debug("Error parsing metadata for certificate with hash: {}", certificate.getHash(), exception);
                    usageStatistics.addCertificateToCategory(UseCaseCategory.NOTARY);
                }
            }
        }

        extensionManager.addUsageStatistics(usageStatistics);
        return usageStatistics.getUseCaseStatistics();
    }

    public Long getTransactionFees() {
        List<UVerifyCertificateEntity> certificates = certificateRepository.findAll();
        BigInteger totalFees = BigInteger.ZERO;

        for (UVerifyCertificateEntity certificate : certificates) {
            Optional<TxnEntity> transaction = transactionRepository.findById(certificate.getTransactionId());
            if (transaction.isPresent()) {
                totalFees = totalFees.add(transaction.get().getFee());
            } else {
                log.warn("Transaction not found for UTXO with hash: {}", certificate.getTransactionId());
            }
        }

        totalFees = extensionManager.addTransactionFees(totalFees);
        return totalFees.longValue();
    }

    @EventListener
    @Transactional
    @SuppressWarnings({"unused", "rawtypes"})
    public void reactOnCommitEvent(CommitEvent commitEvent) {
        transactionRepository.deleteIrrelevantTransactions();
    }
}
