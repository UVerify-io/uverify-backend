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

package io.uverify.backend.extension.dto.fractionized;

import lombok.Data;

import java.util.List;

/**
 * Request body for the fractionized-certificate Insert transaction endpoint.
 */
@Data
public class BuildInsertRequest {
    /** Bech32 address of the allowed inserter (pays fees and must sign). */
    private String inserterAddress;
    /** Hex-encoded certificate hash that will become the node key. */
    private String key;
    /** Total number of fungible tokens available for claiming. */
    private long totalAmount;
    /**
     * Hex-encoded payment key hashes allowed to claim tokens.
     * An empty list means the certificate is open — any wallet may claim.
     */
    private List<String> claimants;
    /** Hex-encoded asset name for the fungible token to be minted on each claim. */
    private String assetName;
    /** Tx-hash used to derive the fractionized-certificate policy ID. */
    private String initUtxoTxHash;
    /** Output index used to derive the fractionized-certificate policy ID. */
    private int initUtxoOutputIndex;
    /**
     * Optional: name of the bootstrap datum to use for the UVerify state update.
     * Leave empty to let the service pick the cheapest available state.
     */
    private String bootstrapTokenName;
}
