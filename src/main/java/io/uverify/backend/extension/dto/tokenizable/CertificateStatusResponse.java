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

import lombok.Builder;
import lombok.Data;

/**
 * Status of a tokenizable certificate node identified by its {@code key}.
 */
@Data
@Builder
public class CertificateStatusResponse {
    private String key;
    /** {@code true} if the node exists in the on-chain linked list. */
    private boolean exists;
    /** {@code true} if the NFT has already been claimed (status == Redeemed). */
    private boolean claimed;
    /** Hex-encoded payment key hash of the node owner; {@code null} if not found. */
    private String ownerPubKeyHash;
    /** Hex-encoded base asset name; {@code null} if not found. */
    private String assetName;
    /** Hex-encoded key of the successor node; {@code null} for the last node. */
    private String next;
}
