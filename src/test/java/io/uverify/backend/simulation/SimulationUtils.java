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

package io.uverify.backend.simulation;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.model.RedeemerTag;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.script.domain.TxScript;
import io.uverify.backend.util.CardanoUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SimulationUtils {

    private static String serializeDatum(PlutusData datum) {
        if (datum == null) {
            return null;
        }

        return datum.serializeToHex();
    }

    private static PlutusData deserializeDatum(String inlineDatum) {
        try {
            byte[] datumBytes = HexUtil.decodeHexString(inlineDatum);
            return ConstrPlutusData.deserialize(CborSerializationUtil.deserialize(datumBytes));
        } catch (Exception exception) {
            log.error("Unable to parse inline datum: " + exception.getMessage());
            return null;
        }
    }

    private static String getDatumHash(PlutusData datum) {
        if (datum == null) {
            return null;
        }
        return datum.getDatumHash();
    }

    public static List<TxScript> getTxScripts(String transactionHash, BackendService backendService, Transaction transaction) throws ApiException, InterruptedException {
        Thread.sleep(3000);

        List<Redeemer> redeemers = transaction.getWitnessSet().getRedeemers();

        Result<TransactionContent> transactionContentResult = backendService.getTransactionService().getTransaction(transactionHash);
        List<TxScript> txScripts = new ArrayList<>();

        Result<TxContentUtxo> transactionUtxoResult = backendService.getTransactionService().getTransactionUtxos(transactionHash);
        if (!transactionUtxoResult.isSuccessful()) {
            log.error("Unable to extract Utxos for transaction: " + transactionHash);
            return List.of();
        }
        List<TxContentUtxoOutputs> outputs = transactionUtxoResult.getValue().getOutputs();

        if (transactionContentResult.isSuccessful()) {
            TransactionContent transactionContent = transactionContentResult.getValue();
            Result<List<TxContentRedeemers>> transactionRedeemersResult = backendService.getTransactionService().getTransactionRedeemers(transactionHash);

            if (transactionRedeemersResult.isSuccessful()) {
                List<TxContentRedeemers> transactionRedeemers = transactionRedeemersResult.getValue();
                for (TxContentRedeemers transactionRedeemer : transactionRedeemers) {
                    PlutusData datum = null;
                    Optional<TxContentUtxoOutputs> maybeOutput = outputs.stream()
                            .filter(output -> output.getAmount().stream().anyMatch(amount ->
                                    amount.getUnit().startsWith(transactionRedeemer.getScriptHash())))
                            .findFirst();

                    if (maybeOutput.isPresent()) {
                        datum = deserializeDatum(maybeOutput.get().getInlineDatum());
                    }

                    Optional<Redeemer> optionalRedeemer = redeemers.stream().filter(txRedeemer ->
                                    txRedeemer.getData().getDatumHash().equals(transactionRedeemer.getRedeemerDataHash()))
                            .findFirst();

                    if (optionalRedeemer.isPresent()) {
                        Redeemer redeemer = optionalRedeemer.get();
                        txScripts.add(TxScript.builder()
                                .blockHash(transactionContent.getBlock())
                                .blockTime(transactionContent.getBlockTime())
                                .slot(transactionContent.getSlot())
                                .txHash(transactionHash)
                                .blockNumber(transactionContent.getBlockHeight())
                                .purpose(RedeemerTag.valueOf(transactionRedeemer.getPurpose().name()))
                                .datumHash(getDatumHash(datum))
                                .datum(serializeDatum(datum))
                                .redeemerData(redeemer.getData().serializeToHex())
                                .redeemerCbor(redeemer.getData().serializeToHex())
                                .redeemerDatahash(transactionRedeemer.getDatumHash())
                                .redeemerIndex(redeemer.getIndex().intValue())
                                .scriptHash(transactionRedeemer.getScriptHash())
                                .unitMem(new BigInteger(transactionRedeemer.getUnitMem()))
                                .unitSteps(new BigInteger(transactionRedeemer.getUnitSteps()))
                                .build());
                    } else {
                        log.warn("Redeemer not found in transaction.");
                    }
                }
            }
        }

        return txScripts;
    }

    public static List<AddressUtxo> getAddressUtxos(String transactionHash, BackendService backendService) throws ApiException, InterruptedException {
        long slot = CardanoUtils.getLatestSlot(backendService);
        long blockNumber = CardanoUtils.getLatestBlockNumber(backendService);
        String blockHash = CardanoUtils.getLatestBlockHash(backendService);
        Thread.sleep(3000);

        Result<TxContentUtxo> transactionUtxos = backendService.getTransactionService().getTransactionUtxos(transactionHash);
        if (transactionUtxos.isSuccessful()) {
            TxContentUtxo txContentUtxo = transactionUtxos.getValue();
            List<TxContentUtxoOutputs> outputs = txContentUtxo.getOutputs();
            List<AddressUtxo> addressUtxos = new ArrayList<>();
            for (TxContentUtxoOutputs output : outputs) {

                Credential credential = new Address(output.getAddress()).getPaymentCredential().orElse(null);
                String paymentCredential = null;
                if (credential != null) {
                    paymentCredential = HexUtil.encodeHexString(credential.getBytes());
                }

                addressUtxos.add(AddressUtxo.builder()
                        .amounts(output.getAmount().stream().map(amt -> {
                            String assetName = "lovelace";
                            String policyId = "";
                            if (amt.getUnit().length() > 56) {
                                policyId = amt.getUnit().substring(0, 56);
                                assetName = new String(HexUtil.decodeHexString(amt.getUnit().substring(56)));
                            }

                            return Amt.builder()
                                    .unit(amt.getUnit())
                                    .quantity(new BigInteger(amt.getQuantity()))
                                    .policyId(policyId)
                                    .assetName(assetName)
                                    .build();
                        }).toList())
                        .inlineDatum(output.getInlineDatum())
                        .txHash(transactionHash)
                        .outputIndex(output.getOutputIndex())
                        .slot(slot)
                        .blockNumber(blockNumber)
                        .blockHash(blockHash)
                        .ownerPaymentCredential(paymentCredential)
                        .referenceScriptHash(output.getReferenceScriptHash())
                        .blockTime(Date.from(Instant.now()).getTime())
                        .build());
            }
            return addressUtxos;
        } else {
            throw new ApiException("Error fetching transaction utxos");
        }
    }
}
