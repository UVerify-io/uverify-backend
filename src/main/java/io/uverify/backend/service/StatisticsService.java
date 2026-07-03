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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.dto.UsageStatistics;
import io.uverify.backend.entity.StatisticEntity;
import io.uverify.backend.enums.UseCaseCategory;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.StatisticRepository;
import io.uverify.backend.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Slf4j
public class StatisticsService {

    private static final String TOTAL_FEES_KEY = "total_fees_lovelace";
    private static final String CATEGORY_KEY_PREFIX = "category_count:";

    private final CertificateRepository certificateRepository;
    private final ExtensionManager extensionManager;
    private final TransactionRepository transactionRepository;
    private final StatisticRepository statisticRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StatisticsService(CertificateRepository certificateRepository,
                             ExtensionManager extensionManager,
                             TransactionRepository transactionRepository,
                             StatisticRepository statisticRepository) {
        this.certificateRepository = certificateRepository;
        this.extensionManager = extensionManager;
        this.transactionRepository = transactionRepository;
        this.statisticRepository = statisticRepository;
    }

    public Map<String, Integer> getTotalUVerifyCertificates() {
        Map<String, Integer> result = new HashMap<>();
        for (StatisticEntity entity : statisticRepository.findByStatisticKeyStartingWith(CATEGORY_KEY_PREFIX)) {
            String displayName = entity.getStatisticKey().substring(CATEGORY_KEY_PREFIX.length());
            result.put(displayName, entity.getStatisticValue().intValue());
        }
        return result;
    }

    public Long getTransactionFees() {
        return statisticRepository.findById(TOTAL_FEES_KEY)
                .map(StatisticEntity::getStatisticValue)
                .orElse(0L);
    }

    @Scheduled(
            fixedDelayString = "${statistics.recompute-interval-ms:600000}",
            initialDelayString = "${statistics.recompute-initial-delay-ms:15000}")
    @Transactional
    public void recomputeStatistics() {
        Instant now = Instant.now();
        recomputeCategories(now);
        recomputeFees(now);
    }

    private void recomputeCategories(Instant now) {
        UsageStatistics usageStatistics = new UsageStatistics();
        try (Stream<String> extras = certificateRepository.streamAllExtra()) {
            extras.forEach(extra -> usageStatistics.addCertificateToCategory(categorize(extra, objectMapper)));
        }
        extensionManager.addUsageStatistics(usageStatistics);

        Map<String, Integer> categoryCounts = usageStatistics.getUseCaseStatistics();
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            upsert(CATEGORY_KEY_PREFIX + entry.getKey(), entry.getValue().longValue(), now);
        }
    }

    private void recomputeFees(Instant now) {
        BigInteger totalFees = transactionRepository.sumUVerifyCertificateFees();
        totalFees = extensionManager.addTransactionFees(totalFees);
        upsert(TOTAL_FEES_KEY, totalFees.longValue(), now);
    }

    private void upsert(String key, long value, Instant now) {
        statisticRepository.save(StatisticEntity.builder()
                .statisticKey(key)
                .statisticValue(value)
                .updatedAt(now)
                .build());
    }

    protected static UseCaseCategory categorize(String extra, ObjectMapper mapper) {
        if (extra == null || extra.isEmpty()) {
            return UseCaseCategory.NOTARY;
        }
        try {
            JsonNode templateId = mapper.readTree(extra).get("uverify_template_id");
            if (templateId == null || templateId.isNull()) {
                return UseCaseCategory.NOTARY;
            }
            return switch (templateId.asText()) {
                case "tadamon" -> UseCaseCategory.IDENTITY;
                case "socialHub", "linktree", "productVerification" -> UseCaseCategory.CONNECTED_GOODS;
                case "diploma" -> UseCaseCategory.STUDENT_CERTIFICATION;
                default -> UseCaseCategory.NOTARY;
            };
        } catch (Exception exception) {
            return UseCaseCategory.NOTARY;
        }
    }

    @EventListener
    @Transactional
    @SuppressWarnings({"unused", "rawtypes"})
    public void reactOnCommitEvent(CommitEvent commitEvent) {
        transactionRepository.deleteIrrelevantTransactions();
    }
}
