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

package io.uverify.backend.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.dto.BuildStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class PrepareCollateralService {

    @Autowired
    private final CardanoBlockchainService cardanoBlockchainService;

    public BuildTransactionResponse prepareCollateral(String senderAddress) {
        try {
            return cardanoBlockchainService.buildPrepareCollateralTx(senderAddress);
        } catch (Exception e) {
            log.error("Error building prepare-collateral transaction: {}", e.getMessage(), e);
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.UNKNOWN_ERROR)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
