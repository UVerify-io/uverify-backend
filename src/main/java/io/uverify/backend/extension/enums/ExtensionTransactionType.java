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

package io.uverify.backend.extension.enums;

/**
 * Discriminates the two high-level operations exposed by extension {@code /build} endpoints.
 *
 * <ul>
 *   <li>{@link #CREATE} — creates or extends the on-chain linked list (Init or Insert,
 *       resolved automatically based on chain state).</li>
 *   <li>{@link #REDEEM} — redeems/claims tokens or NFTs from an existing node.</li>
 * </ul>
 */
public enum ExtensionTransactionType {
    CREATE,
    REDEEM
}
