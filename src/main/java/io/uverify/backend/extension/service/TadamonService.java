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

package io.uverify.backend.extension.service;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.extension.UVerifyServiceExtension;
import io.uverify.backend.extension.dto.TadamonTransactionRequest;
import io.uverify.backend.extension.entity.TadamonTransactionEntity;
import io.uverify.backend.extension.repository.TadamonTransactionRepository;
import io.uverify.backend.service.CardanoBlockchainService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
@ConditionalOnProperty(value = "extensions.tadamon.enabled", havingValue = "true")
public class TadamonService implements UVerifyServiceExtension {

    @Autowired
    private final CardanoBlockchainService cardanoBlockchainService;

    @Autowired
    private final TadamonTransactionRepository tadamonTransactionRepository;

    @Override
    public void processAddressUtxos(List<AddressUtxo> addressUtxos) {

    }

    @Override
    public void handleRollbackToSlot(long slot) {

    }

    public Result<String> submit(TadamonTransactionRequest request) throws CborDeserializationException, CborSerializationException, ApiException {
        TransactionWitnessSet witnessSet = TransactionWitnessSet.deserialize((Map) CborSerializationUtil.deserialize(HexUtil.decodeHexString(request.getWitnessSet())));
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(request.getTransaction()));
        transaction.getWitnessSet().setVkeyWitnesses(witnessSet.getVkeyWitnesses());

        // TODO: Verify the issuer payment credential

        // TODO: Add transaction hex to the database for resubmission on rollback
        TadamonTransactionEntity tadamonTransactionEntity = new TadamonTransactionEntity();
        tadamonTransactionEntity.setTransactionHex(transaction.serializeToHex());
        tadamonTransactionEntity.setCsoName(request.getCso().getName());
        tadamonTransactionEntity.setCsoEmail(request.getCso().getEmail());
        tadamonTransactionEntity.setCsoOrganizationType(request.getCso().getOrganizationType());
        tadamonTransactionEntity.setCsoStatusApproved(request.getCso().getStatusApproved());
        tadamonTransactionEntity.setCsoRegistrationCountry(request.getCso().getRegistrationCountry());
        tadamonTransactionEntity.setCsoEstablishmentDate(request.getCso().getEstablishmentDate());
        tadamonTransactionEntity.setTadamonId(request.getTadamonId());
        tadamonTransactionEntity.setVeridianAid(request.getVeridianAid());
        tadamonTransactionEntity.setUndpSigningDate(request.getUndpSigningDate());

        Result<String> result = cardanoBlockchainService.submitTransaction(transaction);

        if (result.isSuccessful()) {
            tadamonTransactionEntity.setTransactionId(result.getResponse());
            tadamonTransactionRepository.save(tadamonTransactionEntity);
        }

        // TODO: Update the google form

        return result;
    }
}
