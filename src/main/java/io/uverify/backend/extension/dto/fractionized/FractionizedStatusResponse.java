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

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Status of a fractionized certificate node identified by its {@code key}.
 */
@Data
@Builder
public class FractionizedStatusResponse {
    private String key;
    /** {@code true} if the node exists in the on-chain linked list. */
    private boolean exists;
    /** Total number of tokens available when the node was inserted. */
    private long totalAmount;
    /** Remaining tokens that can still be claimed. */
    private long remainingAmount;
    /** {@code true} when remainingAmount has reached zero. */
    private boolean exhausted;
    /** Hex-encoded payment key hashes allowed to claim; empty means open access. */
    private List<String> claimants;
    /** Hex-encoded asset name of the fungible token. */
    private String assetName;
    /** Hex-encoded key of the successor node; {@code null} for the last node. */
    private String next;
}
