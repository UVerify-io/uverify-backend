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

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.TxInput;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoCache;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.TxInputRepository;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.service.CardanoBlockchainService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@SuppressWarnings("unused")
@Profile("!disable-indexer")
public class UVerifyStorage extends UtxoStorageImpl {

    @Autowired
    private final CardanoBlockchainService cardanoBlockchainService;

    @Autowired
    private final ExtensionManager extensionManager;

    public UVerifyStorage(UtxoRepository utxoRepository, TxInputRepository spentOutputRepository, DSLContext dsl, UtxoCache utxoCache, PlatformTransactionManager transactionManager, CardanoBlockchainService cardanoBlockchainService, ExtensionManager extensionManager) {
        super(utxoRepository, spentOutputRepository, dsl, utxoCache, transactionManager);
        this.cardanoBlockchainService = cardanoBlockchainService;
        this.extensionManager = extensionManager;
    }

    @Override
    public void saveUnspent(List<AddressUtxo> addressUtxoList) {
        List<AddressUtxo> processedByUVerifyCore = cardanoBlockchainService.processAddressUtxos(addressUtxoList);
        List<AddressUtxo> processedByExtensions = extensionManager.processAddressUtxos(addressUtxoList);
        Set<AddressUtxo> allProcessedUtxos = new HashSet<>();
        allProcessedUtxos.addAll(processedByUVerifyCore);
        allProcessedUtxos.addAll(processedByExtensions);
        if (!allProcessedUtxos.isEmpty()) {
            super.saveUnspent(new ArrayList<>(allProcessedUtxos));
        }
    }

    @Override
    public void saveSpent(List<TxInput> inputs) {
    }

    @Transactional
    @Override
    public int deleteUnspentBySlotGreaterThan(Long slot) {
        cardanoBlockchainService.handleRollbackToSlot(slot);
        extensionManager.handleRollbackToSlot(slot);
        return 0;
    }
}
