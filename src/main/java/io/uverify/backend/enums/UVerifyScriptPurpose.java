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

package io.uverify.backend.enums;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;

public enum UVerifyScriptPurpose {
    BURN_BOOTSTRAP,
    BURN_STATE,
    UPDATE_STATE,
    MINT_STATE,
    MINT_BOOTSTRAP;

    public static UVerifyScriptPurpose fromConstr(ConstrPlutusData constr) {
        switch ((int) constr.getAlternative()) {
            case 0 -> {
                return BURN_BOOTSTRAP;
            }
            case 1 -> {
                return BURN_STATE;
            }
            case 2 -> {
                return UPDATE_STATE;
            }
            case 3 -> {
                return MINT_STATE;
            }
            case 4 -> {
                return MINT_BOOTSTRAP;
            }
            default -> throw new IllegalArgumentException("Invalid state update purpose: " + constr.getAlternative());
        }
    }

    public ConstrPlutusData toConstr() {
        return new ConstrPlutusData(switch (this) {
            case BURN_BOOTSTRAP -> 0;
            case BURN_STATE -> 1;
            case UPDATE_STATE -> 2;
            case MINT_STATE -> 3;
            case MINT_BOOTSTRAP -> 4;
        }, ListPlutusData.of());
    }
}
