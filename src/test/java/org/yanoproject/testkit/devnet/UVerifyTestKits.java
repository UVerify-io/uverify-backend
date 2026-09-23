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

package org.yanoproject.testkit.devnet;

import org.yanoproject.runtime.assembly.Yano;

/**
 * Lives in the testkit package on purpose. {@link YanoDevnetTestKit#from(Yano)} is
 * package-private and the public factories never wire transaction services, so a
 * node assembled with Plutus evaluation can only be wrapped from inside this package.
 */
public final class UVerifyTestKits {

    private UVerifyTestKits() {
    }

    public static YanoDevnetTestKit wrap(Yano node) {
        return YanoDevnetTestKit.from(node);
    }
}
