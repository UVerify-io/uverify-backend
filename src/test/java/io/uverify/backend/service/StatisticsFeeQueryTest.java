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

import io.uverify.backend.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "store.sync-auto-start=false")
@ActiveProfiles("h2")
class StatisticsFeeQueryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void batchedTransactionFeeIsCountedOnce() {
        String txHash = "aa11223344556677889900aabbccddeeff00112233445566778899aabbccddee";
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO transaction (tx_hash, fee, slot) VALUES (:hash, :fee, :slot)")
                .setParameter("hash", txHash)
                .setParameter("fee", 200000L)
                .setParameter("slot", 1L)
                .executeUpdate();

        for (int index = 0; index < 2; index++) {
            entityManager.createNativeQuery(
                    "INSERT INTO uverify_certificate " +
                    "(hash, payment_credential, block_hash, block_number, transaction_id, " +
                    "creation_time, slot, extra, hash_algorithm, state_datum_id) " +
                    "VALUES (:hash, 'cred', 'block', 1, :transactionId, CURRENT_TIMESTAMP, 1, '{}', 'SHA256', 'sd')")
                    .setParameter("hash", "hash-" + index)
                    .setParameter("transactionId", txHash)
                    .executeUpdate();
        }

        BigInteger total = transactionRepository.sumUVerifyCertificateFees();

        assertEquals(BigInteger.valueOf(200000L), total);
    }
}
