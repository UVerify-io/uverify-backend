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

package io.uverify.backend.extension;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExtensionManager {

    private final List<UVerifyServiceExtension> extensions = new ArrayList<>();

    public void registerExtension(UVerifyServiceExtension extension) {
        extensions.add(extension);
    }

    public void processAddressUtxos(List<AddressUtxo> addressUtxos) {
        extensions.forEach(extension -> extension.processAddressUtxos(addressUtxos));
    }

    public void handleRollbackToSlot(long slot) {
        extensions.forEach(extension -> extension.handleRollbackToSlot(slot));
    }
}
