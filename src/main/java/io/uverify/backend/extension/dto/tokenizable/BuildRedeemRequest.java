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
 * Request body for the Redeem (claim) transaction endpoint.
 * <p>
 * The owner wallet must sign the returned unsigned transaction.
 * Claiming mints the NFT and sends it to the owner's address.
 */
@Data
public class BuildRedeemRequest {
    /** Bech32 address of the node owner (pays fees and must sign). */
    private String ownerAddress;
    /** Hex-encoded node key to redeem. */
    private String key;
    /** Tx-hash used to derive the tokenizable-certificate policy ID. */
    private String initUtxoTxHash;
    /** Output index used to derive the tokenizable-certificate policy ID. */
    private int initUtxoOutputIndex;
}
