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

package io.uverify.backend.extension.dto.tokenizable;

import lombok.Data;

/**
 * Request body for the Insert transaction endpoint.
 * <p>
 * The Insert transaction is a combined UVerify certificate submission +
 * tokenizable-certificate node minting. The {@code key} acts as both the
 * certificate hash submitted to UVerify state and the on-chain node key in
 * the sorted linked list.
 * <p>
 * <b>EUTXO constraint note:</b> Due to Cardano's EUTXO rules, the HEAD UTxO
 * must be in {@code reference_inputs} for the MINT validator while the
 * predecessor node must be in {@code inputs} for the SPEND validator.
 * If HEAD would be the predecessor (i.e. the list is empty, or the new key is
 * smaller than all existing keys), the transaction cannot be built and an
 * error is returned.
 */
@Data
public class BuildInsertRequest {
    /** Bech32 address of the allowed inserter (pays fees and must sign). */
    private String inserterAddress;
    /**
     * Hex-encoded certificate hash that will become the node key.
     * This same value is submitted to UVerify state as the certificate hash.
     */
    private String key;
    /** Hex-encoded payment key hash of the wallet that will own the NFT. */
    private String ownerPubKeyHash;
    /**
     * Hex-encoded base asset name for the NFT.
     * If CIP-68 is configured, user ({@code 000de140}) and reference ({@code 000643b0})
     * token names are derived from this value automatically.
     */
    private String assetName;
    /** Tx-hash used to derive the tokenizable-certificate policy ID. */
    private String initUtxoTxHash;
    /** Output index used to derive the tokenizable-certificate policy ID. */
    private int initUtxoOutputIndex;
    /**
     * Optional: name of the bootstrap datum to use for the UVerify state update.
     * Leave empty to let the service pick the cheapest available state for the inserter.
     */
    private String bootstrapTokenName;
}
