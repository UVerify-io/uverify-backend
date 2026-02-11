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
import com.fasterxml.jackson.annotation.JsonAlias;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.FeeReceiverEntity;
import io.uverify.backend.entity.UserCredentialEntity;
import io.uverify.backend.enums.CardanoNetwork;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BootstrapData {
    private String name;
    @JsonAlias({"whitelistedAddresses", "whitelisted_addresses"})
    private List<String> whitelistedAddresses;
    private Integer fee;
    @JsonAlias({"feeInterval", "fee_interval"})
    private Integer feeInterval;
    @JsonAlias({"feeReceiverAddresses", "fee_receiver_addresses"})
    private List<String> feeReceiverAddresses;
    private Long ttl;
    @JsonAlias({"transactionLimit", "transaction_limit"})
    private Integer transactionLimit;
    @JsonAlias({"batchSize", "batch_size"})
    private Integer batchSize;
    private Integer version;

    public static BootstrapData fromBootstrapDatumEntity(BootstrapDatumEntity bootstrapDatumEntity, CardanoNetwork network) {
        BootstrapData bootstrapData = new BootstrapData();
        bootstrapData.setName(bootstrapDatumEntity.getTokenName());
        bootstrapData.setWhitelistedAddresses(bootstrapDatumEntity.getAllowedCredentials().stream()
                .map(UserCredentialEntity::getCredential)
                .map(HexUtil::decodeHexString)
                .map(credential -> AddressProvider.getEntAddress(Credential.fromKey(credential), fromCardanoNetwork(network)).getAddress())
                .toList());

        bootstrapData.setFee(bootstrapDatumEntity.getFee());
        bootstrapData.setFeeInterval(bootstrapDatumEntity.getFeeInterval());
        bootstrapData.setFeeReceiverAddresses(bootstrapDatumEntity.getFeeReceivers().stream()
                .map(FeeReceiverEntity::getCredential)
                .map(HexUtil::decodeHexString)
                .map(credential -> AddressProvider.getEntAddress(Credential.fromKey(credential), fromCardanoNetwork(network)).getAddress()).toList());

        bootstrapData.setTtl(bootstrapDatumEntity.getTtl());
        bootstrapData.setTransactionLimit(bootstrapDatumEntity.getTransactionLimit());
        bootstrapData.setBatchSize(bootstrapDatumEntity.getBatchSize());
        bootstrapData.setVersion(bootstrapDatumEntity.getVersion());
        return bootstrapData;
    }
}
