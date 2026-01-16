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

import com.bloxbean.cardano.client.plutus.spec.*;
import io.uverify.backend.enums.UVerifyScriptPurpose;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import static io.uverify.backend.util.ValidatorUtils.extractIntegerFromPlutusData;
import static io.uverify.backend.util.ValidatorUtils.extractStringFromPlutusData;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StateRedeemer {
    private UVerifyScriptPurpose purpose;
    private int countdown;
    private List<UVerifyCertificate> certificates;

    public static StateRedeemer fromPlutusData(PlutusData plutusData) {
        StateRedeemer redeemer = new StateRedeemer();
        List<PlutusData> plutusDataList = ((ConstrPlutusData) plutusData).getData().getPlutusDataList();
        redeemer.setPurpose(UVerifyScriptPurpose.fromValue(extractStringFromPlutusData(plutusDataList.get(0))));
        redeemer.setCountdown(extractIntegerFromPlutusData(plutusDataList.get(1)));

        List<PlutusData> certificates = ((ListPlutusData) plutusDataList.get(2)).getPlutusDataList();
        ArrayList<UVerifyCertificate> uverifyCertificates = new ArrayList<>();
        for (PlutusData certificate : certificates) {
            uverifyCertificates.add(UVerifyCertificate.fromPlutusData(certificate));
        }
        redeemer.setCertificates(uverifyCertificates);
        return redeemer;
    }

    public PlutusData toPlutusData() {
        PlutusData uVerifyCertificates = ListPlutusData.of(ConstrPlutusData.builder().alternative(1).data(ListPlutusData.of()).build());
        if (this.certificates != null) {
            uVerifyCertificates = ListPlutusData.of(
                    this.certificates.stream()
                            .map(UVerifyCertificate::toPlutusData)
                            .toList()
                            .toArray(new PlutusData[0])
            );
        }

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(purpose.getValue()),
                BigIntPlutusData.of(countdown),
                uVerifyCertificates
        );
    }
}
