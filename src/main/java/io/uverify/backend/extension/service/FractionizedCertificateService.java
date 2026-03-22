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

package io.uverify.backend.extension.service;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.enums.UVerifyScriptPurpose;
import io.uverify.backend.model.ProxyRedeemer;
import io.uverify.backend.model.converter.ProxyRedeemerConverter;
import io.uverify.backend.extension.dto.fractionized.BuildClaimRequest;
import io.uverify.backend.extension.dto.fractionized.BuildInitRequest;
import io.uverify.backend.extension.dto.fractionized.BuildInsertRequest;
import io.uverify.backend.extension.dto.fractionized.FractionizedBuildRequest;
import io.uverify.backend.extension.dto.fractionized.FractionizedStatusResponse;
import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.validators.fractionized.FractionizedConfig;
import io.uverify.backend.extension.validators.fractionized.FractionizedDatum;
import io.uverify.backend.model.StateDatum;
import io.uverify.backend.model.StateRedeemer;
import io.uverify.backend.model.UVerifyCertificate;
import io.uverify.backend.service.BootstrapDatumService;
import io.uverify.backend.service.LibraryService;
import io.uverify.backend.service.StateDatumService;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.util.CardanoUtils;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;
import static io.uverify.backend.util.ValidatorUtils.*;

