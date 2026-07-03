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

import io.uverify.backend.entity.StatisticEntity;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.StatisticRepository;
import io.uverify.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    private CertificateRepository certificateRepository;
    private ExtensionManager extensionManager;
    private TransactionRepository transactionRepository;
    private StatisticRepository statisticRepository;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        certificateRepository = mock(CertificateRepository.class);
        extensionManager = mock(ExtensionManager.class);
        transactionRepository = mock(TransactionRepository.class);
        statisticRepository = mock(StatisticRepository.class);
        service = new StatisticsService(
                certificateRepository, extensionManager, transactionRepository, statisticRepository);
        when(extensionManager.addTransactionFees(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recomputeWritesFeesAndCategoryCounts() {
        when(certificateRepository.streamAllExtra()).thenReturn(Stream.of(
                "{\"uverify_template_id\":\"diploma\"}",
                "{\"uverify_template_id\":\"diploma\"}",
                "{}"));
        when(transactionRepository.sumUVerifyCertificateFees()).thenReturn(BigInteger.valueOf(500000L));

        service.recomputeStatistics();

        Map<String, Long> written = writtenRows();
        assertEquals(500000L, written.get("total_fees_lovelace"));
        assertEquals(2L, written.get("category_count:Student Certification"));
        assertEquals(1L, written.get("category_count:Notary"));
    }

    @Test
    void getTransactionFeesReadsRowOrZero() {
        when(statisticRepository.findById("total_fees_lovelace")).thenReturn(Optional.of(
                StatisticEntity.builder().statisticKey("total_fees_lovelace").statisticValue(777L)
                        .updatedAt(Instant.EPOCH).build()));
        assertEquals(777L, service.getTransactionFees());

        when(statisticRepository.findById("total_fees_lovelace")).thenReturn(Optional.empty());
        assertEquals(0L, service.getTransactionFees());
    }

    @Test
    void getCategoriesReadsPrefixedRows() {
        when(statisticRepository.findByStatisticKeyStartingWith("category_count:")).thenReturn(List.of(
                StatisticEntity.builder().statisticKey("category_count:Notary").statisticValue(3L)
                        .updatedAt(Instant.EPOCH).build(),
                StatisticEntity.builder().statisticKey("category_count:Diploma").statisticValue(4L)
                        .updatedAt(Instant.EPOCH).build()));

        Map<String, Integer> result = service.getTotalUVerifyCertificates();

        assertEquals(3, result.get("Notary"));
        assertEquals(4, result.get("Diploma"));
    }

    private Map<String, Long> writtenRows() {
        var captor = org.mockito.ArgumentCaptor.forClass(StatisticEntity.class);
        verify(statisticRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .collect(Collectors.toMap(StatisticEntity::getStatisticKey, StatisticEntity::getStatisticValue, (a, b) -> b));
    }
}
