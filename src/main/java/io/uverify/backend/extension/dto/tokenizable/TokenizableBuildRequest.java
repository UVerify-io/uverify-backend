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

import io.uverify.backend.dto.CertificateData;
import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.validators.tokenizable.TokenizableConfig;
import lombok.Data;

/**
 * Unified request body for the tokenizable-certificate {@code /build} endpoint.
 *
 * <p>The {@code type} field selects the operation:
 * <ul>
 *   <li>{@code CREATE} — the service checks on-chain state and automatically decides
 *       whether to <em>Init</em> (no HEAD node found) or <em>Insert</em> (HEAD exists).
 *       All fields relevant to both Init and Insert should be populated.</li>
 *   <li>{@code REDEEM} — builds a Redeem transaction that claims the NFT for an
 *       available node and sends it to the sender's address.</li>
 * </ul>
 */
@Data
public class TokenizableBuildRequest {

    /** Operation type: {@code CREATE} (init or insert) or {@code REDEEM} (claim NFT). */
    private ExtensionTransactionType type;

    /** Bech32 address of the sender — deployer for Init, inserter for Insert, owner for Redeem. */
    private String senderAddress;

    /** Tx-hash used to derive the tokenizable-certificate policy ID. */
    private String initUtxoTxHash;

    /** Output index used to derive the tokenizable-certificate policy ID. */
    private int initUtxoOutputIndex;

    // ── CREATE / Init fields ──────────────────────────────────────────────────

    /**
     * Configuration embedded in the HEAD datum.
     * Required when {@code type == CREATE} and no HEAD node exists yet (Init path).
     */
    private TokenizableConfig config;

    // ── CREATE / Insert fields ────────────────────────────────────────────────

    /**
     * Certificate to register. The {@code hash} field becomes the node key;
     * the {@code metadata} field (JSON string) is stored on-chain alongside
     * the backend-generated fields (template ID, policy ID, init UTxO coords).
     * Required for CREATE.
     */
    private CertificateData certificate;

    /**
     * Hex-encoded payment key hash of the wallet that will own the NFT.
     * Required for Insert. If {@code ownerAddress} is provided instead, the backend
     * will derive the key hash automatically.
     */
    private String ownerPubKeyHash;

    /**
     * Bech32 address of the wallet that will own the NFT.
     * Alternative to {@code ownerPubKeyHash} — the backend extracts the payment key hash.
     */
    private String ownerAddress;

    /**
     * Hex-encoded base asset name for the NFT.
     * If CIP-68 is configured, user and reference token names are derived automatically.
     * Required for Insert.
     */
    private String assetName;

    /**
     * Optional bootstrap token name for the UVerify state update.
     * Leave null to let the service pick the cheapest available state.
     */
    private String bootstrapTokenName;
}
