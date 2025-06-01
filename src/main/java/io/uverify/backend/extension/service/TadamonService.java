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
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.extension.UVerifyServiceExtension;
import io.uverify.backend.extension.dto.TadamonTransactionRequest;
import io.uverify.backend.extension.entity.TadamonTransactionEntity;
import io.uverify.backend.extension.repository.TadamonTransactionRepository;
import io.uverify.backend.model.StateDatum;
import io.uverify.backend.model.UVerifyCertificate;
import io.uverify.backend.service.CardanoBlockchainService;
import io.uverify.backend.util.ValidatorUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@AllArgsConstructor
@ConditionalOnProperty(value = "extensions.tadamon.enabled", havingValue = "true")
public class TadamonService implements UVerifyServiceExtension {

    @Autowired
    private final CardanoBlockchainService cardanoBlockchainService;

    @Autowired
    private final TadamonTransactionRepository tadamonTransactionRepository;

    @Autowired
    private final TadamonGoogleSheetsService tadamonGoogleSheetsService;

    @Value("${extensions.tadamon.allowed-addresses}")
    private final List<String> allowedAddresses;

    @Override
    public void processAddressUtxos(List<AddressUtxo> addressUtxos) {

    }

    @Override
    public void handleRollbackToSlot(long slot) {
        List<TadamonTransactionEntity> transactionEntities = tadamonTransactionRepository.findBySlotGreaterThan(slot);

        for (TadamonTransactionEntity transactionEntity : transactionEntities) {
            log.info("Handle rollback for tadamon certificate {} to slot {} for transaction {}",
                    transactionEntity.getCertificateDataHash(),
                    slot,
                    transactionEntity.getTransactionId());
            try {
                Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(transactionEntity.getTransactionHex()));
                Result<String> result = cardanoBlockchainService.submitTransaction(transaction);
                if (result.isSuccessful()) {
                    log.info("Transaction {} rolled back successfully and is now {}", transactionEntity.getTransactionId(), result.getResponse());
                    transactionEntity.setTransactionId(result.getValue());
                    transactionEntity.setCertificateCreationDate(LocalDateTime.now());
                    transactionEntity.setSlot(cardanoBlockchainService.getLatestSlot());
                    tadamonTransactionRepository.save(transactionEntity);

                    int row = tadamonGoogleSheetsService.findRowByDataHash(transactionEntity.getCertificateDataHash());
                    tadamonGoogleSheetsService.writeRowToSheet(transactionEntity, row);
                } else {
                    log.error("Failed to roll back transaction {}: {}", transactionEntity.getTransactionId(), result.getResponse());
                }
            } catch (Exception e) {
                log.error("Error while rolling back transaction {}: {}", transactionEntity.getTransactionId(), e.getMessage());
            }
        }
    }

    public Result<?> submit(TadamonTransactionRequest request) throws CborDeserializationException, CborSerializationException, ApiException {
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(request.getTransaction()));
        if (request.getWitnessSet() != null && !request.getWitnessSet().isEmpty()) {
            TransactionWitnessSet witnessSet = TransactionWitnessSet.deserialize((Map) CborSerializationUtil.deserialize(HexUtil.decodeHexString(request.getWitnessSet())));
            transaction.getWitnessSet().setVkeyWitnesses(witnessSet.getVkeyWitnesses());
        }

        if (transaction.getWitnessSet() == null || transaction.getWitnessSet().getVkeyWitnesses().isEmpty()) {
            return Result.error("Transaction witness set is empty. Make sure either the transaction is signed or the witness set is provided");
        }

        boolean signedByAllowedAddress = transaction.getWitnessSet().getVkeyWitnesses().stream().anyMatch(
                vkeyWitness -> {
                    String vkeyHash = HexUtil.encodeHexString(Blake2bUtil.blake2bHash224(vkeyWitness.getVkey()));
                    for (String address : allowedAddresses) {
                        Optional<byte[]> paymentCredential = new Address(address).getPaymentCredentialHash();
                        if (paymentCredential.isPresent()) {
                            String paymentCredentialHash = HexUtil.encodeHexString(paymentCredential.get());
                            if (paymentCredentialHash.equalsIgnoreCase(vkeyHash)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
        );

        if (!signedByAllowedAddress) {
           log.warn("Transaction not signed by whitelisted address");
        }

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
        tadamonTransactionEntity.setBeneficiarySigningDate(request.getBeneficiarySigningDate());

        TransactionOutput transactionOutput = transaction.getBody().getOutputs().stream().filter(
                ValidatorUtils::includesStateToken
        ).findFirst().orElse(null);

        if (transactionOutput == null) {
            return Result.error("Transaction output does not contain a state token nor UVerify certificates");
        }

        StateDatum stateDatum = StateDatum.fromUtxoDatum(transactionOutput.getInlineDatum().serializeToHex());

        if (stateDatum.getUVerifyCertificates().isEmpty()) {
            return Result.error("Transaction output does not contain UVerify certificates");
        } else if (stateDatum.getUVerifyCertificates().size() > 1) {
            return Result.error("Transaction output contains multiple UVerify certificates. Only one is allowed" +
                    " for the Tadamon extension");
        }

        UVerifyCertificate certificate = stateDatum.getUVerifyCertificates().get(0);
        tadamonTransactionEntity.setCertificateDataHash(certificate.getHash());

        Result<String> result = cardanoBlockchainService.submitTransaction(transaction);
        if (result.isSuccessful()) {
            tadamonTransactionEntity.setTransactionId(result.getValue());
            tadamonTransactionEntity.setCertificateCreationDate(LocalDateTime.now());
            tadamonTransactionEntity.setSlot(cardanoBlockchainService.getLatestSlot());
            tadamonTransactionRepository.save(tadamonTransactionEntity);

            int row = tadamonGoogleSheetsService.findRowByDataHash(tadamonTransactionEntity.getCertificateDataHash());
            tadamonGoogleSheetsService.writeRowToSheet(tadamonTransactionEntity, row);
        }

        return result;
    }
}
