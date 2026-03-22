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

import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.validators.fractionized.FractionizedConfig;
import lombok.Data;

import java.util.List;

/**
 * Unified request body for the fractionized-certificate {@code /build} endpoint.
 *
 * <p>The {@code type} field selects the operation:
 * <ul>
 *   <li>{@code CREATE} — the service checks on-chain state and automatically decides
 *       whether to <em>Init</em> (no HEAD node found) or <em>Insert</em> (HEAD exists).
 *       All fields relevant to both Init and Insert should be populated.</li>
 *   <li>{@code REDEEM} — builds a Claim transaction that mints {@code amount} fungible
 *       tokens from an available node and sends them to the sender.</li>
 * </ul>
 */
@Data
public class FractionizedBuildRequest {

    /** Operation type: {@code CREATE} (init or insert) or {@code REDEEM} (claim). */
    private ExtensionTransactionType type;

    /** Bech32 address of the sender — deployer for Init, inserter for Insert, claimer for Redeem. */
    private String senderAddress;

    /** Tx-hash used to derive the fractionized-certificate policy ID. */
    private String initUtxoTxHash;

    /** Output index used to derive the fractionized-certificate policy ID. */
    private int initUtxoOutputIndex;

    // ── CREATE / Init fields ──────────────────────────────────────────────────

    /**
     * Configuration embedded in the HEAD datum.
     * Required when {@code type == CREATE} and no HEAD node exists yet (Init path).
     */
    private FractionizedConfig config;

    // ── CREATE / Insert fields ────────────────────────────────────────────────

    /** Hex-encoded certificate hash that becomes the node key. Required for Insert. */
    private String key;

    /** Total number of fungible tokens available for claiming. Required for Insert. */
    private long totalAmount;

    /**
     * Hex-encoded payment key hashes allowed to claim tokens.
     * An empty list means the certificate is open — any wallet may claim.
     */
    private List<String> claimants;

    /** Hex-encoded asset name for the fungible token minted on each claim. Required for Insert. */
    private String assetName;

    /**
     * Optional bootstrap token name for the UVerify state update.
     * Leave null to let the service pick the cheapest available state.
     */
    private String bootstrapTokenName;

    // ── REDEEM / Claim fields ─────────────────────────────────────────────────

    /** Number of tokens to claim. Required when {@code type == REDEEM}. */
    private long amount;
}
