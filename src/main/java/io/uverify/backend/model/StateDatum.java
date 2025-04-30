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

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.util.ValidatorUtils;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.List;

import static io.uverify.backend.util.ValidatorUtils.*;

@Getter
@Setter
public class StateDatum {
    private String id;
    private String owner;
    private Integer fee;
    private Integer feeInterval;
    private List<byte[]> feeReceivers;
    private Long ttl;
    private Integer countdown;
    private List<UVerifyCertificate> uVerifyCertificates;

    private Integer batchSize;
    private String bootstrapDatumName;

    public static StateDatum fromBootstrapDatum(BootstrapDatum bootstrapDatum, String ownerCredential, List<UVerifyCertificate> uVerifyCertificates) {
        StateDatum stateDatum = new StateDatum();
        stateDatum.setOwner(ownerCredential);
        stateDatum.setFee(bootstrapDatum.getFee());
        stateDatum.setFeeInterval(bootstrapDatum.getFeeInterval());
        stateDatum.setFeeReceivers(bootstrapDatum.getFeeReceivers());
        stateDatum.setTtl(bootstrapDatum.getTtl());
        stateDatum.setCountdown(bootstrapDatum.getTransactionLimit());
        stateDatum.setUVerifyCertificates(uVerifyCertificates);
        stateDatum.setBatchSize(bootstrapDatum.getBatchSize());
        stateDatum.setBootstrapDatumName(bootstrapDatum.getTokenName());
        return stateDatum;
    }

    public static StateDatum fromBootstrapDatum(String inlineDatum, byte[] ownerCredential) {
        BootstrapDatum bootstrapDatum = BootstrapDatum.fromUtxoDatum(inlineDatum);
        return fromBootstrapDatum(bootstrapDatum, HexUtil.encodeHexString(ownerCredential), null);
    }

    public static StateDatum fromUtxoDatum(String inlineDatum) {
        StateDatum stateDatum = new StateDatum();
        try {
            List<PlutusData> datum = ((ConstrPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(inlineDatum))).getData().getPlutusDataList();
            stateDatum.setId(extractByteArrayFromPlutusData(datum.get(0)));
            stateDatum.setOwner(extractByteArrayFromPlutusData(datum.get(1)));
            stateDatum.setFee(extractIntegerFromPlutusData(datum.get(4)));
            stateDatum.setFeeInterval(extractIntegerFromPlutusData(datum.get(5)));
            stateDatum.setFeeReceivers(extractListFromPlutusData(datum.get(6)));
            stateDatum.setTtl(extractLongFromPlutusData(datum.get(7)));
            stateDatum.setCountdown(extractIntegerFromPlutusData(datum.get(8)));
            stateDatum.setUVerifyCertificates(UVerifyCertificate.listFromPlutusData(datum.get(9)));
            stateDatum.setBatchSize(extractIntegerFromPlutusData(datum.get(10)));
            stateDatum.setBootstrapDatumName(extractStringFromPlutusData(datum.get(11)));
        } catch (CborDeserializationException e) {
            throw new RuntimeException(e);
        }
        return stateDatum;
    }

    public static StateDatum fromPreviousStateDatum(StateDatum previousStateDatum) {
        StateDatum stateDatum = new StateDatum();
        stateDatum.setId(previousStateDatum.getId());
        stateDatum.setOwner(previousStateDatum.getOwner());
        stateDatum.setFee(previousStateDatum.getFee());
        stateDatum.setFeeInterval(previousStateDatum.getFeeInterval());
        stateDatum.setFeeReceivers(previousStateDatum.getFeeReceivers());
        stateDatum.setTtl(previousStateDatum.getTtl());
        stateDatum.setCountdown(previousStateDatum.getCountdown() - 1);
        stateDatum.setUVerifyCertificates(previousStateDatum.getUVerifyCertificates());
        stateDatum.setBatchSize(previousStateDatum.getBatchSize());
        stateDatum.setBootstrapDatumName(previousStateDatum.getBootstrapDatumName());
        return stateDatum;
    }

    public static StateDatum fromPreviousStateDatum(String inlineDatum) {
        StateDatum previousStateDatum = fromUtxoDatum(inlineDatum);
        return fromPreviousStateDatum(previousStateDatum);
    }

    public PlutusData toPlutusData(CardanoNetwork network) {
        PlutusData uVerifyCertificates = ListPlutusData.of(ConstrPlutusData.builder().alternative(1).data(ListPlutusData.of()).build());
        if (this.uVerifyCertificates != null) {
            uVerifyCertificates = ListPlutusData.of(
                    this.uVerifyCertificates.stream()
                            .map(UVerifyCertificate::toPlutusData)
                            .toList()
                            .toArray(new PlutusData[0])
            );
        }

        String stateId = this.id;
        if (stateId == null) {
            stateId = "00";
        }

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(stateId)),
                BytesPlutusData.of(HexUtil.decodeHexString(this.owner)),
                BytesPlutusData.of(HexUtil.decodeHexString(ValidatorUtils.getMintOrBurnAuthTokenHash(network))),
                BytesPlutusData.of(HexUtil.decodeHexString(ValidatorUtils.getUpdateStateTokenHash(network))),
                BigIntPlutusData.of(BigInteger.valueOf(this.fee)),
                BigIntPlutusData.of(BigInteger.valueOf(this.feeInterval)),
                ListPlutusData.of(
                        this.feeReceivers.stream()
                                .map(BytesPlutusData::of)
                                .toList()
                                .toArray(new PlutusData[0])
                ),
                BigIntPlutusData.of(BigInteger.valueOf(this.ttl)),
                BigIntPlutusData.of(BigInteger.valueOf(this.countdown)),
                uVerifyCertificates,
                BigIntPlutusData.of(BigInteger.valueOf(this.batchSize)),
                BytesPlutusData.of(this.bootstrapDatumName.getBytes())
        );
    }
}
