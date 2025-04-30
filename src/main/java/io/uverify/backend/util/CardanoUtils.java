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

package io.uverify.backend.util;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import io.uverify.backend.enums.CardanoNetwork;

import java.util.Optional;

public class CardanoUtils {

    public static byte[] extractCredentialFromAddress(String address) {
        Optional<byte[]> optionalUserPaymentCredential = new Address(address).getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        return optionalUserPaymentCredential.get();
    }

    public static Long getLatestSlot(BackendService backendService) throws ApiException {
        Result<Block> latestBlock = backendService.getBlockService().getLatestBlock();
        if (latestBlock.isSuccessful()) {
            return latestBlock.getValue().getSlot();
        } else {
            throw new ApiException("Failed to get latest block: " + latestBlock.getResponse());
        }
    }

    public static Long getLatestBlockNumber(BackendService backendService) throws ApiException {
        Result<Block> latestBlock = backendService.getBlockService().getLatestBlock();
        if (latestBlock.isSuccessful()) {
            return latestBlock.getValue().getHeight();
        } else {
            throw new ApiException("Failed to get latest block: " + latestBlock.getResponse());
        }
    }

    public static String getLatestBlockHash(BackendService backendService) throws ApiException {
        Result<Block> latestBlock = backendService.getBlockService().getLatestBlock();
        if (latestBlock.isSuccessful()) {
            return latestBlock.getValue().getHash();
        } else {
            throw new ApiException("Failed to get latest block: " + latestBlock.getResponse());
        }
    }

    public static Network fromCardanoNetwork(CardanoNetwork cardanoNetwork) {
        return switch (cardanoNetwork) {
            case MAINNET -> Networks.mainnet();
            case PREPROD, CUSTOM -> Networks.preprod();
            case PREVIEW -> Networks.preview();
        };
    }
}
