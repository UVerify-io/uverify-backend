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

package io.uverify.backend.dto;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.uverify.backend.entity.FeeReceiverEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.enums.CardanoNetwork;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StateData {
    private String id;
    private Integer fee;
    @JsonProperty("fee_interval")
    private Integer feeInterval;
    @JsonProperty("fee_receiver_addresses")
    private List<String> feeReceiverAddresses;
    private Long ttl;
    @JsonProperty("batch_size")
    private Integer batchSize;
    private Integer countdown;
    @JsonProperty("bootstrap_datum_name")
    private String bootstrapDatumName;

    public static StateData fromStateDatumEntity(StateDatumEntity stateDatumEntity, CardanoNetwork network) {
        StateData stateData = new StateData();
        stateData.setId(stateDatumEntity.getId());
        stateData.setFee(stateDatumEntity.getBootstrapDatum().getFee());
        stateData.setFeeInterval(stateDatumEntity.getBootstrapDatum().getFeeInterval());
        stateData.setFeeReceiverAddresses(stateDatumEntity.getBootstrapDatum().getFeeReceivers().stream()
                .map(FeeReceiverEntity::getCredential)
                .map(HexUtil::decodeHexString)
                .map(credential -> AddressProvider.getEntAddress(Credential.fromKey(credential), fromCardanoNetwork(network)).getAddress())
                .toList());
        stateData.setTtl(stateDatumEntity.getBootstrapDatum().getTtl());
        stateData.setBatchSize(stateDatumEntity.getBootstrapDatum().getBatchSize());
        stateData.setCountdown(stateDatumEntity.getCountdown());
        stateData.setBootstrapDatumName(stateDatumEntity.getBootstrapDatum().getTokenName());
        return stateData;
    }
}
