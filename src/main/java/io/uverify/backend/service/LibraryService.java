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

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.dto.BuildStatus;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.LibraryDeploymentResponse;
import io.uverify.backend.dto.LibraryEntry;
import io.uverify.backend.entity.LibraryEntity;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.enums.TransactionType;
import io.uverify.backend.model.ProxyDatum;
import io.uverify.backend.repository.LibraryRepository;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.uverify.backend.util.ValidatorUtils.*;

@Service
@Slf4j
public class LibraryService {

    @Autowired
    private final ValidatorHelper validatorHelper;
    private final Address serviceUserAddress;
    private final String libraryContractAddress;
    private final PlutusScript libraryContract;
    @Autowired
    private final LibraryRepository libraryRepository;
    private final Network network;
    private Utxo proxyLibraryUtxo;
    private Utxo stateLibraryUtxo;
    private BackendService backendService;

    @Autowired
    public LibraryService(@Value("${cardano.service.user.address}") String serviceUserAddress,
                          @Value("${cardano.backend.service.type}") String cardanoBackendServiceType,
                          @Value("${cardano.backend.blockfrost.baseUrl}") String blockfrostBaseUrl,
                          @Value("${cardano.backend.blockfrost.projectId}") String blockfrostProjectId,
                          @Value("${cardano.network}") String network,
                          ValidatorHelper validatorHelper,
                          LibraryRepository libraryRepository
    ) {
        this.serviceUserAddress = new Address(serviceUserAddress);
        this.validatorHelper = validatorHelper;
        this.libraryRepository = libraryRepository;
        this.network = CardanoNetwork.valueOf(network).toCardaoNetwork();

        if (cardanoBackendServiceType.equals("blockfrost")) {
            if (blockfrostProjectId == null || blockfrostProjectId.isEmpty()) {
                throw new IllegalArgumentException("Blockfrost projectId is required when using Blockfrost backend service");
            }

            this.backendService = new BFBackendService(
                    blockfrostBaseUrl,
                    blockfrostProjectId);
        } else if (cardanoBackendServiceType.equals("koios")) {
            this.backendService = new KoiosBackendService(Constants.KOIOS_PREPROD_URL);
        }

        Optional<byte[]> optionalUserPaymentCredential = this.serviceUserAddress.getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        this.libraryContract = getLibraryContract(optionalUserPaymentCredential.get());
        this.libraryContractAddress = AddressProvider.getEntAddress(libraryContract, this.network).toBech32();
        reloadLibraryCache();
    }

    public void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    public Utxo getProxyLibraryUtxo() {
        if (proxyLibraryUtxo == null) {
            reloadLibraryCache();
        }
        return proxyLibraryUtxo;
    }

    public Utxo getStateLibraryUtxo() {
        if (stateLibraryUtxo == null) {
            reloadLibraryCache();
        }
        return stateLibraryUtxo;
    }

    public Transaction deployUVerifyContracts() {
        Optional<byte[]> optionalUserPaymentCredential = serviceUserAddress.getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        String proxyTransactionHash = validatorHelper.getProxyTransactionHash();
        Integer proxyOutputIndex = validatorHelper.getProxyOutputIndex();

        PlutusScript libraryContract = getLibraryContract(optionalUserPaymentCredential.get());
        String libraryContractAddress = AddressProvider.getEntAddress(libraryContract, network).toBech32();

        PlutusScript uverifyProxyContract = getUverifyProxyContract(proxyTransactionHash, proxyOutputIndex);
        PlutusScript uverifyStateContract = getUVerifyStateContract(proxyTransactionHash, proxyOutputIndex);

        if (libraryRepository.count() > 0) {
            throw new IllegalStateException("Library contracts have already been deployed. Multiple deployments are not allowed.");
        }

        Tx tx = new Tx()
                .from(serviceUserAddress.getAddress())
                .payToContract(libraryContractAddress, Amount.ada(1L), PlutusData.unit(), uverifyProxyContract)
                .payToContract(libraryContractAddress, Amount.ada(1L), PlutusData.unit(), uverifyStateContract)
                .registerStakeAddress(AddressProvider.getRewardAddress(uverifyStateContract, network).toBech32());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        return quickTxBuilder.compose(tx)
                .feePayer(serviceUserAddress.getAddress())
                .withRequiredSigners(serviceUserAddress)
                .mergeOutputs(false)
                .build();
    }

