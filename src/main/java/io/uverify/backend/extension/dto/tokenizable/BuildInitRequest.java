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
 * Request body for the Init transaction endpoint.
 * <p>
 * The deployer wallet must sign the returned unsigned transaction.
 * The {@code initUtxoTxHash} + {@code initUtxoOutputIndex} pair identifies the
 * one-shot UTxO that parameterizes the tokenizable-certificate script (the
 * policy ID is derived from this pair).
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
}
