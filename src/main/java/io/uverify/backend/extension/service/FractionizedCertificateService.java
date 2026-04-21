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
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.extension.dto.fractionized.*;
import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.validators.fractionized.FractionizedConfig;
import io.uverify.backend.extension.validators.fractionized.FractionizedDatum;
import io.uverify.backend.model.UVerifyCertificate;
import io.uverify.backend.service.CardanoBlockchainService;
import io.uverify.backend.service.StateDatumService;
import io.uverify.backend.util.CardanoUtils;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;

import static io.uverify.backend.util.ValidatorUtils.getCurrentUtxoByUnit;
import static io.uverify.backend.util.ValidatorUtils.validatorToScriptHash;

@Slf4j
@Service
@ConditionalOnProperty(value = "extensions.fractionized-certificate.enabled", havingValue = "true")
public class FractionizedCertificateService {

    private static final String NODE_PREFIX_HEX = "46524e";

    private static final int NODE_KEY_MAX_HEX_CHARS = (32 - 3) * 2; // 58 hex chars = 29 bytes

    private static String nodeTokenName(String keyHex) {
        return NODE_PREFIX_HEX + keyHex.substring(0, Math.min(NODE_KEY_MAX_HEX_CHARS, keyHex.length()));
    }

    private final CardanoNetwork cardanoNetwork;
    private BackendService backendService;

    @Autowired
    private ValidatorHelper validatorHelper;
    @Autowired
    private StateDatumService stateDatumService;
    @Autowired
    private CardanoBlockchainService cardanoBlockchainService;

    @Autowired
    public FractionizedCertificateService(
            @Value("${cardano.network}") String network,
            @Value("${cardano.backend.service.type}") String cardanoBackendServiceType,
            @Value("${cardano.backend.blockfrost.baseUrl}") String blockfrostBaseUrl,
            @Value("${cardano.backend.blockfrost.projectId}") String blockfrostProjectId) {

        this.cardanoNetwork = CardanoNetwork.valueOf(network);

        if (cardanoBackendServiceType.equals("blockfrost")) {
            this.backendService = new BFBackendService(blockfrostBaseUrl, blockfrostProjectId);
        } else if (cardanoBackendServiceType.equals("koios")) {
            this.backendService = new KoiosBackendService(Constants.KOIOS_PREPROD_URL);
        }
    }

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

            FractionizedConfig config = req.getConfig();
            if (config == null) {
                throw new IllegalArgumentException("config is required for the first issuance (Init path). " +
                        "Provide deployer payment key hash and optionally allowedInserters / cip68ScriptAddress.");
            }
            config.setUverifyValidatorHash(validatorToScriptHash(validatorHelper.getParameterizedUVerifyStateContract()));

