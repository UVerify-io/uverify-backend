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
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import io.uverify.backend.entity.LibraryEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.repository.LibraryRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static io.uverify.backend.util.ValidatorUtils.getProxyStateTokenName;

@Component
@Getter
@Slf4j
public class ValidatorHelper {
    private final CardanoNetwork network;
    private final LibraryRepository libraryRepository;
    private String proxyTransactionHash;
    private Integer proxyOutputIndex;

    @Autowired
    public ValidatorHelper(@Value("${proxy.transaction-hash}") String proxyTransactionHash,
                           @Value("${proxy.output-index}") Integer proxyOutputIndex,
                           @Value("${cardano.network}") String network,
                           LibraryRepository libraryRepository) {
        this.proxyTransactionHash = proxyTransactionHash;
        this.proxyOutputIndex = proxyOutputIndex;
        this.network = CardanoNetwork.valueOf(network);
        this.libraryRepository = libraryRepository;
    }

    /**
     * Resolves the state contract for new transactions from the latest script
     * deployed to the on-chain library. The bundled contract in ValidatorUtils
     * is only the artifact for the very first deployment, before any library
     * entry exists.
     */
    public PlutusScript getParameterizedUVerifyStateContract() {
        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(getParameterizedProxyContract());
        Optional<LibraryEntity> latestScript = libraryRepository.getLatestScript(proxyScriptHash);
        if (latestScript.isPresent()) {
            return PlutusV3Script.builder().cborHex(latestScript.get().getCompiledCode()).build();
        }
        return ValidatorUtils.getUVerifyStateContract(proxyTransactionHash, proxyOutputIndex);
    }

    public PlutusScript getParameterizedProxyContract() {
        return ValidatorUtils.getUverifyProxyContract(proxyTransactionHash, proxyOutputIndex);
    }

    public void setProxy(String proxyTransactionHash, Integer proxyOutputIndex) {
        this.proxyTransactionHash = proxyTransactionHash;
        this.proxyOutputIndex = proxyOutputIndex;
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

        // Providers that filter by asset before paginating (e.g. Blockfrost)
        // return the state UTxO on the first filtered page. Providers that
        // paginate the address UTxOs before filtering (e.g. yano) can return
        // sparse or empty pages even though the state UTxO exists, with no way
        // to tell a sparse page from the end of the results. Try the cheap
        // filtered query first, then fall back to paging through all address
        // UTxOs and filtering here, where a short page reliably marks the end.
        int pageSize = 100;
        Result<List<Utxo>> filteredRequest = backendService.getUtxoService().getUtxos(proxyScriptAddress, stateTokenUnit, pageSize, 1);
        Optional<Utxo> stateUtxo = firstUtxoHoldingToken(filteredRequest.getValue(), stateTokenUnit);
        if (stateUtxo.isPresent()) {
            return stateUtxo.get();
        }

        for (int page = 1; ; page++) {
            Result<List<Utxo>> pageRequest = backendService.getUtxoService().getUtxos(proxyScriptAddress, pageSize, page);
            List<Utxo> utxos = pageRequest.getValue() == null ? List.of() : pageRequest.getValue();
            Optional<Utxo> match = firstUtxoHoldingToken(utxos, stateTokenUnit);
            if (match.isPresent()) {
                return match.get();
            }
            if (utxos.size() < pageSize) {
                throw new IllegalStateException("No proxy state UTxO found at " + proxyScriptAddress);
            }
        }
    }

    private static Optional<Utxo> firstUtxoHoldingToken(List<Utxo> utxos, String unit) {
        if (utxos == null) {
            return Optional.empty();
        }
        return utxos.stream()
                .filter(utxo -> utxo.getAmount().stream().anyMatch(amount -> unit.equals(amount.getUnit())))
                .findFirst();
    }
}
