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

public enum UVerifyScriptPurpose {
    MINT_BOOTSTRAP("mint_bootstrap"),
    BURN_BOOTSTRAP("burn_bootstrap"),
    MINT_STATE("mint_state"),
    RENEW_STATE("renew_state"),
    BURN_STATE("burn_state"),
    UPDATE_STATE("update_state");

    private final String value;

    UVerifyScriptPurpose(String value) {
        this.value = value;
    }

    public static UVerifyScriptPurpose fromValue(String value) {
        for (UVerifyScriptPurpose type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid UVerifyScriptPurpose type: " + value);
    }

    public String getValue() {
        return value;
    }
}