            init.setDeployerAddress(req.getSenderAddress());
            init.setInitUtxoTxHash(req.getInitUtxoTxHash());
            init.setInitUtxoOutputIndex(req.getInitUtxoOutputIndex());
            init.setConfig(config);
            init.setKey(req.getKey());
            init.setTotalAmount(req.getTotalAmount());
            init.setClaimants(req.getClaimants());
            init.setAssetName(req.getAssetName());
            init.setBootstrapTokenName(req.getBootstrapTokenName());
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
        PlutusScript fractionizedScript = getFractionizedCertificateContract(req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String fractionizedAddress = scriptAddress(fractionizedScript);

        // ── 1. Find the init UTxO in the deployer's wallet ───────────────────
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

        Address deployerAddress = new Address(req.getDeployerAddress());
        byte[] deployerCredential = deployerAddress.getPaymentCredentialHash()
                .orElseThrow(() -> new IllegalArgumentException("Invalid deployer address"));

        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer(HexUtil.encodeHexString(deployerCredential))
                .extra("{\"uverify_template_id\":\"fractionizedCertificate\"}")
                .build();

        Asset headToken = Asset.builder()
                .name("0x" + NODE_PREFIX_HEX)
                .value(BigInteger.ONE)
                .build();

        String nodeTokenName = nodeTokenName(req.getKey());
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        PlutusData initMintRedeemer = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        FractionizedDatum.FHead headDatum = FractionizedDatum.FHead.builder()
                .next(Optional.of(HexUtil.decodeHexString(req.getKey())))  // HEAD points to first node
                .config(req.getConfig())
                .build();

        List<byte[]> initClaimantBytes = req.getClaimants() != null
                ? req.getClaimants().stream().map(HexUtil::decodeHexString).toList()
                : List.of();

        FractionizedDatum.FNode nodeDatum = FractionizedDatum.FNode.builder()
                .key(HexUtil.decodeHexString(req.getKey()))
                .next(Optional.empty())
                .totalAmount(req.getTotalAmount())
                .remainingAmount(req.getTotalAmount())
                .claimants(initClaimantBytes)
                .assetName(HexUtil.decodeHexString(req.getAssetName()))
                .exhausted(false)
                .build();

        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();

        ScriptTx fractionizedMintTx = cardanoBlockchainService.buildUVerifyCertificateScriptTx(
                req.getDeployerAddress(), List.of(cert));

        fractionizedMintTx = fractionizedMintTx
                .mintAsset(fractionizedScript, List.of(headToken, nodeToken), initMintRedeemer)
                .payToContract(fractionizedAddress, Amount.asset(AssetUtil.getUnit(fractionizedScript.getPolicyId(), headToken), 1L), headDatum.toPlutusData())
                .payToContract(fractionizedAddress, Amount.asset(AssetUtil.getUnit(fractionizedScript.getPolicyId(), nodeToken), 1L), nodeDatum.toPlutusData());

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(fractionizedMintTx)
                .feePayer(req.getDeployerAddress())
                .collateralPayer(req.getDeployerAddress())
                .mergeOutputs(false)
                .withRequiredSigners(deployerAddress)
                .withReferenceScripts(stateContract, proxyContract)
                .validFrom(currentSlot - 10)
                .validTo(currentSlot + 600)
                .build();
        // Check if the init utxo is already part of the transaction
        // because of the UVerify certificate
        if (unsignedTx.getBody().getInputs().stream().anyMatch(utxo ->
                utxo.getTransactionId().equals(initUtxo.getTxHash())
                        && utxo.getIndex() == initUtxo.getOutputIndex())) {
            return unsignedTx.serializeToHex();
        }

        fractionizedMintTx = fractionizedMintTx.collectFrom(initUtxo);

        unsignedTx = new QuickTxBuilder(backendService)
                .compose(fractionizedMintTx)
                .feePayer(req.getDeployerAddress())
                .collateralPayer(req.getDeployerAddress())
                .mergeOutputs(false)
                .withRequiredSigners(deployerAddress)
                .withReferenceScripts(stateContract, proxyContract)
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

        byte[] inserterCredential = new Address(req.getInserterAddress()).getPaymentCredentialHash()
                .orElseThrow(() -> new IllegalArgumentException("Invalid inserter address"));

        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer(HexUtil.encodeHexString(inserterCredential))
                .extra("{\"uverify_template_id\":\"fractionizedCertificate\"}")
                .build();

        String nodeTokenName = nodeTokenName(req.getKey());
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        List<byte[]> insertClaimantBytes = req.getClaimants() != null
                ? req.getClaimants().stream().map(HexUtil::decodeHexString).toList()
                : List.of();
        String insertSuccessorKey = pred.getSuccessorKey();

        FractionizedDatum.FNode newNodeDatum = FractionizedDatum.FNode.builder()
                .key(HexUtil.decodeHexString(req.getKey()))
                .next(insertSuccessorKey != null ? Optional.of(HexUtil.decodeHexString(insertSuccessorKey)) : Optional.empty())
                .totalAmount(req.getTotalAmount())
                .remainingAmount(req.getTotalAmount())
                .claimants(insertClaimantBytes)
                .assetName(HexUtil.decodeHexString(req.getAssetName()))
                .exhausted(false)
                .build();

        PlutusData insertSpendRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));
        PlutusData insertMintRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        PlutusData updatedPredecessorDatum = pred.buildUpdatedPredecessorDatum(req.getKey());

        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();

        ScriptTx fractionizedMintTx = cardanoBlockchainService.buildUVerifyCertificateScriptTx(
                        req.getInserterAddress(), List.of(cert))
                .readFrom(headUtxo)
                .collectFrom(pred.getUtxo(), insertSpendRedeemer)
                .payToContract(fractionizedAddress, pred.getUtxo().getAmount(), updatedPredecessorDatum)
                .mintAsset(fractionizedScript, List.of(nodeToken), insertMintRedeemer,
                        fractionizedAddress, newNodeDatum.toPlutusData());

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Address inserterAddress = new Address(req.getInserterAddress());
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(fractionizedMintTx)
                .feePayer(req.getInserterAddress())
                .collateralPayer(req.getInserterAddress())
                .mergeOutputs(false)
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

        String nodeTokenName = nodeTokenName(req.getKey());
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
                .name("0x" + HexUtil.encodeHexString(nodeDatum.getAssetName()))
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

        String nodeTokenName = nodeTokenName(key);
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
                    .claimants(node.getClaimants().stream().map(HexUtil::encodeHexString).toList())
                    .assetName(HexUtil.encodeHexString(node.getAssetName()))
                    .next(node.getNext().map(HexUtil::encodeHexString).orElse(null))
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

        Optional<byte[]> headNextBytes = headDatum.getNext();
        if (headNextBytes.isEmpty()) {
            return PredecessorResult.headPredecessor();
        }
        String headNextHex = HexUtil.encodeHexString(headNextBytes.get());
        if (key.compareTo(headNextHex) < 0) {
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
                    String nodeKeyHex = HexUtil.encodeHexString(node.getKey());
                    utxoByKey.put(nodeKeyHex, u);
                    datumByKey.put(nodeKeyHex, node);
                }
            } catch (Exception ignored) {
            }
        }

        String currentKey = headNextHex;
        while (currentKey != null) {
            FractionizedDatum.FNode currentNode = datumByKey.get(currentKey);
            if (currentNode == null) {
                throw new IllegalStateException("Linked list is inconsistent: node '" + currentKey + "' missing.");
            }
            String nextKey = currentNode.getNext().map(HexUtil::encodeHexString).orElse(null);
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
        Address userAddress = new Address(req.getInserterAddress());
        byte[] userCredential = userAddress.getPaymentCredentialHash()
                .orElseThrow(() -> new IllegalArgumentException("Invalid Cardano payment address"));

        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer(HexUtil.encodeHexString(userCredential))
                .extra("{\"uverify_template_id\":\"fractionizedCertificate\"}")
                .build();

        // Orphan node: HEAD stays in reference_inputs only, no predecessor consumed
        String nodeTokenName = nodeTokenName(req.getKey());
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        List<byte[]> forkClaimantBytes = req.getClaimants() != null
                ? req.getClaimants().stream().map(HexUtil::decodeHexString).toList()
                : List.of();

        FractionizedDatum.FNode newNodeDatum = FractionizedDatum.FNode.builder()
                .key(HexUtil.decodeHexString(req.getKey()))
                .next(Optional.empty())
                .totalAmount(req.getTotalAmount())
                .remainingAmount(req.getTotalAmount())
                .claimants(forkClaimantBytes)
                .assetName(HexUtil.decodeHexString(req.getAssetName()))
                .exhausted(false)
                .build();

        PlutusData insertMintRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        // Proxy cert token: proves a UVerify cert is present in this tx
        Asset certToken = Asset.builder()
                .name("0x" + req.getKey())
                .value(BigInteger.ONE)
                .build();

        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();

        ScriptTx uverifyCertificateTx = cardanoBlockchainService.buildUVerifyCertificateScriptTx(
                req.getInserterAddress(), List.of(cert));

        ScriptTx fractionizedMintTx = new ScriptTx()
                .readFrom(headUtxo)
                .mintAsset(fractionizedScript, List.of(nodeToken), insertMintRedeemer,
                        fractionizedAddress, newNodeDatum.toPlutusData())
                .mintAsset(fractionizedScript, List.of(certToken), insertMintRedeemer);

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(fractionizedMintTx, uverifyCertificateTx)
                .feePayer(req.getInserterAddress())
                .collateralPayer(req.getInserterAddress())
                .mergeOutputs(false)
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

        boolean isHeadPredecessor() {
            return headPredecessor;
        }

        Utxo getUtxo() {
            return utxo;
        }

        String getSuccessorKey() {
            return successorKey;
        }

        PlutusData buildUpdatedPredecessorDatum(String newKey) {
            return node.withNext(HexUtil.decodeHexString(newKey)).toPlutusData();
        }
    }
}
