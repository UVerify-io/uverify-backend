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

package io.uverify.backend.extension.validators.tokenizable;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.List;

/**
 * Represents the {@code TokenizableConfig} Aiken type:
 * <pre>
 * TokenizableConfig {
 *   uverify_validator_hash: ByteArray,
 *   proxy_policy_id: ByteArray,
 *   allowed_inserters: List&lt;VerificationKeyHash&gt;,
 *   deployer: VerificationKeyHash,
 *   cip68_script_address: Option&lt;ByteArray&gt;,
 * }
 * </pre>
 * Serialized as {@code ConstrPlutusData(0, [...])}.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TokenizableConfig {
    private String uverifyValidatorHash;
    private List<String> allowedInserters;
    private String deployer;
    /**
     * Hex-encoded script hash; null means None (no CIP-68 support).
     */
    private String cip68ScriptAddress;

    public static TokenizableConfig fromPlutusData(ConstrPlutusData constr) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();
        String uvHash = HexUtil.encodeHexString(((BytesPlutusData) fields.get(0)).getValue());

        List<String> inserters = ((ListPlutusData) fields.get(1)).getPlutusDataList().stream()
                .map(d -> HexUtil.encodeHexString(((BytesPlutusData) d).getValue()))
                .toList();

        String dep = HexUtil.encodeHexString(((BytesPlutusData) fields.get(2)).getValue());

        ConstrPlutusData cip68Constr = (ConstrPlutusData) fields.get(3);
        String cip68 = null;
        if (cip68Constr.getAlternative() == 0) {
            cip68 = HexUtil.encodeHexString(((BytesPlutusData) cip68Constr.getData().getPlutusDataList().get(0)).getValue());
        }

        return TokenizableConfig.builder()
                .uverifyValidatorHash(uvHash)
                .allowedInserters(inserters)
                .deployer(dep)
                .cip68ScriptAddress(cip68)
                .build();
    }

    public ConstrPlutusData toPlutusData() {
        PlutusData[] inserterItems = allowedInserters.stream()
                .map(h -> (PlutusData) BytesPlutusData.of(HexUtil.decodeHexString(h)))
                .toArray(PlutusData[]::new);
        ListPlutusData inserterList = ListPlutusData.of(inserterItems);

        PlutusData cip68Option = cip68ScriptAddress != null
                ? ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(cip68ScriptAddress)))
                : ConstrPlutusData.of(1);

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(uverifyValidatorHash)),
                inserterList,
                BytesPlutusData.of(HexUtil.decodeHexString(deployer)),
                cip68Option
        );
    }
}
