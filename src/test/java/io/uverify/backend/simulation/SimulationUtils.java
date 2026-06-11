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

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;

import java.math.BigInteger;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;

public class SimulationUtils {

    public static AddressUtxo simulateAddressUtxo(String txHash, int outputIndex, long slot, String ownerPaymentCredential,
                                                  String assetName, String policyId, String unit, String inlineDatum) {

        ArrayList<Amt> amounts = new ArrayList<>();
        amounts.add(Amt.builder()
                .assetName("lovelace")
                .unit("lovelace")
                .quantity(BigInteger.valueOf(1767100))
                .build());

        if (unit != null && !unit.isEmpty()) {
            amounts.add(Amt.builder()
                    .assetName(assetName)
                    .policyId(policyId)
                    .unit(unit)
                    .quantity(BigInteger.valueOf(1))
                    .build());
        }

        AddressUtxo addressUtxo = AddressUtxo.builder()
                .txHash(txHash)
                .outputIndex(outputIndex)
                .ownerPaymentCredential(ownerPaymentCredential)
                .amounts(amounts)
                .inlineDatum(inlineDatum)
                .slot(slot)
                .blockNumber(0L)
                .blockHash("")
                .blockTime(Date.from(Instant.now()).getTime())
                .build();

        if (inlineDatum != null && !inlineDatum.isEmpty()) {
            addressUtxo.setInlineDatum(inlineDatum);
        }
        return addressUtxo;
    }
}