    public BuildTransactionResponse buildDeployTransaction() {
        return buildDeployTransaction("");
    }

    public BuildTransactionResponse buildDeployTransaction(String compiledCode) {
        try {
            Transaction transaction;
            if (compiledCode != null && !compiledCode.isEmpty()) {
                transaction = upgradeProxy(compiledCode);
            } else {
                transaction = deployUVerifyContracts();
            }
            return BuildTransactionResponse.builder()
                    .unsignedTransaction(transaction.serializeToHex())
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.SUCCESS)
                            .build())
                    .type(TransactionType.DEPLOY)
                    .build();
        } catch (Exception exception) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(exception.getMessage())
                            .build())
                    .type(TransactionType.DEPLOY)
                    .build();
        }
    }

    private Transaction upgradeProxy(String compiledCode) {
        PlutusScript script = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, PlutusVersion.v3);

        Optional<byte[]> optionalUserPaymentCredential = serviceUserAddress.getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = validatorHelper.resolveProxyStateUtxo(backendService);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        PlutusScript libraryContract = getLibraryContract(optionalUserPaymentCredential.get());
        String libraryContractAddress = AddressProvider.getEntAddress(libraryContract, network).toBech32();

        Tx tx = new Tx()
                .from(serviceUserAddress.getAddress())
                .registerStakeAddress(AddressProvider.getRewardAddress(script, network).toBech32())
                .payToAddress(serviceUserAddress.getAddress(), Amount.ada(1L));

        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, network).toBech32();

        ProxyDatum proxyDatum = ProxyDatum.builder()
                .ScriptOwner(Hex.encodeHexString(optionalUserPaymentCredential.get()))
                .ScriptPointer(validatorToScriptHash(script))
                .build();

        ScriptTx scriptTx = new ScriptTx()
                .readFrom(proxyLibraryUtxo)
                .payToContract(libraryContractAddress, Amount.ada(1L), PlutusData.unit(), script)
                .collectFrom(proxyStateUtxo, PlutusData.unit())
                .payToContract(proxyScriptAddress, proxyStateUtxo.getAmount(), proxyDatum.toPlutusData());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        return quickTxBuilder.compose(tx, scriptTx)
                .feePayer(serviceUserAddress.getAddress())
                .withRequiredSigners(serviceUserAddress)
                .withReferenceScripts(proxyContract)
                .build();
    }

    private void reloadLibraryCache() {
        Optional<LibraryEntity> proxyEntry = libraryRepository.findById(0L);
        Optional<LibraryEntity> stateEntry = libraryRepository.getLatestScript();

        if (proxyEntry.isPresent() && stateEntry.isPresent()) {
            try {
                this.proxyLibraryUtxo = resolveUtxo(proxyEntry.get().getTransactionId(), proxyEntry.get().getOutputIndex());
                this.stateLibraryUtxo = resolveUtxo(stateEntry.get().getTransactionId(), stateEntry.get().getOutputIndex());
            } catch (Exception e) {
                log.error("Failed to reload library cache: {}", e.getMessage());
            }
        } else {
            log.error("Failed to reload library cache: Missing library entries in the database.");
        }
    }

    private Utxo resolveUtxo(String transactionId, Integer outputIndex) {
        try {
            Result<Utxo> utxoResult = backendService.getUtxoService().getTxOutput(transactionId, outputIndex);
            if (utxoResult.isSuccessful()) {
                return utxoResult.getValue();
            } else {
                log.error("Failed to fetch UTXO: {}", utxoResult.getResponse());
                return null;
            }
        } catch (ApiException e) {
            log.error("Failed to fetch UTXO: {}", e.getMessage());
            return null;
        }
    }

    private List<LibraryEntry> getLibraryEntries() {
        List<Utxo> utxos = new ArrayList<>();
        try {
            Result<List<Utxo>> utxoResult = backendService.getUtxoService().getUtxos(libraryContractAddress, 100, 1);
            if (utxoResult.isSuccessful() && utxoResult.getValue() != null) {
                utxos = utxoResult.getValue();
            }
        } catch (ApiException e) {
            log.error("Error fetching UTXOs for library contract address {}: {}", libraryContractAddress, e.getMessage());
        }

        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(validatorHelper.getParameterizedProxyContract());
        String stateScriptHash = ValidatorUtils.validatorToScriptHash(validatorHelper.getParameterizedUVerifyStateContract());

        ArrayList<LibraryEntry> entries = new ArrayList<>();
        for (Utxo utxo : utxos) {
            boolean isActiveContract = utxo.getReferenceScriptHash() != null && (utxo.getReferenceScriptHash().equals(proxyScriptHash) || utxo.getReferenceScriptHash().equals(stateScriptHash));
            entries.add(LibraryEntry.builder()
                    .transactionHash(utxo.getTxHash())
                    .outputIndex(utxo.getOutputIndex())
                    .isActiveContract(isActiveContract)
                    .build());
        }
        return entries;
    }

    public LibraryDeploymentResponse getDeployments() {
        List<LibraryEntry> entries = getLibraryEntries();

        return LibraryDeploymentResponse.builder()
                .address(libraryContractAddress)
                .entries(entries)
                .build();
    }

    public BuildTransactionResponse undeployUnusedContracts() {
        List<LibraryEntry> entries = getLibraryEntries();

        if (entries.isEmpty()) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(
                                    "No contracts found in library"
                            ).build())
                    .build();
        }

        List<LibraryEntry> entriesToUndeploy = entries.stream()
                .filter(entry -> !entry.getIsActiveContract())
                .toList();

        if (entriesToUndeploy.isEmpty()) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.SUCCESS)
                            .message(
                                    "No unused contracts to undeploy"
                            ).build())
                    .build();
        } else {
            ArrayList<Utxo> utxosToUndeploy = new ArrayList<>();
            for (LibraryEntry entry : entriesToUndeploy) {
                try {
                    Result<Utxo> utxoResult = backendService.getUtxoService().getTxOutput(entry.getTransactionHash(), entry.getOutputIndex());
                    if (utxoResult.isSuccessful() && utxoResult.getValue() != null) {
                        utxosToUndeploy.add(utxoResult.getValue());
                    } else {
                        log.error("Failed to fetch UTXO for transaction hash {} and output index {}", entry.getTransactionHash(), entry.getOutputIndex());
                    }
                } catch (ApiException e) {
                    log.error("Error fetching UTXO for transaction hash {} and output index {}: {}", entry.getTransactionHash(), entry.getOutputIndex(), e.getMessage());
                }
            }
            ScriptTx tx = new ScriptTx()
                    .collectFrom(utxosToUndeploy, PlutusData.unit())
                    .attachSpendingValidator(libraryContract)
                    .payToAddress(serviceUserAddress.getAddress(),
                            utxosToUndeploy.stream()
                                    .map(Utxo::getAmount)
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toList()));

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

            Transaction unsignedTx = quickTxBuilder.compose(tx)
                    .feePayer(serviceUserAddress.getAddress())
                    .withRequiredSigners(serviceUserAddress)
                    .build();

            String serializedTx = "";
            try {
                serializedTx = HexUtil.encodeHexString(unsignedTx.serialize());
            } catch (CborSerializationException e) {
                log.error("Error serializing transaction: {}", e.getMessage());
            }

            if (serializedTx.equals("")) {
                return BuildTransactionResponse.builder()
                        .status(BuildStatus.builder()
                                .code(BuildStatusCode.ERROR)
                                .message(
                                        "Failed to serialize transaction"
                                ).build())
                        .build();
            }

            return BuildTransactionResponse.builder()
                    .unsignedTransaction(serializedTx)
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.SUCCESS)
                            .build()
                    ).build();
        }
    }

    public BuildTransactionResponse undeployContract(String transactionHash, Integer outputIndex) {
        List<LibraryEntry> entries = getLibraryEntries();
        LibraryEntry libraryEntry = entries.stream().filter(entry -> entry.getTransactionHash().equals(transactionHash) && entry.getOutputIndex().equals(outputIndex)).findFirst().orElse(null);
        if (libraryEntry == null || libraryEntry.getIsActiveContract()) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(
                                    "The library entry is either not found or is an active contract and cannot be undeployed"
                            ).build())
                    .build();
        }

        Utxo utxoToUndeploy = null;
        try {
            Result<Utxo> utxoResult = backendService.getUtxoService().getTxOutput(transactionHash, outputIndex);
            if (utxoResult.isSuccessful() && utxoResult.getValue() != null) {
                utxoToUndeploy = utxoResult.getValue();
            } else {
                log.error("Failed to fetch UTXO for transaction hash {} and output index {}", transactionHash, outputIndex);
            }
        } catch (ApiException e) {
            log.error("Error fetching UTXO for transaction hash {} and output index {}: {}", transactionHash, outputIndex, e.getMessage());
        }

        if (utxoToUndeploy == null) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(
                                    "Failed to fetch UTXO for the library entry to undeploy"
                            ).build())
                    .build();
        }

        ScriptTx tx = new ScriptTx()
                .collectFrom(utxoToUndeploy, PlutusData.unit())
                .attachSpendingValidator(libraryContract)
                .payToAddress(serviceUserAddress.getAddress(),
                        utxoToUndeploy.getAmount());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Transaction unsignedTx = quickTxBuilder.compose(tx)
                .feePayer(serviceUserAddress.getAddress())
                .withRequiredSigners(serviceUserAddress)
                .build();

        String serializedTx = "";
        try {
            serializedTx = HexUtil.encodeHexString(unsignedTx.serialize());
        } catch (CborSerializationException e) {
            log.error("Error serializing transaction: {}", e.getMessage());
        }

        if (serializedTx.equals("")) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(
                                    "Failed to serialize transaction"
                            ).build())
                    .build();
        } else {
            return BuildTransactionResponse.builder()
                    .unsignedTransaction(serializedTx)
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.SUCCESS)
                            .build()
                    ).build();
        }
    }

    private boolean hasProxyScriptRef(com.bloxbean.cardano.yaci.helper.model.Utxo utxo) {
        String compiledCode = utxo.getScriptRef();
        PlutusScript script = PlutusScript.deserializeScriptRef(HexUtil.decodeHexString(compiledCode));

        try {
            return validatorHelper.getParameterizedProxyContract().getPolicyId().equals(script.getPolicyId());
        } catch (Exception e) {
            log.error("Failed to get policy id from scripts: {}", e.getMessage());
            return false;
        }
    }

    private boolean isFirstDeployment() {
        return libraryRepository.count() == 0;
    }

    private void deployUtxoScript(com.bloxbean.cardano.yaci.helper.model.Utxo utxo, Long slot) {
        try {
            String compiledCode = utxo.getScriptRef();
            PlutusScript script = PlutusScript.deserializeScriptRef(HexUtil.decodeHexString(compiledCode));
            LibraryEntity libraryEntity = LibraryEntity.builder()
                    .slot(slot)
                    .transactionId(utxo.getTxHash())
                    .outputIndex(utxo.getIndex())
                    .compiledCode(script.getCborHex())
                    .hash(script.getPolicyId()).build();
            libraryRepository.save(libraryEntity);
        } catch (Exception e) {
            log.error("Potential script deployment skipped. Failed to deserialize inline datum for library UTXO: {}", e.getMessage());
        }
    }

    private void deployAllUtxoScripts(List<com.bloxbean.cardano.yaci.helper.model.Utxo> utxos, Long slot) {
        for (com.bloxbean.cardano.yaci.helper.model.Utxo utxo : utxos) {
            deployUtxoScript(utxo, slot);
        }
    }

    public void deployToLibrary(ArrayList<com.bloxbean.cardano.yaci.helper.model.Utxo> utxos, Long slot) {
        if (isFirstDeployment()) {
            boolean proxyScriptInUtxos = utxos.stream().anyMatch(this::hasProxyScriptRef);
            if (!proxyScriptInUtxos) {
                log.error("No proxy script found in the provided UTXOs for the first deployment. Deployment skipped.");
            } else {
                Optional<com.bloxbean.cardano.yaci.helper.model.Utxo> proxyUtxo = utxos.stream().filter(this::hasProxyScriptRef).findFirst();
                proxyUtxo.ifPresent(utxos::remove);

                if (proxyUtxo.isPresent()) {
                    deployUtxoScript(proxyUtxo.get(), slot);
                } else {
                    log.error("Failed to find the proxy script UTXO for the first deployment. Deployment skipped.");
                    return;
                }

                deployAllUtxoScripts(utxos, slot);
            }
        } else {
            deployAllUtxoScripts(utxos, slot);
        }
    }

    public String getLibraryAddress() {
        return libraryContractAddress;
    }
}
