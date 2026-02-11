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

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import io.uverify.backend.enums.CardanoNetwork;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.uverify.backend.util.ValidatorUtils.getProxyStateTokenName;

@Component
@Getter
@Slf4j
public class ValidatorHelper {
    private final CardanoNetwork network;
    private String proxyTransactionHash;
    private Integer proxyOutputIndex;
    private String libraryTransactionHash;
    private Integer proxyLibraryOutputIndex;
    private Integer stateLibraryOutputIndex;

    public ValidatorHelper(@Value("${proxy.transaction-hash}") String proxyTransactionHash,
                           @Value("${proxy.output-index}") Integer proxyOutputIndex,
                           @Value("${library.transaction-hash}") String libraryTransactionHash,
                           @Value("${library.proxy-output-index}") Integer proxyLibraryOutputIndex,
                           @Value("${library.state-output-index}") Integer stateLibraryOutputIndex,
                           @Value("${cardano.network}") String network) {
        this.proxyTransactionHash = proxyTransactionHash;
        this.libraryTransactionHash = libraryTransactionHash;
        this.proxyLibraryOutputIndex = proxyLibraryOutputIndex;
        this.stateLibraryOutputIndex = stateLibraryOutputIndex;
        this.proxyOutputIndex = proxyOutputIndex;
        this.network = CardanoNetwork.valueOf(network);
    }

    public PlutusScript getParameterizedUVerifyStateContract() {
        return ValidatorUtils.getUVerifyStateContract(proxyTransactionHash, proxyOutputIndex);
    }

    public PlutusScript getParameterizedProxyContract() {
        return ValidatorUtils.getUverifyProxyContract(proxyTransactionHash, proxyOutputIndex);
    }

    public void setProxy(String proxyTransactionHash, Integer proxyOutputIndex) {
        this.proxyTransactionHash = proxyTransactionHash;
        this.proxyOutputIndex = proxyOutputIndex;
    }

    public void setLibrary(String libraryTransactionHash, Integer proxyLibraryOutputIndex, Integer stateLibraryOutputIndex) {
        this.libraryTransactionHash = libraryTransactionHash;
        this.proxyLibraryOutputIndex = proxyLibraryOutputIndex;
        this.stateLibraryOutputIndex = stateLibraryOutputIndex;
    }

    public String getProxyContractAddress() {
        PlutusScript uverifyProxyContract = ValidatorUtils.getUverifyProxyContract(proxyTransactionHash, proxyOutputIndex);

        if (network.equals(CardanoNetwork.MAINNET)) {
            return AddressProvider.getEntAddress(uverifyProxyContract, Networks.mainnet()).toBech32();
        }

        return AddressProvider.getEntAddress(uverifyProxyContract, Networks.preprod()).toBech32();
    }

    public String getStateContractAddress() {
        PlutusScript stateContract = getParameterizedUVerifyStateContract();

        if (network.equals(CardanoNetwork.MAINNET)) {
            return AddressProvider.getRewardAddress(stateContract, Networks.mainnet()).toBech32();
        }

        return AddressProvider.getRewardAddress(stateContract, Networks.preprod()).toBech32();
    }

    public Utxo resolveProxyStateUtxo(BackendService backendService) throws ApiException {
        String stateTokenName = getProxyStateTokenName(proxyTransactionHash, proxyOutputIndex);
        PlutusScript proxyContract = ValidatorUtils.getUverifyProxyContract(proxyTransactionHash, proxyOutputIndex);

        String proxyScriptAddress;
        if (network.equals(CardanoNetwork.MAINNET)) {
            proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, Networks.mainnet()).toBech32();
        } else {
            proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, Networks.preprod()).toBech32();
        }

        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(proxyContract);
        String stateTokenUnit = proxyScriptHash + stateTokenName;

        Result<List<Utxo>> stateUtxRequest = backendService.getUtxoService().getUtxos(proxyScriptAddress, stateTokenUnit, 1, 1);
        return stateUtxRequest.getValue().get(0);
    }

    public Utxo resolveProxyLibraryUtxo(BackendService backendService) {
        try {
            Result<Utxo> utxoResult = backendService.getUtxoService().getTxOutput(libraryTransactionHash, proxyLibraryOutputIndex);
            if (utxoResult.isSuccessful()) {
                return utxoResult.getValue();
            } else {
                log.error("Failed to fetch proxy reference input UTXO: {}", utxoResult.getResponse());
                return null;
            }
        } catch (ApiException e) {
            log.error("Failed to fetch proxy reference input UTXO: {}", e.getMessage());
            return null;
        }
    }

    public Utxo resolveStateLibraryUtxo(BackendService backendService) {
        try {
            Result<Utxo> utxoResult = backendService.getUtxoService().getTxOutput(libraryTransactionHash, stateLibraryOutputIndex);
            if (utxoResult.isSuccessful()) {
                return utxoResult.getValue();
            } else {
                log.error("Failed to fetch state reference input UTXO: {}", utxoResult.getResponse());
                return null;
            }
        } catch (ApiException e) {
            log.error("Failed to fetch state reference input UTXO: {}", e.getMessage());
            return null;
        }
    }
}
