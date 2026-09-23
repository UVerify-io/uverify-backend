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

package io.uverify.backend.devnet;

import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.script.domain.Datum;
import com.bloxbean.cardano.yaci.store.script.storage.DatumStorage;
import com.bloxbean.cardano.yaci.store.script.storage.impl.model.DatumEntity;
import com.bloxbean.cardano.yaci.store.script.storage.impl.repository.DatumRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class YanoDevnetTestConfiguration {

    /**
     * Records which transactions the UVerify transaction event pipeline has finished
     * with. The pipeline runs in its own database transaction that commits after the
     * indexer cursor is already visible, so the cursor alone is not enough to know that
     * state datum and certificate tables are up to date.
     */
    public static class ProcessedTransactions {
        private final Set<String> transactionHashes = ConcurrentHashMap.newKeySet();

        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE)
        public void onTransactionEvent(TransactionEvent transactionEvent) {
            transactionEvent.getTransactions().forEach(transaction -> transactionHashes.add(transaction.getTxHash()));
        }

        public boolean isProcessed(String transactionHash) {
            return transactionHashes.contains(transactionHash);
        }

        public void clear() {
            transactionHashes.clear();
        }
    }

    @Bean
    public ProcessedTransactions processedTransactions() {
        return new ProcessedTransactions();
    }

    /**
     * yaci-store 2.0.2.1 persists datums with a jOOQ MERGE that H2 rejects with
     * "column DATUM not found". Production runs on Postgres, which takes the INSERT ON
     * CONFLICT path. UVerify never reads the datum table, so the tests persist through
     * JPA instead.
     */
    @Bean
    public DatumStorage datumStorage(DatumRepository datumRepository) {
        return new DatumStorage() {
            @Override
            public void saveAll(Collection<Datum> datums) {
                datumRepository.saveAll(datums.stream()
                        .map(datum -> new DatumEntity(datum.getHash(), datum.getDatum(), datum.getCreatedAtTx()))
                        .toList());
            }

            @Override
            public void handleCommit(CommitEvent commitEvent) {
            }
        };
    }
}
