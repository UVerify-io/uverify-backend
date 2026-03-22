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

package io.uverify.backend.extension.validators.fractionized;

import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.List;

/**
 * Represents the {@code FractionizedConfig} Aiken type:
 * <pre>
 * FractionizedConfig {
 *   uverify_validator_hash: ByteArray,
 *   proxy_policy_id: ByteArray,
 *   allowed_inserters: List&lt;VerificationKeyHash&gt;,
 *   deployer: VerificationKeyHash,
 * }
 * </pre>
 * Serialized as {@code ConstrPlutusData(0, [...])}.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FractionizedConfig {
    private String uverifyValidatorHash;
    private String proxyPolicyId;
    private List<String> allowedInserters;
    private String deployer;

    public ConstrPlutusData toPlutusData() {
        PlutusData[] inserterItems = allowedInserters.stream()
                .map(h -> (PlutusData) BytesPlutusData.of(HexUtil.decodeHexString(h)))
                .toArray(PlutusData[]::new);
        ListPlutusData inserterList = ListPlutusData.of(inserterItems);

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(uverifyValidatorHash)),
                BytesPlutusData.of(HexUtil.decodeHexString(proxyPolicyId)),
                inserterList,
                BytesPlutusData.of(HexUtil.decodeHexString(deployer))
        );
    }

    public static FractionizedConfig fromPlutusData(ConstrPlutusData constr) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();
        String uvHash = HexUtil.encodeHexString(((BytesPlutusData) fields.get(0)).getValue());
        String proxyId = HexUtil.encodeHexString(((BytesPlutusData) fields.get(1)).getValue());

        List<String> inserters = ((ListPlutusData) fields.get(2)).getPlutusDataList().stream()
                .map(d -> HexUtil.encodeHexString(((BytesPlutusData) d).getValue()))
                .toList();

        String dep = HexUtil.encodeHexString(((BytesPlutusData) fields.get(3)).getValue());

        return FractionizedConfig.builder()
                .uverifyValidatorHash(uvHash)
                .proxyPolicyId(proxyId)
                .allowedInserters(inserters)
                .deployer(dep)
                .build();
    }
}
