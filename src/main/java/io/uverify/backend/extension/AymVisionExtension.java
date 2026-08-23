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
import io.uverify.backend.dto.UsageStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymVisionExtension implements UVerifyServiceExtension {

    public AymVisionExtension(ExtensionManager extensionManager) {
        extensionManager.registerExtension(this);
        log.info("AYM Vision extension registered");
    }

    @Override
    public String getName() {
        return "aym-vision";
    }

    @Override
    public List<AddressUtxo> processAddressUtxos(List<AddressUtxo> addressUtxos) {
        return List.of();
    }

    @Override
    public void handleRollbackToSlot(long slot) {
        // AYM Vision does not store chain-derived state
    }

    @Override
    public void addUsageStatistics(UsageStatistics usageStatistics) {
        // No chain-derived statistics for AYM Vision
    }

    @Override
    public BigInteger addTransactionFees(BigInteger totalFees) {
        return BigInteger.ZERO;
    }
}
