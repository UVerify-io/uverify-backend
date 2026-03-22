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

import java.math.BigInteger;
import java.util.List;

/**
 * Java representation of the {@code FractionizedDatum} Aiken sum type:
 * <pre>
 * FHead { next: Option&lt;ByteArray&gt;, config: FractionizedConfig }  → alternative 0
 * FNode { key, next, total_amount, remaining_amount, claimants, asset_name, status } → alternative 1
 * </pre>
 */
public abstract class FractionizedDatum {

    public abstract ConstrPlutusData toPlutusData();

    // ── FHEAD ────────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FHead extends FractionizedDatum {
        /** Hex-encoded key of the first node, or {@code null} for an empty list. */
        private String next;
        private FractionizedConfig config;

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData nextOption = next != null
                    ? ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(next)))
                    : ConstrPlutusData.of(1);
            return ConstrPlutusData.of(0, nextOption, config.toPlutusData());
        }

        public FHead withNext(String newNext) {
            return FHead.builder().next(newNext).config(this.config).build();
        }
    }

    // ── FNODE ────────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FNode extends FractionizedDatum {
        private String key;
        /** Hex-encoded key of the successor, or {@code null} for the last node. */
        private String next;
        private long totalAmount;
        private long remainingAmount;
        /** Hex-encoded payment key hashes; empty list means open access. */
        private List<String> claimants;
        /** Hex-encoded asset name for the fungible token to be minted per claim. */
        private String assetName;
        /** {@code true} when remainingAmount has reached zero (Exhausted). */
        private boolean exhausted;

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData nextOption = next != null
                    ? ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(next)))
                    : ConstrPlutusData.of(1);

            PlutusData[] claimantItems = (claimants == null ? List.<String>of() : claimants).stream()
                    .map(h -> (PlutusData) BytesPlutusData.of(HexUtil.decodeHexString(h)))
                    .toArray(PlutusData[]::new);
            ListPlutusData claimantList = ListPlutusData.of(claimantItems);

            // FractionAvailable = alternative 0, FractionExhausted = alternative 1
            PlutusData status = exhausted ? ConstrPlutusData.of(1) : ConstrPlutusData.of(0);

            return ConstrPlutusData.of(1,
                    BytesPlutusData.of(HexUtil.decodeHexString(key)),
                    nextOption,
                    BigIntPlutusData.of(BigInteger.valueOf(totalAmount)),
                    BigIntPlutusData.of(BigInteger.valueOf(remainingAmount)),
                    claimantList,
                    BytesPlutusData.of(HexUtil.decodeHexString(assetName)),
                    status
            );
        }

        public FNode withNext(String newNext) {
            return FNode.builder()
                    .key(this.key).next(newNext).totalAmount(this.totalAmount)
                    .remainingAmount(this.remainingAmount).claimants(this.claimants)
                    .assetName(this.assetName).exhausted(this.exhausted)
                    .build();
        }

        public FNode withRemainingAmount(long newRemaining) {
            return FNode.builder()
                    .key(this.key).next(this.next).totalAmount(this.totalAmount)
                    .remainingAmount(newRemaining).claimants(this.claimants)
                    .assetName(this.assetName).exhausted(newRemaining == 0)
                    .build();
        }
    }

    // ── PARSING ──────────────────────────────────────────────────────────────

    public static FractionizedDatum fromInlineDatum(String inlineDatumHex) {
        try {
            PlutusData pd = PlutusData.deserialize(HexUtil.decodeHexString(inlineDatumHex));
            return fromPlutusData((ConstrPlutusData) pd);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize FractionizedDatum from: " + inlineDatumHex, e);
        }
    }

    public static FractionizedDatum fromPlutusData(ConstrPlutusData constr) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();
        int alt = (int) constr.getAlternative();

        if (alt == 0) {
            ConstrPlutusData nextConstr = (ConstrPlutusData) fields.get(0);
            String next = nextConstr.getAlternative() == 0
                    ? HexUtil.encodeHexString(((BytesPlutusData) nextConstr.getData().getPlutusDataList().get(0)).getValue())
                    : null;
            FractionizedConfig config = FractionizedConfig.fromPlutusData((ConstrPlutusData) fields.get(1));
            return FHead.builder().next(next).config(config).build();

        } else if (alt == 1) {
            String key = HexUtil.encodeHexString(((BytesPlutusData) fields.get(0)).getValue());
            ConstrPlutusData nextConstr = (ConstrPlutusData) fields.get(1);
            String next = nextConstr.getAlternative() == 0
                    ? HexUtil.encodeHexString(((BytesPlutusData) nextConstr.getData().getPlutusDataList().get(0)).getValue())
                    : null;
            long totalAmount = ((BigIntPlutusData) fields.get(2)).getValue().longValue();
            long remainingAmount = ((BigIntPlutusData) fields.get(3)).getValue().longValue();

            List<String> claimants = ((ListPlutusData) fields.get(4)).getPlutusDataList().stream()
                    .map(d -> HexUtil.encodeHexString(((BytesPlutusData) d).getValue()))
                    .toList();

            String assetName = HexUtil.encodeHexString(((BytesPlutusData) fields.get(5)).getValue());
            boolean exhausted = ((ConstrPlutusData) fields.get(6)).getAlternative() == 1;

            return FNode.builder()
                    .key(key).next(next).totalAmount(totalAmount).remainingAmount(remainingAmount)
                    .claimants(claimants).assetName(assetName).exhausted(exhausted)
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown FractionizedDatum alternative: " + alt);
        }
    }
}
