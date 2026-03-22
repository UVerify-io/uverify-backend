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

import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.List;

/**
 * Java representation of the {@code TokenizableDatum} Aiken sum type:
 * <pre>
 * Head { next: Option&lt;ByteArray&gt;, config: TokenizableConfig }  → alternative 0
 * Node { key, next, owner, asset_name, status }                 → alternative 1
 * </pre>
 */
public abstract class TokenizableDatum {

    public abstract ConstrPlutusData toPlutusData();

    // ── HEAD ─────────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Head extends TokenizableDatum {
        /** Hex-encoded key of the first node, or {@code null} for an empty list. */
        private String next;
        private TokenizableConfig config;

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData nextOption = next != null
                    ? ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(next)))
                    : ConstrPlutusData.of(1);
            return ConstrPlutusData.of(0, nextOption, config.toPlutusData());
        }

        /** Returns a new Head with the {@code next} pointer updated to {@code newNext}. */
        public Head withNext(String newNext) {
            return Head.builder().next(newNext).config(this.config).build();
        }
    }

    // ── NODE ─────────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Node extends TokenizableDatum {
        private String key;
        /** Hex-encoded key of the successor, or {@code null} for the last node. */
        private String next;
        /** Hex-encoded payment key hash of the NFT owner. */
        private String owner;
        /** Hex-encoded base asset name (without any CIP-68 prefix). */
        private String assetName;
        private boolean redeemed;

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData nextOption = next != null
                    ? ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(next)))
                    : ConstrPlutusData.of(1);
            PlutusData status = redeemed ? ConstrPlutusData.of(1) : ConstrPlutusData.of(0);
            return ConstrPlutusData.of(1,
                    BytesPlutusData.of(HexUtil.decodeHexString(key)),
                    nextOption,
                    BytesPlutusData.of(HexUtil.decodeHexString(owner)),
                    BytesPlutusData.of(HexUtil.decodeHexString(assetName)),
                    status
            );
        }

        /** Returns a new Node with the {@code next} pointer updated. */
        public Node withNext(String newNext) {
            return Node.builder()
                    .key(this.key).next(newNext).owner(this.owner)
                    .assetName(this.assetName).redeemed(this.redeemed)
                    .build();
        }
    }

    // ── PARSING ──────────────────────────────────────────────────────────────

    /**
     * Deserializes a {@link TokenizableDatum} from a hex-encoded inline-datum CBOR string.
     */
    public static TokenizableDatum fromInlineDatum(String inlineDatumHex) {
        try {
            PlutusData pd = PlutusData.deserialize(HexUtil.decodeHexString(inlineDatumHex));
            return fromPlutusData((ConstrPlutusData) pd);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize TokenizableDatum from: " + inlineDatumHex, e);
        }
    }

    public static TokenizableDatum fromPlutusData(ConstrPlutusData constr) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();
        int alt = (int) constr.getAlternative();

        if (alt == 0) {
            // Head
            ConstrPlutusData nextConstr = (ConstrPlutusData) fields.get(0);
            String next = nextConstr.getAlternative() == 0
                    ? HexUtil.encodeHexString(((BytesPlutusData) nextConstr.getData().getPlutusDataList().get(0)).getValue())
                    : null;
            TokenizableConfig config = TokenizableConfig.fromPlutusData((ConstrPlutusData) fields.get(1));
            return Head.builder().next(next).config(config).build();

        } else if (alt == 1) {
            // Node
            String key = HexUtil.encodeHexString(((BytesPlutusData) fields.get(0)).getValue());
            ConstrPlutusData nextConstr = (ConstrPlutusData) fields.get(1);
            String next = nextConstr.getAlternative() == 0
                    ? HexUtil.encodeHexString(((BytesPlutusData) nextConstr.getData().getPlutusDataList().get(0)).getValue())
                    : null;
            String owner = HexUtil.encodeHexString(((BytesPlutusData) fields.get(2)).getValue());
            String assetName = HexUtil.encodeHexString(((BytesPlutusData) fields.get(3)).getValue());
            boolean redeemed = ((ConstrPlutusData) fields.get(4)).getAlternative() == 1;
            return Node.builder().key(key).next(next).owner(owner).assetName(assetName).redeemed(redeemed).build();

        } else {
            throw new IllegalArgumentException("Unknown TokenizableDatum alternative: " + alt);
        }
    }
}
