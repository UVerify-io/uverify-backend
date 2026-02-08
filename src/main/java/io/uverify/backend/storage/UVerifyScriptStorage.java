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

package io.uverify.backend.storage;

import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.bloxbean.cardano.yaci.store.script.domain.TxScript;
import com.bloxbean.cardano.yaci.store.script.storage.impl.TxScriptStorageImpl;
import com.bloxbean.cardano.yaci.store.script.storage.impl.mapper.ScriptMapper;
import com.bloxbean.cardano.yaci.store.script.storage.impl.repository.TxScriptRepository;
import io.uverify.backend.service.CardanoBlockchainService;
import io.uverify.backend.util.ValidatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@SuppressWarnings("unused")
@Component
@Profile("!disable-indexer")
public class UVerifyScriptStorage extends TxScriptStorageImpl {
    @Autowired
    private final CardanoBlockchainService cardanoBlockchainService;

    public UVerifyScriptStorage(TxScriptRepository txScriptRepository, ScriptMapper scriptMapper, CardanoBlockchainService cardanoBlockchainService, ValidatorHelper validatorHelper) {
        super(txScriptRepository, scriptMapper);
        this.cardanoBlockchainService = cardanoBlockchainService;
    }

    @Override
    public void saveAll(List<TxScript> txScripts) {
    }

    @EventListener
    @Transactional
    public void handleScriptTransactionEvent(TransactionEvent transactionEvent) {
        cardanoBlockchainService.processTransactionEvent(transactionEvent);
    }
}
