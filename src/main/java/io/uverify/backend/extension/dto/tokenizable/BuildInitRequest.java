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

import io.uverify.backend.extension.validators.tokenizable.TokenizableConfig;
import lombok.Data;

/**
 * Request body for the tokenizable-certificate Init transaction endpoint.
 * <p>
 * Init always creates both the HEAD node and the first certificate node in one
 * atomic transaction (empty lists are not permitted by the on-chain validator).
 */
@Data
public class BuildInitRequest {
    /** Bech32 address of the deployer (pays fees and must sign). */
    private String deployerAddress;
    /** Tx-hash of the one-shot init UTxO (must exist in deployerAddress). */
    private String initUtxoTxHash;
    /** Output index of the one-shot init UTxO. */
    private int initUtxoOutputIndex;
    /** Configuration embedded in the HEAD datum. */
    private TokenizableConfig config;
    /** Hex-encoded certificate hash that becomes the first node's key. Required. */
    private String key;
    /** Hex-encoded payment key hash of the wallet that will own the NFT. Required. */
    private String ownerPubKeyHash;
    /**
     * Hex-encoded base asset name for the NFT.
     * If CIP-68 is configured, user and reference token names are derived automatically.
     * Required.
     */
    private String assetName;
    /**
     * Optional bootstrap token name for UVerify state creation.
     * Leave null to let the service pick the cheapest available bootstrap datum.
     */
    private String bootstrapTokenName;
}
