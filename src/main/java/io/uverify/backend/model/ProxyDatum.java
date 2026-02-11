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

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

import static io.uverify.backend.util.ValidatorUtils.extractStringFromPlutusData;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyDatum {
    private String ScriptPointer;
    private String ScriptOwner;

    public static Optional<ProxyDatum> fromCbor(String cbor) {
        try {
            PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString(cbor));
            return Optional.of(fromPlutusData(plutusData));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static ProxyDatum fromPlutusData(PlutusData plutusData) {
        ProxyDatum datum = new ProxyDatum();
        List<PlutusData> plutusDataList = ((ConstrPlutusData) plutusData).getData().getPlutusDataList();
        datum.setScriptPointer(extractStringFromPlutusData(plutusDataList.get(0)));
        datum.setScriptOwner(extractStringFromPlutusData(plutusDataList.get(1)));
        return datum;
    }

    public PlutusData toPlutusData() {
        return ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.of(
                        BytesPlutusData.of(HexUtil.decodeHexString(this.ScriptPointer)),
                        BytesPlutusData.of(HexUtil.decodeHexString(this.ScriptOwner)
                        )))
                .build();
    }
}
