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

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import io.uverify.backend.util.CardanoUtils;
import java.math.BigInteger;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SimulationUtils {

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
                                .blockTime(Date.from(Instant.now()).getTime())
                        .build());
            }
            return addressUtxos;
        } else {
            throw new ApiException("Error fetching transaction utxos");
        }
    }
}
