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

package io.uverify.backend.model;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.dto.BootstrapData;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.FeeReceiverEntity;
import io.uverify.backend.entity.UserCredentialEntity;
import io.uverify.backend.enums.CardanoNetwork;
import lombok.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static io.uverify.backend.util.ValidatorUtils.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BootstrapDatum {
    private List<byte[]> allowedCredentials;
    private String tokenName;
    private Integer fee;
    private Integer feeInterval;
    private List<byte[]> feeReceivers;
    private Long ttl;
    private Integer transactionLimit;
    private Integer batchSize;

    public static BootstrapDatum fromBootstrapData(BootstrapData bootstrapData) {
        return new BootstrapDatum(
                bootstrapData.getWhitelistedAddresses().stream().map(BootstrapDatum::extractCredentialFromAddress).toList(),
                bootstrapData.getName(),
                bootstrapData.getFee(),
                bootstrapData.getFeeInterval(),
                bootstrapData.getFeeReceiverAddresses().stream().map(BootstrapDatum::extractCredentialFromAddress).toList(),
                bootstrapData.getTtl(),
                bootstrapData.getTransactionLimit(),
                bootstrapData.getBatchSize()
        );
    }

    public static BootstrapDatum generateFrom(List<String> feeReceivers) {
        return new BootstrapDatum(
                List.of(),
                "UVerify_Default_Token",
                2000000,
                10,
                feeReceivers.stream().map(BootstrapDatum::extractCredentialFromAddress).toList(),
                System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000,
                1000,
                1
        );
    }

    public static byte[] extractCredentialFromAddress(String address) {
        Optional<byte[]> maybeServiceAccountPaymentCredential = new Address(address).getPaymentCredentialHash();
        if (maybeServiceAccountPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid payment address");
        }
        return maybeServiceAccountPaymentCredential.get();
    }

    public static BootstrapDatum fromBootstapDatumEntity(BootstrapDatumEntity bootstrapDatumEntity) {
        BootstrapDatum bootstrapDatum = new BootstrapDatum();
        bootstrapDatum.setAllowedCredentials(bootstrapDatumEntity.getAllowedCredentials().stream()
                .map(UserCredentialEntity::getCredential)
                .map(String::getBytes).toList());
        bootstrapDatum.setTokenName(bootstrapDatumEntity.getTokenName());
        bootstrapDatum.setFee(bootstrapDatumEntity.getFee());
        bootstrapDatum.setFeeInterval(bootstrapDatumEntity.getFeeInterval());
        bootstrapDatum.setFeeReceivers(bootstrapDatumEntity.getFeeReceivers().stream()
                .map(FeeReceiverEntity::getCredential)
                .map(String::getBytes).toList());
        bootstrapDatum.setTtl(bootstrapDatumEntity.getTtl());
        bootstrapDatum.setTransactionLimit(bootstrapDatumEntity.getTransactionLimit());
        bootstrapDatum.setBatchSize(bootstrapDatumEntity.getBatchSize());
        return bootstrapDatum;
    }

    public static BootstrapDatum fromLegancyUtxoDatum(String inlineDatum) {
        BootstrapDatum bootstrapDatum = new BootstrapDatum();
        try {
            List<PlutusData> datum = ((ConstrPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(inlineDatum))).getData().getPlutusDataList();
            bootstrapDatum.setAllowedCredentials(extractListFromPlutusData(datum.get(0)));
            bootstrapDatum.setTokenName(extractStringFromPlutusData(datum.get(2)));
            bootstrapDatum.setFee(extractIntegerFromPlutusData(datum.get(4)));
            bootstrapDatum.setFeeInterval(extractIntegerFromPlutusData(datum.get(5)));
            bootstrapDatum.setFeeReceivers(extractListFromPlutusData(datum.get(6)));
            bootstrapDatum.setTtl(extractLongFromPlutusData(datum.get(7)));
            bootstrapDatum.setTransactionLimit(extractIntegerFromPlutusData(datum.get(8)));
            bootstrapDatum.setBatchSize(extractIntegerFromPlutusData(datum.get(9)));
        } catch (CborDeserializationException e) {
            throw new RuntimeException(e);
        }
        return bootstrapDatum;
    }

    public static BootstrapDatum fromUtxoDatum(String inlineDatum) {
        BootstrapDatum bootstrapDatum = new BootstrapDatum();
        try {
            List<PlutusData> datum = ((ConstrPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(inlineDatum))).getData().getPlutusDataList();
            bootstrapDatum.setAllowedCredentials(extractListFromPlutusData(datum.get(0)));
            bootstrapDatum.setTokenName(extractStringFromPlutusData(datum.get(1)));
            bootstrapDatum.setFee(extractIntegerFromPlutusData(datum.get(2)));
            bootstrapDatum.setFeeInterval(extractIntegerFromPlutusData(datum.get(3)));
            bootstrapDatum.setFeeReceivers(extractListFromPlutusData(datum.get(4)));
            bootstrapDatum.setTtl(extractLongFromPlutusData(datum.get(5)));
            bootstrapDatum.setTransactionLimit(extractIntegerFromPlutusData(datum.get(6)));
            bootstrapDatum.setBatchSize(extractIntegerFromPlutusData(datum.get(7)));
        } catch (CborDeserializationException e) {
            throw new RuntimeException(e);
        }
        return bootstrapDatum;
    }

    public static BootstrapDatum fromScriptTxDatum(String inlineDatum) {
        BootstrapDatum bootstrapDatum = new BootstrapDatum();
        try {
            List<PlutusData> datum = ((ConstrPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(inlineDatum))).getData().getPlutusDataList();
            bootstrapDatum.setAllowedCredentials(extractListFromPlutusData(datum.get(0)));
            bootstrapDatum.setTokenName(extractStringFromPlutusData(datum.get(1)));
            bootstrapDatum.setFee(extractIntegerFromPlutusData(datum.get(2)));
            bootstrapDatum.setFeeInterval(extractIntegerFromPlutusData(datum.get(3)));
            bootstrapDatum.setFeeReceivers(extractListFromPlutusData(datum.get(4)));
            bootstrapDatum.setTtl(extractLongFromPlutusData(datum.get(5)));
            bootstrapDatum.setTransactionLimit(extractIntegerFromPlutusData(datum.get(6)));
            bootstrapDatum.setBatchSize(extractIntegerFromPlutusData(datum.get(7)));
        } catch (CborDeserializationException e) {
            throw new RuntimeException(e);
        }
        return bootstrapDatum;
    }

    public PlutusData toPlutusData() {
        return ConstrPlutusData.of(0,
                ListPlutusData.of(
                        this.allowedCredentials.stream()
                                .map(BytesPlutusData::of)
                                .toList()
                                .toArray(new PlutusData[0])
                ),
                BytesPlutusData.of(this.tokenName),
                BigIntPlutusData.of(BigInteger.valueOf(this.fee)),
                BigIntPlutusData.of(BigInteger.valueOf(this.feeInterval)),
                ListPlutusData.of(
                        this.feeReceivers.stream()
                                .map(BytesPlutusData::of)
                                .toList()
                                .toArray(new PlutusData[0])
                ),
                BigIntPlutusData.of(BigInteger.valueOf(this.ttl)),
                BigIntPlutusData.of(BigInteger.valueOf(this.transactionLimit)),
                BigIntPlutusData.of(BigInteger.valueOf(this.batchSize))
        );
    }

    @Deprecated(forRemoval = true)
    public PlutusData toPlutusData(CardanoNetwork network) {
        return ConstrPlutusData.of(0,
                ListPlutusData.of(
                        this.allowedCredentials.stream()
                                .map(BytesPlutusData::of)
                                .toList()
                                .toArray(new PlutusData[0])
                ),
                BytesPlutusData.of(HexUtil.decodeHexString(getMintOrBurnAuthTokenHash(network))),
                BytesPlutusData.of(this.tokenName),
                BytesPlutusData.of(HexUtil.decodeHexString(getUpdateStateTokenHash(network))),
                BigIntPlutusData.of(BigInteger.valueOf(this.fee)),
                BigIntPlutusData.of(BigInteger.valueOf(this.feeInterval)),
                ListPlutusData.of(
                        this.feeReceivers.stream()
                                .map(BytesPlutusData::of)
                                .toList()
                                .toArray(new PlutusData[0])
                ),
                BigIntPlutusData.of(BigInteger.valueOf(this.ttl)),
                BigIntPlutusData.of(BigInteger.valueOf(this.transactionLimit)),
                BigIntPlutusData.of(BigInteger.valueOf(this.batchSize))
        );
    }
}
