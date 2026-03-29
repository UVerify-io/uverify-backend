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
import java.util.Optional;

/**
 * Java representation of the {@code FractionizedDatum} Aiken sum type:
 * <pre>
 * CertificateHead { next: Option&lt;ByteArray&gt;, config: FractionizedConfig }  → alternative 0
 * CertificateNode { key, next, total_amount, remaining_amount, claimants,
 *                   asset_name, status }                                     → alternative 1
 * </pre>
 */
public abstract class FractionizedDatum {

    public static FractionizedDatum fromInlineDatum(String inlineDatumHex) {
        try {
            ConstrPlutusData pd = (ConstrPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(inlineDatumHex));
            int alt = (int) pd.getAlternative();
            if (alt == 0) {
                return parseFHead(pd);
            } else if (alt == 1) {
                return parseFNode(pd);
            }
            throw new IllegalArgumentException("Unknown FractionizedDatum alternative: " + alt);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize FractionizedDatum from: " + inlineDatumHex, e);
        }
    }

    private static FHead parseFHead(ConstrPlutusData constr) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();
        ConstrPlutusData nextConstr = (ConstrPlutusData) fields.get(0);
        Optional<byte[]> next = nextConstr.getAlternative() == 0
                ? Optional.of(((BytesPlutusData) nextConstr.getData().getPlutusDataList().get(0)).getValue())
                : Optional.empty();
        FractionizedConfig config = FractionizedConfig.fromPlutusData((ConstrPlutusData) fields.get(1));
        return FHead.builder().next(next).config(config).build();
    }

    private static FNode parseFNode(ConstrPlutusData constr) {
        List<PlutusData> fields = constr.getData().getPlutusDataList();

        byte[] key = ((BytesPlutusData) fields.get(0)).getValue();

        ConstrPlutusData nextConstr = (ConstrPlutusData) fields.get(1);
        Optional<byte[]> next = nextConstr.getAlternative() == 0
                ? Optional.of(((BytesPlutusData) nextConstr.getData().getPlutusDataList().get(0)).getValue())
                : Optional.empty();

        long totalAmount = ((BigIntPlutusData) fields.get(2)).getValue().longValue();
        long remainingAmount = ((BigIntPlutusData) fields.get(3)).getValue().longValue();

        List<byte[]> claimants = ((ListPlutusData) fields.get(4)).getPlutusDataList().stream()
                .map(d -> ((BytesPlutusData) d).getValue())
                .toList();

        byte[] assetName = ((BytesPlutusData) fields.get(5)).getValue();

        ConstrPlutusData statusConstr = (ConstrPlutusData) fields.get(6);
        boolean exhausted = statusConstr.getAlternative() == 1;

        return FNode.builder()
                .key(key).next(next).totalAmount(totalAmount).remainingAmount(remainingAmount)
                .claimants(claimants).assetName(assetName).exhausted(exhausted)
                .build();
    }

    public abstract ConstrPlutusData toPlutusData();

    // ── FHEAD (CertificateHead, alternative 0) ────────────────────────────────

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FHead extends FractionizedDatum {
        /**
         * Raw bytes of the first node's key, or {@link Optional#empty()} for an empty list.
         */
        private Optional<byte[]> next;
        private FractionizedConfig config;

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData nextOption = next.isPresent()
                    ? ConstrPlutusData.of(0, BytesPlutusData.of(next.get()))
                    : ConstrPlutusData.of(1);
            return ConstrPlutusData.of(0, nextOption, config.toPlutusData());
        }

        public FHead withNext(Optional<byte[]> newNext) {
            return FHead.builder().next(newNext).config(this.config).build();
        }
    }

    // ── FNODE (CertificateNode, alternative 1) ────────────────────────────────

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FNode extends FractionizedDatum {
        /** Raw bytes of this node's certificate hash key. */
        private byte[] key;
        /** Raw bytes of the successor key, or {@link Optional#empty()} for the last node. */
        private Optional<byte[]> next;
        private long totalAmount;
        private long remainingAmount;
        /** Raw payment key hashes; empty list means open access. */
        private List<byte[]> claimants;
        /** Raw bytes of the fungible token asset name. */
        private byte[] assetName;
        /**
         * Maps to {@code FractionizedNodeStatus}: {@code TokensAvailable} (alt 0) = false,
         * {@code TokensExhausted} (alt 1) = true.
         */
        private boolean exhausted;

        @Override
        public ConstrPlutusData toPlutusData() {
            PlutusData nextOption = next.isPresent()
                    ? ConstrPlutusData.of(0, BytesPlutusData.of(next.get()))
                    : ConstrPlutusData.of(1);

            PlutusData[] claimantItems = claimants.stream()
                    .map(c -> (PlutusData) BytesPlutusData.of(c))
                    .toArray(PlutusData[]::new);
            ListPlutusData claimantList = ListPlutusData.of(claimantItems);

            PlutusData status = exhausted ? ConstrPlutusData.of(1) : ConstrPlutusData.of(0);

            return ConstrPlutusData.of(1,
                    BytesPlutusData.of(key),
                    nextOption,
                    BigIntPlutusData.of(BigInteger.valueOf(totalAmount)),
                    BigIntPlutusData.of(BigInteger.valueOf(remainingAmount)),
                    claimantList,
                    BytesPlutusData.of(assetName),
                    status
            );
        }

        public FNode withNext(byte[] newNext) {
            return FNode.builder()
                    .key(this.key).next(Optional.ofNullable(newNext)).totalAmount(this.totalAmount)
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
}