/**
 * Builds unsigned Cardano transactions for the fractionized-certificate contract.
 *
 * <h3>Supported operations</h3>
 * <ul>
 *   <li><b>Init</b> – creates the HEAD node and sets the list configuration.</li>
 *   <li><b>Insert</b> – submits a UVerify certificate and mints a node token in the
 *       sorted linked list in a single atomic transaction.</li>
 *   <li><b>Claim</b> – mints {@code amount} fungible tokens from an available node
 *       to the claimer's address.</li>
 *   <li><b>Status</b> – queries the on-chain state of a node by key.</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "extensions.fractionized-certificate.enabled", havingValue = "true")
public class FractionizedCertificateService {

    private static final String NODE_PREFIX_HEX = "46524e";

    private final Network network;
    private final CardanoNetwork cardanoNetwork;
    private BackendService backendService;

    @Autowired
    private ValidatorHelper validatorHelper;
    @Autowired
    private LibraryService libraryService;
    @Autowired
    private StateDatumService stateDatumService;
    @Autowired
    private BootstrapDatumService bootstrapDatumService;

    @Autowired
    public FractionizedCertificateService(
            @Value("${cardano.network}") String network,
            @Value("${cardano.backend.service.type}") String cardanoBackendServiceType,
            @Value("${cardano.backend.blockfrost.baseUrl}") String blockfrostBaseUrl,
            @Value("${cardano.backend.blockfrost.projectId}") String blockfrostProjectId) {

        this.cardanoNetwork = CardanoNetwork.valueOf(network);
        this.network = fromCardanoNetwork(this.cardanoNetwork);

        if (cardanoBackendServiceType.equals("blockfrost")) {
            this.backendService = new BFBackendService(blockfrostBaseUrl, blockfrostProjectId);
        } else if (cardanoBackendServiceType.equals("koios")) {
            this.backendService = new KoiosBackendService(Constants.KOIOS_PREPROD_URL);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Unified entry point — routes to Init, Insert, or Claim based on {@code req.getType()}
     * and current on-chain state.
     *
     * <ul>
     *   <li>{@code CREATE} — checks whether the HEAD node exists. If not, runs Init using
     *       {@code req.getConfig()}; otherwise runs Insert.</li>
     *   <li>{@code REDEEM} — runs Claim for the given key and amount.</li>
     * </ul>
     */
    public String buildTransaction(FractionizedBuildRequest req) throws ApiException, CborSerializationException {
        if (req.getType() == ExtensionTransactionType.REDEEM) {
            BuildClaimRequest claim = new BuildClaimRequest();
            claim.setClaimerAddress(req.getSenderAddress());
            claim.setKey(req.getKey());
            claim.setAmount(req.getAmount());
            claim.setInitUtxoTxHash(req.getInitUtxoTxHash());
            claim.setInitUtxoOutputIndex(req.getInitUtxoOutputIndex());
            return buildClaimTransaction(claim);
        }

        // CREATE — decide Init vs Insert by checking whether HEAD already exists
        PlutusScript script = getFractionizedCertificateContract(req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String policyId = validatorToScriptHash(script);
        String scriptAddress = scriptAddress(script);
        String headUnit = policyId + NODE_PREFIX_HEX;
        boolean headExists = getCurrentUtxoByUnit(scriptAddress, headUnit, backendService).isPresent();

        if (!headExists) {
            BuildInitRequest init = new BuildInitRequest();
            init.setDeployerAddress(req.getSenderAddress());
            init.setInitUtxoTxHash(req.getInitUtxoTxHash());
            init.setInitUtxoOutputIndex(req.getInitUtxoOutputIndex());
            init.setConfig(req.getConfig());
            return buildInitTransaction(init);
        } else {
            BuildInsertRequest insert = new BuildInsertRequest();
            insert.setInserterAddress(req.getSenderAddress());
            insert.setKey(req.getKey());
            insert.setTotalAmount(req.getTotalAmount());
            insert.setClaimants(req.getClaimants());
            insert.setAssetName(req.getAssetName());
            insert.setInitUtxoTxHash(req.getInitUtxoTxHash());
            insert.setInitUtxoOutputIndex(req.getInitUtxoOutputIndex());
            insert.setBootstrapTokenName(req.getBootstrapTokenName());
            return buildInsertTransaction(insert);
        }
    }

    public String buildInitTransaction(BuildInitRequest req) throws ApiException, CborSerializationException {
        PlutusScript script = getFractionizedCertificateContract(req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String scriptAddress = scriptAddress(script);

        Result<List<Utxo>> utxoResult = backendService.getUtxoService().getUtxos(req.getDeployerAddress(), 100, 1);
        if (!utxoResult.isSuccessful() || utxoResult.getValue() == null) {
            throw new IllegalStateException("Could not retrieve UTxOs for deployer address");
        }
        Optional<Utxo> optInitUtxo = utxoResult.getValue().stream()
                .filter(u -> u.getTxHash().equals(req.getInitUtxoTxHash())
                        && u.getOutputIndex() == req.getInitUtxoOutputIndex())
                .findFirst();
        if (optInitUtxo.isEmpty()) {
            throw new IllegalArgumentException("Init UTxO " + req.getInitUtxoTxHash() + "#"
                    + req.getInitUtxoOutputIndex() + " not found in deployer address");
        }
        Utxo initUtxo = optInitUtxo.get();

        FractionizedDatum.FHead headDatum = FractionizedDatum.FHead.builder()
                .next(null)
                .config(req.getConfig())
                .build();

        Asset headToken = Asset.builder()
                .name("0x" + NODE_PREFIX_HEX)
                .value(BigInteger.ONE)
                .build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(List.of(initUtxo))
                .mintAsset(script, List.of(headToken), ConstrPlutusData.of(0),
                        scriptAddress, headDatum.toPlutusData());

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(req.getDeployerAddress())
                .collateralPayer(req.getDeployerAddress())
                .withRequiredSigners(new Address(req.getDeployerAddress()))
                .validFrom(currentSlot - 10)
                .validTo(currentSlot + 600)
                .build();

        return unsignedTx.serializeToHex();
    }

    public String buildInsertTransaction(BuildInsertRequest req) throws ApiException, CborSerializationException {
        PlutusScript fractionizedScript = getFractionizedCertificateContract(
                req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String policyId = validatorToScriptHash(fractionizedScript);
        String fractionizedAddress = scriptAddress(fractionizedScript);

        Utxo headUtxo = fetchUtxoByToken(fractionizedAddress, policyId, NODE_PREFIX_HEX);
        FractionizedDatum.FHead headDatum = (FractionizedDatum.FHead) FractionizedDatum.fromInlineDatum(headUtxo.getInlineDatum());

        PredecessorResult pred = findPredecessor(fractionizedAddress, policyId, req.getKey(), headDatum);
        if (pred.isHeadPredecessor()) {
            String bootstrapTokenForFork = req.getBootstrapTokenName() != null ? req.getBootstrapTokenName() : "";
            Optional<StateDatumEntity> existingState = resolveStateDatumOptional(req.getInserterAddress(), bootstrapTokenForFork);
            if (existingState.isPresent()) {
                throw new IllegalStateException(
                        "Cannot insert key '" + req.getKey() + "': the HEAD node would be the predecessor. "
                        + "This is impossible due to the Cardano EUTXO constraint.");
            }
            return buildForkAndOrphanInsertTransaction(req, fractionizedScript, fractionizedAddress, headUtxo);
        }

        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        String proxyScriptAddress = scriptAddress(proxyContract);
        String stateRewardAddress = AddressProvider.getRewardAddress(stateContract, network).toBech32();
        String proxyScriptHash = validatorToScriptHash(proxyContract);

        String bootstrapToken = req.getBootstrapTokenName() != null ? req.getBootstrapTokenName() : "";
        StateDatumEntity stateDatumEntity = resolveStateDatum(req.getInserterAddress(), bootstrapToken);
        String unit = proxyScriptHash + stateDatumEntity.getId();
        Optional<Utxo> optProxyUtxo = getCurrentUtxoByUnit(proxyScriptAddress, unit, backendService);
        if (optProxyUtxo.isEmpty()) {
            throw new IllegalStateException("Proxy state UTxO not found for inserter address");
        }
        Utxo proxyUtxo = optProxyUtxo.get();

        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer("")
                .extra("{\"uverify_template_id\":\"fractionizedCertificate\"}")
                .build();

        StateDatum nextStateDatum = StateDatum.fromPreviousStateDatum(proxyUtxo.getInlineDatum());
        nextStateDatum.setCertificates(List.of(cert));

        StateRedeemer stateRedeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.UPDATE_STATE)
                .certificates(List.of(cert))
                .build();

        PlutusData proxySpendRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        Utxo proxyStateRef = validatorHelper.resolveProxyStateUtxo(backendService);
        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();
        Utxo proxyLibraryUtxo = libraryService.getProxyLibraryUtxo();

        String nodeTokenName = NODE_PREFIX_HEX + req.getKey();
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        FractionizedDatum.FNode newNodeDatum = FractionizedDatum.FNode.builder()
                .key(req.getKey())
                .next(pred.getSuccessorKey())
                .totalAmount(req.getTotalAmount())
                .remainingAmount(req.getTotalAmount())
                .claimants(req.getClaimants() != null ? req.getClaimants() : List.of())
                .assetName(req.getAssetName())
                .exhausted(false)
                .build();

        PlutusData insertSpendRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));
        PlutusData insertMintRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        PlutusData updatedPredecessorDatum = pred.buildUpdatedPredecessorDatum(req.getKey());

        ScriptTx tx = new ScriptTx()
                .readFrom(proxyStateRef, stateLibraryUtxo, proxyLibraryUtxo, headUtxo)
                .collectFrom(proxyUtxo, proxySpendRedeemer)
                .payToContract(proxyScriptAddress, proxyUtxo.getAmount(), nextStateDatum.toPlutusData())
                .withdraw(stateRewardAddress, BigInteger.ZERO, stateRedeemer.toPlutusData())
                .collectFrom(pred.getUtxo(), insertSpendRedeemer)
                .payToContract(fractionizedAddress, pred.getUtxo().getAmount(), updatedPredecessorDatum)
                .mintAsset(fractionizedScript, List.of(nodeToken), insertMintRedeemer,
                        fractionizedAddress, newNodeDatum.toPlutusData());

        applyFeePaymentsIfNeeded(tx, stateDatumEntity, proxyUtxo);

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Address inserterAddress = new Address(req.getInserterAddress());
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(req.getInserterAddress())
                .collateralPayer(req.getInserterAddress())
                .withRequiredSigners(inserterAddress)
                .withReferenceScripts(stateContract, proxyContract)
                .validFrom(currentSlot - 10)
                .validTo(currentSlot + 600)
                .build();

        return unsignedTx.serializeToHex();
    }

    public String buildClaimTransaction(BuildClaimRequest req) throws ApiException, CborSerializationException {
        PlutusScript script = getFractionizedCertificateContract(
                req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String policyId = validatorToScriptHash(script);
        String scriptAddress = scriptAddress(script);

        String nodeTokenName = NODE_PREFIX_HEX + req.getKey();
        Utxo nodeUtxo = fetchUtxoByToken(scriptAddress, policyId, nodeTokenName);
        FractionizedDatum.FNode nodeDatum = (FractionizedDatum.FNode) FractionizedDatum.fromInlineDatum(nodeUtxo.getInlineDatum());

        if (nodeDatum.isExhausted()) {
            throw new IllegalStateException("Node '" + req.getKey() + "' has been exhausted — no tokens remaining.");
        }
        if (req.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (req.getAmount() > nodeDatum.getRemainingAmount()) {
            throw new IllegalArgumentException("Requested amount " + req.getAmount()
                    + " exceeds remaining amount " + nodeDatum.getRemainingAmount() + ".");
        }

        Address claimerAddr = new Address(req.getClaimerAddress());
        String claimerPubKeyHash = HexUtil.encodeHexString(claimerAddr.getPaymentCredentialHash().orElseThrow(
                () -> new IllegalArgumentException("Could not extract payment key hash from claimer address")));

        Utxo headUtxo = fetchUtxoByToken(scriptAddress, policyId, NODE_PREFIX_HEX);

        FractionizedDatum.FNode updatedNodeDatum = nodeDatum.withRemainingAmount(
                nodeDatum.getRemainingAmount() - req.getAmount());

        PlutusData claimSpendRedeemer = ConstrPlutusData.of(2,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())),
                BytesPlutusData.of(HexUtil.decodeHexString(claimerPubKeyHash)),
                BigIntPlutusData.of(BigInteger.valueOf(req.getAmount())));
        PlutusData claimMintRedeemer = ConstrPlutusData.of(2,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())),
                BytesPlutusData.of(HexUtil.decodeHexString(claimerPubKeyHash)),
                BigIntPlutusData.of(BigInteger.valueOf(req.getAmount())));

        Asset fungibleToken = Asset.builder()
                .name("0x" + nodeDatum.getAssetName())
                .value(BigInteger.valueOf(req.getAmount()))
                .build();

        ScriptTx tx = new ScriptTx()
                .readFrom(headUtxo)
                .collectFrom(nodeUtxo, claimSpendRedeemer)
                .payToContract(scriptAddress, nodeUtxo.getAmount(), updatedNodeDatum.toPlutusData())
                .mintAsset(script, List.of(fungibleToken), claimMintRedeemer,
                        req.getClaimerAddress(), PlutusData.unit());

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(req.getClaimerAddress())
                .collateralPayer(req.getClaimerAddress())
                .withRequiredSigners(claimerAddr)
                .validFrom(currentSlot - 10)
                .validTo(currentSlot + 600)
                .build();

        return unsignedTx.serializeToHex();
    }

    public FractionizedStatusResponse getCertificateStatus(
            String key, String initUtxoTxHash, int initUtxoOutputIndex) throws ApiException {

        PlutusScript script = getFractionizedCertificateContract(initUtxoTxHash, initUtxoOutputIndex);
        String policyId = validatorToScriptHash(script);
        String scriptAddress = scriptAddress(script);

        String nodeTokenName = NODE_PREFIX_HEX + key;
        Optional<Utxo> optUtxo = getCurrentUtxoByUnit(scriptAddress, policyId + nodeTokenName, backendService);

        if (optUtxo.isEmpty()) {
            return FractionizedStatusResponse.builder().key(key).exists(false).build();
        }

        FractionizedDatum datum = FractionizedDatum.fromInlineDatum(optUtxo.get().getInlineDatum());
        if (datum instanceof FractionizedDatum.FNode node) {
            return FractionizedStatusResponse.builder()
                    .key(key)
                    .exists(true)
                    .totalAmount(node.getTotalAmount())
                    .remainingAmount(node.getRemainingAmount())
                    .exhausted(node.isExhausted())
                    .claimants(node.getClaimants())
                    .assetName(node.getAssetName())
                    .next(node.getNext())
                    .build();
        }
        return FractionizedStatusResponse.builder().key(key).exists(false).build();
    }

    public void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PlutusScript getFractionizedCertificateContract(String txHash, int outputIndex) {
        return ValidatorUtils.getFractionizedCertificateContract(txHash, outputIndex);
    }

    private String scriptAddress(PlutusScript script) {
        if (cardanoNetwork == CardanoNetwork.MAINNET) {
            return AddressProvider.getEntAddress(script, com.bloxbean.cardano.client.common.model.Networks.mainnet()).toBech32();
        }
        return AddressProvider.getEntAddress(script, com.bloxbean.cardano.client.common.model.Networks.preprod()).toBech32();
    }

    private Utxo fetchUtxoByToken(String scriptAddress, String policyId, String tokenNameHex) throws ApiException {
        String unit = policyId + tokenNameHex;
        Optional<Utxo> opt = getCurrentUtxoByUnit(scriptAddress, unit, backendService);
        if (opt.isEmpty()) {
            throw new IllegalStateException("UTxO with unit " + unit + " not found at " + scriptAddress);
        }
        return opt.get();
    }

    private PredecessorResult findPredecessor(
            String scriptAddress, String policyId, String key,
            FractionizedDatum.FHead headDatum) throws ApiException {

        if (headDatum.getNext() == null) {
            return PredecessorResult.headPredecessor();
        }
        if (key.compareTo(headDatum.getNext()) < 0) {
            return PredecessorResult.headPredecessor();
        }

        List<Utxo> allUtxos = getAllUtxosAtAddress(scriptAddress);

        Map<String, Utxo> utxoByKey = new HashMap<>();
        Map<String, FractionizedDatum.FNode> datumByKey = new HashMap<>();
        for (Utxo u : allUtxos) {
            if (u.getInlineDatum() == null) continue;
            try {
                FractionizedDatum d = FractionizedDatum.fromInlineDatum(u.getInlineDatum());
                if (d instanceof FractionizedDatum.FNode node) {
                    utxoByKey.put(node.getKey(), u);
                    datumByKey.put(node.getKey(), node);
                }
            } catch (Exception ignored) {}
        }

        String currentKey = headDatum.getNext();
        while (currentKey != null) {
            FractionizedDatum.FNode currentNode = datumByKey.get(currentKey);
            if (currentNode == null) {
                throw new IllegalStateException("Linked list is inconsistent: node '" + currentKey + "' missing.");
            }
            String nextKey = currentNode.getNext();
            if (key.compareTo(currentKey) > 0 && (nextKey == null || key.compareTo(nextKey) < 0)) {
                return PredecessorResult.nodePredecessor(utxoByKey.get(currentKey), currentNode, nextKey);
            }
            if (key.equals(currentKey)) {
                throw new IllegalArgumentException("Key '" + key + "' already exists in the list.");
            }
            currentKey = nextKey;
        }

        return PredecessorResult.headPredecessor();
    }

    private List<Utxo> getAllUtxosAtAddress(String address) throws ApiException {
        List<Utxo> result = new ArrayList<>();
        int page = 1;
        while (true) {
            Result<List<Utxo>> r = backendService.getUtxoService().getUtxos(address, 100, page);
            if (!r.isSuccessful() || r.getValue() == null || r.getValue().isEmpty()) break;
            result.addAll(r.getValue());
            if (r.getValue().size() < 100) break;
            page++;
        }
        return result;
    }

    private String buildForkAndOrphanInsertTransaction(BuildInsertRequest req,
            PlutusScript fractionizedScript, String fractionizedAddress,
            Utxo headUtxo) throws ApiException, CborSerializationException {
        String bootstrapToken = req.getBootstrapTokenName() != null ? req.getBootstrapTokenName() : "";
        Optional<BootstrapDatumEntity> optEntity = bootstrapDatumService.getBootstrapDatum(bootstrapToken, 2);
        if (optEntity.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap datum '" + bootstrapToken + "' not found");
        }
        BootstrapDatumEntity bootstrapDatumEntity = optEntity.get();

        Address userAddress = new Address(req.getInserterAddress());
        byte[] userCredential = userAddress.getPaymentCredentialHash()
                .orElseThrow(() -> new IllegalArgumentException("Invalid Cardano payment address"));

        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, network).toBech32();
        String stateRewardAddress = AddressProvider.getRewardAddress(stateContract, network).toBech32();

        // Resolve bootstrap UTxO (read-only reference input)
        String bootstrapTokenUnit = proxyContract.getPolicyId()
                + HexUtil.encodeHexString(bootstrapToken.getBytes());
        Result<TxContentUtxo> txResult = backendService.getTransactionService()
                .getTransactionUtxos(bootstrapDatumEntity.getTransactionId());
        TxContentUtxoOutputs bootstrapOutput = txResult.getValue().getOutputs().stream()
                .filter(o -> o.getAmount().stream().anyMatch(a -> a.getUnit().equals(bootstrapTokenUnit)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bootstrap UTxO not found for token: " + bootstrapToken));
        Utxo bootstrapUtxo = bootstrapOutput.toUtxos(bootstrapDatumEntity.getTransactionId());

        // Pick a user wallet UTxO to consume (drives state ID derivation)
        List<Utxo> userUtxos = new DefaultUtxoSupplier(backendService.getUtxoService()).getAll(req.getInserterAddress());
        if (userUtxos.isEmpty()) {
            throw new IllegalArgumentException("No UTxOs found for inserter address");
        }
        Utxo userUtxo = userUtxos.get(0);

        // Derive state ID: sha256(txHash || LE_uint16(outputIndex))
        String indexHex = HexUtil.encodeHexString(ByteBuffer.allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) userUtxo.getOutputIndex())
                .array());
        String stateId = DigestUtils.sha256Hex(HexUtil.decodeHexString(userUtxo.getTxHash() + indexHex));

        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer("")
                .extra("{\"uverify_template_id\":\"fractionizedCertificate\"}")
                .build();

        StateDatum stateDatum = StateDatum.fromBootstrapDatum(bootstrapUtxo.getInlineDatum(), userCredential);
        stateDatum.setCertificateDataHash(List.of(cert));
        stateDatum.setCountdown(stateDatum.getCountdown() - 1);
        stateDatum.setId(stateId);

        Asset stateToken = Asset.builder()
                .name("0x" + stateId)
                .value(BigInteger.ONE)
                .build();

        StateRedeemer mintStateRedeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.MINT_STATE)
                .certificates(List.of(cert))
                .build();
        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        Utxo proxyStateRef = validatorHelper.resolveProxyStateUtxo(backendService);
        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();
        Utxo proxyLibraryUtxo = libraryService.getProxyLibraryUtxo();

        // Orphan node: HEAD stays in reference_inputs only, no predecessor consumed
        String nodeTokenName = NODE_PREFIX_HEX + req.getKey();
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        FractionizedDatum.FNode newNodeDatum = FractionizedDatum.FNode.builder()
                .key(req.getKey())
                .next(null)
                .totalAmount(req.getTotalAmount())
                .remainingAmount(req.getTotalAmount())
                .claimants(req.getClaimants() != null ? req.getClaimants() : List.of())
                .assetName(req.getAssetName())
                .exhausted(false)
                .build();

        PlutusData insertMintRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        ScriptTx tx = new ScriptTx()
                .readFrom(bootstrapUtxo, proxyStateRef, stateLibraryUtxo, proxyLibraryUtxo, headUtxo)
                .collectFrom(userUtxo)
                .withdraw(stateRewardAddress, BigInteger.ZERO, mintStateRedeemer.toPlutusData())
                .mintAsset(proxyContract, List.of(stateToken), mintProxyRedeemer,
                        proxyScriptAddress, stateDatum.toPlutusData())
                .mintAsset(fractionizedScript, List.of(nodeToken), insertMintRedeemer,
                        fractionizedAddress, newNodeDatum.toPlutusData());

        if (bootstrapDatumEntity.getFee() > 0) {
            long feePerReceiver = bootstrapDatumEntity.getFee() / stateDatum.getFeeReceivers().size();
            for (byte[] paymentCredential : stateDatum.getFeeReceivers()) {
                Credential cred = Credential.fromKey(paymentCredential);
                String receiverAddress = AddressProvider.getEntAddress(cred, network).toBech32();
                tx.payToAddress(receiverAddress, Amount.lovelace(BigInteger.valueOf(feePerReceiver)));
            }
        }

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(req.getInserterAddress())
                .collateralPayer(req.getInserterAddress())
                .withRequiredSigners(userAddress)
                .withReferenceScripts(stateContract, proxyContract)
                .validFrom(currentSlot - 10)
                .validTo(currentSlot + 600)
                .build();

        return unsignedTx.serializeToHex();
    }

    private Optional<StateDatumEntity> resolveStateDatumOptional(String address, String bootstrapTokenName) {
        if (bootstrapTokenName == null || bootstrapTokenName.isEmpty()) {
            List<StateDatumEntity> list = stateDatumService.findByOwner(address, 2);
            if (list.isEmpty()) return Optional.empty();
            return Optional.of(stateDatumService.selectCheapestStateDatum(list));
        } else {
            return stateDatumService.findByUserAndBootstrapToken(address, bootstrapTokenName);
        }
    }

    private StateDatumEntity resolveStateDatum(String address, String bootstrapTokenName) {
        Optional<StateDatumEntity> opt;
        if (bootstrapTokenName == null || bootstrapTokenName.isEmpty()) {
            List<StateDatumEntity> list = stateDatumService.findByOwner(address, 2);
            if (list.isEmpty()) throw new IllegalStateException("No UVerify state found for address " + address);
            opt = Optional.of(stateDatumService.selectCheapestStateDatum(list));
        } else {
            opt = stateDatumService.findByUserAndBootstrapToken(address, bootstrapTokenName);
            if (opt.isEmpty()) throw new IllegalStateException("No UVerify state found for given bootstrap token");
        }
        return opt.get();
    }

    private void applyFeePaymentsIfNeeded(ScriptTx tx, StateDatumEntity stateDatum, Utxo proxyUtxo) {
        StateDatum datum = StateDatum.fromPreviousStateDatum(proxyUtxo.getInlineDatum());
        var bootstrapDatum = stateDatum.getBootstrapDatum();
        if (datum.getCountdown() % bootstrapDatum.getFeeInterval() == 0 && bootstrapDatum.getFee() > 0) {
            long feePerReceiver = bootstrapDatum.getFee() / bootstrapDatum.getFeeReceivers().size();
            for (var receiver : bootstrapDatum.getFeeReceivers()) {
                com.bloxbean.cardano.client.address.Credential cred =
                        com.bloxbean.cardano.client.address.Credential.fromKey(HexUtil.decodeHexString(receiver.getCredential()));
                String receiverAddress = AddressProvider.getEntAddress(cred, network).toBech32();
                tx.payToAddress(receiverAddress, com.bloxbean.cardano.client.api.model.Amount.lovelace(BigInteger.valueOf(feePerReceiver)));
            }
        }
    }

    // ── PredecessorResult ─────────────────────────────────────────────────────

    private static class PredecessorResult {
        private final boolean headPredecessor;
        private final Utxo utxo;
        private final FractionizedDatum.FNode node;
        private final String successorKey;

        private PredecessorResult(boolean headPredecessor, Utxo utxo,
                                  FractionizedDatum.FNode node, String successorKey) {
            this.headPredecessor = headPredecessor;
            this.utxo = utxo;
            this.node = node;
            this.successorKey = successorKey;
        }

        static PredecessorResult headPredecessor() {
            return new PredecessorResult(true, null, null, null);
        }

        static PredecessorResult nodePredecessor(Utxo utxo, FractionizedDatum.FNode node, String successorKey) {
            return new PredecessorResult(false, utxo, node, successorKey);
        }

        boolean isHeadPredecessor() { return headPredecessor; }
        Utxo getUtxo() { return utxo; }
        String getSuccessorKey() { return successorKey; }

        PlutusData buildUpdatedPredecessorDatum(String newKey) {
            return node.withNext(newKey).toPlutusData();
        }
    }
}
