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
import io.uverify.backend.extension.dto.tokenizable.BuildInitRequest;
import io.uverify.backend.extension.dto.tokenizable.BuildInsertRequest;
import io.uverify.backend.extension.dto.tokenizable.BuildRedeemRequest;
import io.uverify.backend.extension.dto.tokenizable.CertificateStatusResponse;
import io.uverify.backend.extension.dto.tokenizable.TokenizableBuildRequest;
import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.validators.tokenizable.TokenizableConfig;
import io.uverify.backend.extension.validators.tokenizable.TokenizableDatum;
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
import java.util.List;
import java.util.Optional;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;
import static io.uverify.backend.util.ValidatorUtils.*;

/**
 * Builds unsigned Cardano transactions for the tokenizable-certificate contract.
 *
 * <h3>Supported operations</h3>
 * <ul>
 *   <li><b>Init</b> – creates the HEAD node and sets the list configuration.</li>
 *   <li><b>Insert</b> – submits a UVerify certificate and mints a node NFT in the
 *       sorted linked list in a single atomic transaction.</li>
 *   <li><b>Redeem (Claim)</b> – mints the owner NFT for an available node.</li>
 *   <li><b>Status</b> – queries the on-chain state of a node by key.</li>
 * </ul>
 *
 * <h3>EUTXO constraint</h3>
 * The MINT validator for Insert reads the HEAD UTxO from {@code reference_inputs}.
 * The SPEND validator for Insert consumes the predecessor node from {@code inputs}.
 * Cardano's ledger requires these sets to be disjoint, so when HEAD would be the
 * predecessor (empty list, or new key smaller than all existing keys) the Insert
 * cannot be executed in a single transaction.  The service returns a descriptive
 * error in that case.
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "extensions.tokenizable-certificate.enabled", havingValue = "true")
public class TokenizableCertificateService {

    /** Hex-encoded token-name prefix for all nodes in a list ("TCN"). */
    private static final String NODE_PREFIX_HEX = "54434e";

    /** CIP-68 label-222 prefix (user token). */
    private static final String CIP68_USER_PREFIX_HEX = "000de140";

    /** CIP-68 label-100 prefix (reference token). */
    private static final String CIP68_REF_PREFIX_HEX = "000643b0";

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
    public TokenizableCertificateService(
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
     * Unified entry point — routes to Init, Insert, or Redeem based on {@code req.getType()}
     * and current on-chain state.
     *
     * <ul>
     *   <li>{@code CREATE} — checks whether the HEAD node exists. If not, runs Init using
     *       {@code req.getConfig()}; otherwise runs Insert.</li>
     *   <li>{@code REDEEM} — runs Redeem (claim NFT) for the given key.</li>
     * </ul>
     */
    public String buildTransaction(TokenizableBuildRequest req) throws ApiException, CborSerializationException {
        if (req.getType() == ExtensionTransactionType.REDEEM) {
            BuildRedeemRequest redeem = new BuildRedeemRequest();
            redeem.setOwnerAddress(req.getSenderAddress());
            redeem.setKey(req.getKey());
            redeem.setInitUtxoTxHash(req.getInitUtxoTxHash());
            redeem.setInitUtxoOutputIndex(req.getInitUtxoOutputIndex());
            return buildRedeemTransaction(redeem);
        }

        // CREATE — decide Init vs Insert by checking whether HEAD already exists
        PlutusScript script = getTokenizableCertificateContract(req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
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
            init.setKey(req.getKey());
            init.setOwnerPubKeyHash(req.getOwnerPubKeyHash());
            init.setAssetName(req.getAssetName());
            init.setBootstrapTokenName(req.getBootstrapTokenName());
            return buildInitTransaction(init);
        } else {
            BuildInsertRequest insert = new BuildInsertRequest();
            insert.setInserterAddress(req.getSenderAddress());
            insert.setKey(req.getKey());
            insert.setOwnerPubKeyHash(req.getOwnerPubKeyHash());
            insert.setAssetName(req.getAssetName());
            insert.setInitUtxoTxHash(req.getInitUtxoTxHash());
            insert.setInitUtxoOutputIndex(req.getInitUtxoOutputIndex());
            insert.setBootstrapTokenName(req.getBootstrapTokenName());
            return buildInsertTransaction(insert);
        }
    }

    /**
     * Builds an unsigned Init transaction that simultaneously:
     * <ol>
     *   <li>Consumes the one-shot init UTxO (parameterises the policy).</li>
     *   <li>Creates a new UVerify state (MINT_STATE) tied to the deployer.</li>
     *   <li>Mints the HEAD node token and the first certificate node token.</li>
     *   <li>Mints a proxy certificate token (enforces UVerify cert presence).</li>
     * </ol>
     * Empty linked lists are not permitted by the on-chain validator, so Init
     * always includes the first node.
     */
    public String buildInitTransaction(BuildInitRequest req) throws ApiException, CborSerializationException {
        PlutusScript tokenizableScript = getTokenizableCertificateContract(req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String tokenizableAddress = scriptAddress(tokenizableScript);

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

        // ── 2. Bootstrap datum for UVerify state creation (MINT_STATE) ───────
        String bootstrapToken = req.getBootstrapTokenName() != null ? req.getBootstrapTokenName() : "";
        Optional<BootstrapDatumEntity> optEntity = bootstrapDatumService.getBootstrapDatum(bootstrapToken, 2);
        if (optEntity.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap datum '" + bootstrapToken + "' not found");
        }
        BootstrapDatumEntity bootstrapDatumEntity = optEntity.get();

        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, network).toBech32();
        String stateRewardAddress = AddressProvider.getRewardAddress(stateContract, network).toBech32();

        // Derive state ID from the init UTxO (same formula as fork-and-orphan)
        String indexHex = HexUtil.encodeHexString(ByteBuffer.allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) initUtxo.getOutputIndex())
                .array());
        String stateId = DigestUtils.sha256Hex(HexUtil.decodeHexString(initUtxo.getTxHash() + indexHex));

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

        // ── 3. Certificate and state datum ───────────────────────────────────
        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer(HexUtil.encodeHexString(deployerCredential))
                .extra("{\"uverify_template_id\":\"tokenizableCertificate\"}")
                .build();

        StateDatum stateDatum = StateDatum.fromBootstrapDatum(bootstrapUtxo.getInlineDatum(), deployerCredential);
        stateDatum.setCertificateDataHash(List.of(cert));
        stateDatum.setCountdown(stateDatum.getCountdown() - 1);
        stateDatum.setId(stateId);

        StateRedeemer mintStateRedeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.MINT_STATE)
                .certificates(List.of(cert))
                .build();
        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        // ── 4. Tokens ────────────────────────────────────────────────────────
        Asset stateToken = Asset.builder()
                .name("0x" + stateId)
                .value(BigInteger.ONE)
                .build();

        // Proxy cert token: proves a UVerify cert is present in this tx
        Asset certToken = Asset.builder()
                .name("0x" + req.getKey())
                .value(BigInteger.ONE)
                .build();

        Asset headToken = Asset.builder()
                .name("0x" + NODE_PREFIX_HEX)
                .value(BigInteger.ONE)
                .build();

        String nodeTokenName = NODE_PREFIX_HEX + req.getKey();
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        // Init { key } redeemer: constr 0 with key bytes
        PlutusData initMintRedeemer = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        // ── 5. Datums ────────────────────────────────────────────────────────
        TokenizableDatum.Head headDatum = TokenizableDatum.Head.builder()
                .next(req.getKey())        // HEAD points to first node
                .config(req.getConfig())
                .build();

        TokenizableDatum.Node nodeDatum = TokenizableDatum.Node.builder()
                .key(req.getKey())
                .next(null)
                .owner(req.getOwnerPubKeyHash())
                .assetName(req.getAssetName())
                .redeemed(false)
                .build();

        // ── 6. Build transaction ──────────────────────────────────────────────
        Utxo proxyStateRef = validatorHelper.resolveProxyStateUtxo(backendService);
        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();
        Utxo proxyLibraryUtxo = libraryService.getProxyLibraryUtxo();

        ScriptTx tx = new ScriptTx()
                .readFrom(bootstrapUtxo, proxyStateRef, stateLibraryUtxo, proxyLibraryUtxo)
                .collectFrom(List.of(initUtxo))
                .withdraw(stateRewardAddress, BigInteger.ZERO, mintStateRedeemer.toPlutusData())
                .mintAsset(proxyContract, List.of(stateToken), mintProxyRedeemer,
                        proxyScriptAddress, stateDatum.toPlutusData())
                .mintAsset(tokenizableScript, List.of(headToken), initMintRedeemer,
                        tokenizableAddress, headDatum.toPlutusData())
                .mintAsset(tokenizableScript, List.of(nodeToken), initMintRedeemer,
                        tokenizableAddress, nodeDatum.toPlutusData())
                .mintAsset(tokenizableScript, List.of(certToken), initMintRedeemer);

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

    /**
     * Builds an unsigned Insert transaction that simultaneously:
     * <ol>
     *   <li>Updates the caller's UVerify state with the certificate hash.</li>
     *   <li>Mints the node NFT and inserts it at the correct sorted position.</li>
     * </ol>
     *
     * @throws IllegalStateException if the HEAD would be the predecessor (EUTXO constraint).
     */
    public String buildInsertTransaction(BuildInsertRequest req) throws ApiException, CborSerializationException {
        PlutusScript tokenizableScript = getTokenizableCertificateContract(
                req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String policyId = validatorToScriptHash(tokenizableScript);
        String tokenizableAddress = scriptAddress(tokenizableScript);

        // ── 1. Locate HEAD and predecessor ───────────────────────────────────
        Utxo headUtxo = fetchUtxoByToken(tokenizableAddress, policyId, NODE_PREFIX_HEX);
        TokenizableDatum.Head headDatum = (TokenizableDatum.Head) TokenizableDatum.fromInlineDatum(headUtxo.getInlineDatum());

        PredecessorResult pred = findPredecessor(tokenizableAddress, policyId, req.getKey(), headDatum);
        if (pred.isHeadPredecessor()) {
            String bootstrapTokenForFork = req.getBootstrapTokenName() != null ? req.getBootstrapTokenName() : "";
            Optional<StateDatumEntity> existingState = resolveStateDatumOptional(req.getInserterAddress(), bootstrapTokenForFork);
            if (existingState.isPresent()) {
                throw new IllegalStateException(
                        "Cannot insert key '" + req.getKey() + "': the HEAD node would be the predecessor. "
                        + "This is impossible due to the Cardano EUTXO constraint.");
            }
            return buildForkAndOrphanInsertTransaction(req, tokenizableScript, tokenizableAddress, headUtxo);
        }

        // ── 2. UVerify state update components ───────────────────────────────
        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        String proxyScriptAddress = scriptAddress(proxyContract);
        String stateRewardAddress = AddressProvider.getRewardAddress(stateContract, network).toBech32();
        String proxyScriptHash = validatorToScriptHash(proxyContract);

        // Find the inserter's proxy UTxO
        String bootstrapToken = req.getBootstrapTokenName() != null ? req.getBootstrapTokenName() : "";
        StateDatumEntity stateDatumEntity = resolveStateDatum(req.getInserterAddress(), bootstrapToken);
        String unit = proxyScriptHash + stateDatumEntity.getId();
        Optional<Utxo> optProxyUtxo = getCurrentUtxoByUnit(proxyScriptAddress, unit, backendService);
        if (optProxyUtxo.isEmpty()) {
            throw new IllegalStateException("Proxy state UTxO not found for inserter address");
        }
        Utxo proxyUtxo = optProxyUtxo.get();

        byte[] inserterCredential = new Address(req.getInserterAddress()).getPaymentCredentialHash()
                .orElseThrow(() -> new IllegalArgumentException("Invalid inserter address"));

        UVerifyCertificate cert = UVerifyCertificate.builder()
                .hash(req.getKey())
                .algorithm("sha3_256")
                .issuer(HexUtil.encodeHexString(inserterCredential))
                .extra("{\"uverify_template_id\":\"tokenizableCertificate\"}")
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

        // ── 3. Tokenizable insert components ─────────────────────────────────
        String nodeTokenName = NODE_PREFIX_HEX + req.getKey();
        Asset nodeToken = Asset.builder()
                .name("0x" + nodeTokenName)
                .value(BigInteger.ONE)
                .build();

        TokenizableDatum.Node newNodeDatum = TokenizableDatum.Node.builder()
                .key(req.getKey())
                .next(pred.getSuccessorKey())    // what the predecessor's current next was
                .owner(req.getOwnerPubKeyHash())
                .assetName(req.getAssetName())
                .redeemed(false)
                .build();

        PlutusData insertSpendRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));
        PlutusData insertMintRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        // Compute updated predecessor datum (update its `next` to point to the new key)
        PlutusData updatedPredecessorDatum = pred.buildUpdatedPredecessorDatum(req.getKey());

        // Proxy cert token: proves a UVerify cert is present in this tx
        Asset certToken = Asset.builder()
                .name("0x" + req.getKey())
                .value(BigInteger.ONE)
                .build();
        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        // ── 4. Build ScriptTx ─────────────────────────────────────────────────
        ScriptTx tx = new ScriptTx()
                // UVerify part
                .readFrom(proxyStateRef, stateLibraryUtxo, proxyLibraryUtxo, headUtxo)
                .collectFrom(proxyUtxo, proxySpendRedeemer)
                .payToContract(proxyScriptAddress, proxyUtxo.getAmount(), nextStateDatum.toPlutusData())
                .withdraw(stateRewardAddress, BigInteger.ZERO, stateRedeemer.toPlutusData())
                // Tokenizable insert part
                .collectFrom(pred.getUtxo(), insertSpendRedeemer)
                .payToContract(tokenizableAddress, pred.getUtxo().getAmount(), updatedPredecessorDatum)
                .mintAsset(tokenizableScript, List.of(nodeToken), insertMintRedeemer,
                        tokenizableAddress, newNodeDatum.toPlutusData())
                .mintAsset(tokenizableScript, List.of(certToken), insertMintRedeemer);

        // Optional: fee payments from bootstrap datum
        applyFeePaymentsIfNeeded(tx, stateDatumEntity, proxyUtxo);

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Address inserterAddress = new Address(req.getInserterAddress());
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(tx)
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

    /**
     * Builds an unsigned Redeem (claim) transaction.
     * The owner must sign the returned transaction.
     */
    public String buildRedeemTransaction(BuildRedeemRequest req) throws ApiException, CborSerializationException {
        PlutusScript script = getTokenizableCertificateContract(
                req.getInitUtxoTxHash(), req.getInitUtxoOutputIndex());
        String policyId = validatorToScriptHash(script);
        String scriptAddress = scriptAddress(script);

        // Find the node UTxO
        String nodeTokenName = NODE_PREFIX_HEX + req.getKey();
        Utxo nodeUtxo = fetchUtxoByToken(scriptAddress, policyId, nodeTokenName);
        TokenizableDatum.Node nodeDatum = (TokenizableDatum.Node) TokenizableDatum.fromInlineDatum(nodeUtxo.getInlineDatum());

        if (nodeDatum.isRedeemed()) {
            throw new IllegalStateException("Node '" + req.getKey() + "' has already been claimed.");
        }

        // HEAD for reference (MINT validator needs it)
        Utxo headUtxo = fetchUtxoByToken(scriptAddress, policyId, NODE_PREFIX_HEX);
        TokenizableDatum.Head headDatum = (TokenizableDatum.Head) TokenizableDatum.fromInlineDatum(headUtxo.getInlineDatum());
        TokenizableConfig config = headDatum.getConfig();

        // Redeemed node output datum (status → Redeemed)
        TokenizableDatum.Node redeemedNodeDatum = TokenizableDatum.Node.builder()
                .key(nodeDatum.getKey())
                .next(nodeDatum.getNext())
                .owner(nodeDatum.getOwner())
                .assetName(nodeDatum.getAssetName())
                .redeemed(true)
                .build();

        Address ownerAddress = new Address(req.getOwnerAddress());

        PlutusData redeemSpendRedeemer = ConstrPlutusData.of(2,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));
        PlutusData redeemMintRedeemer = ConstrPlutusData.of(2,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        ScriptTx tx = new ScriptTx()
                .readFrom(headUtxo)
                .collectFrom(nodeUtxo, redeemSpendRedeemer)
                .payToContract(scriptAddress, nodeUtxo.getAmount(), redeemedNodeDatum.toPlutusData());

        if (config.getCip68ScriptAddress() == null) {
            // Plain (non-CIP-68) token — mint 1 and send to owner
            Asset userToken = Asset.builder()
                    .name("0x" + nodeDatum.getAssetName())
                    .value(BigInteger.ONE)
                    .build();
            tx.mintAsset(script, List.of(userToken), redeemMintRedeemer,
                    req.getOwnerAddress(), PlutusData.unit());
        } else {
            // CIP-68: mint user token + reference token
            String userTn = CIP68_USER_PREFIX_HEX + nodeDatum.getAssetName();
            String refTn  = CIP68_REF_PREFIX_HEX  + nodeDatum.getAssetName();
            Asset userToken = Asset.builder().name("0x" + userTn).value(BigInteger.ONE).build();
            Asset refToken  = Asset.builder().name("0x" + refTn).value(BigInteger.ONE).build();

            String cip68ScriptAddress = AddressProvider.getEntAddress(
                    PlutusV3Script.builder().cborHex("").build(), // placeholder — actual address resolved by hash
                    network).toBech32();
            // Resolve CIP-68 reference script address from the stored script hash
            cip68ScriptAddress = resolveScriptAddress(config.getCip68ScriptAddress());

            tx.mintAsset(script, List.of(userToken, refToken), redeemMintRedeemer,
                    req.getOwnerAddress(), PlutusData.unit())
              .payToContract(cip68ScriptAddress,
                      List.of(Amount.asset(policyId + refTn, 1)),
                      PlutusData.unit());
        }

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        Transaction unsignedTx = new QuickTxBuilder(backendService)
                .compose(tx)
                .feePayer(req.getOwnerAddress())
                .collateralPayer(req.getOwnerAddress())
                .withRequiredSigners(ownerAddress)
                .validFrom(currentSlot - 10)
                .validTo(currentSlot + 600)
                .build();

        return unsignedTx.serializeToHex();
    }

    /**
     * Returns the on-chain status of a node identified by its key.
     */
    public CertificateStatusResponse getCertificateStatus(
            String key, String initUtxoTxHash, int initUtxoOutputIndex) throws ApiException {

        PlutusScript script = getTokenizableCertificateContract(initUtxoTxHash, initUtxoOutputIndex);
        String policyId = validatorToScriptHash(script);
        String scriptAddress = scriptAddress(script);

        String nodeTokenName = NODE_PREFIX_HEX + key;
        Optional<Utxo> optUtxo = getCurrentUtxoByUnit(scriptAddress, policyId + nodeTokenName, backendService);

        if (optUtxo.isEmpty()) {
            return CertificateStatusResponse.builder().key(key).exists(false).claimed(false).build();
        }

        TokenizableDatum datum = TokenizableDatum.fromInlineDatum(optUtxo.get().getInlineDatum());
        if (datum instanceof TokenizableDatum.Node node) {
            return CertificateStatusResponse.builder()
                    .key(key)
                    .exists(true)
                    .claimed(node.isRedeemed())
                    .ownerPubKeyHash(node.getOwner())
                    .assetName(node.getAssetName())
                    .next(node.getNext())
                    .build();
        }
        return CertificateStatusResponse.builder().key(key).exists(false).claimed(false).build();
    }

    public void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String scriptAddress(PlutusScript script) {
        if (cardanoNetwork == CardanoNetwork.MAINNET) {
            return AddressProvider.getEntAddress(script, com.bloxbean.cardano.client.common.model.Networks.mainnet()).toBech32();
        }
        return AddressProvider.getEntAddress(script, com.bloxbean.cardano.client.common.model.Networks.preprod()).toBech32();
    }

    private String resolveScriptAddress(String scriptHash) {
        // Build a minimal enterprise address from the script hash
        byte[] hashBytes = HexUtil.decodeHexString(scriptHash);
        com.bloxbean.cardano.client.address.Credential cred =
                com.bloxbean.cardano.client.address.Credential.fromScript(hashBytes);
        if (cardanoNetwork == CardanoNetwork.MAINNET) {
            return AddressProvider.getEntAddress(cred, com.bloxbean.cardano.client.common.model.Networks.mainnet()).toBech32();
        }
        return AddressProvider.getEntAddress(cred, com.bloxbean.cardano.client.common.model.Networks.preprod()).toBech32();
    }

    /**
     * Fetches the unique UTxO at {@code scriptAddress} that holds exactly 1 of the
     * token with unit {@code policyId + tokenName}.
     */
    private Utxo fetchUtxoByToken(String scriptAddress, String policyId, String tokenNameHex) throws ApiException {
        String unit = policyId + tokenNameHex;
        Optional<Utxo> opt = getCurrentUtxoByUnit(scriptAddress, unit, backendService);
        if (opt.isEmpty()) {
            throw new IllegalStateException("UTxO with unit " + unit + " not found at " + scriptAddress);
        }
        return opt.get();
    }

    /**
     * Scans all UTxOs at the tokenizable script address and finds the node that
     * should immediately precede the new {@code key} in the sorted linked list.
     * <p>
     * Returns {@link PredecessorResult#headPredecessor()} when HEAD is the only
     * valid predecessor — this case cannot be handled in a single transaction.
     */
    private PredecessorResult findPredecessor(
            String scriptAddress, String policyId, String key,
            TokenizableDatum.Head headDatum) throws ApiException {

        // If the list is empty, HEAD is the predecessor (EUTXO conflict).
        if (headDatum.getNext() == null) {
            return PredecessorResult.headPredecessor();
        }

        // If key < first node's key, HEAD is the predecessor (EUTXO conflict).
        if (key.compareTo(headDatum.getNext()) < 0) {
            return PredecessorResult.headPredecessor();
        }

        // Load all node UTxOs and build an in-memory map key → (Utxo, NodeDatum).
        List<Utxo> allUtxos = getAllUtxosAtAddress(scriptAddress);

        java.util.Map<String, Utxo> utxoByKey = new java.util.HashMap<>();
        java.util.Map<String, TokenizableDatum.Node> datumByKey = new java.util.HashMap<>();
        for (Utxo u : allUtxos) {
            if (u.getInlineDatum() == null) continue;
            try {
                TokenizableDatum d = TokenizableDatum.fromInlineDatum(u.getInlineDatum());
                if (d instanceof TokenizableDatum.Node node) {
                    utxoByKey.put(node.getKey(), u);
                    datumByKey.put(node.getKey(), node);
                }
            } catch (Exception ignored) {
                // Skip UTxOs with unrecognized datums
            }
        }

        // Walk the linked list from the first node to find the insertion point.
        String currentKey = headDatum.getNext();
        while (currentKey != null) {
            TokenizableDatum.Node currentNode = datumByKey.get(currentKey);
            if (currentNode == null) {
                throw new IllegalStateException("Linked list is inconsistent: node '" + currentKey + "' missing from UTxO set.");
            }
            String nextKey = currentNode.getNext();
            // Insert between currentNode and nextKey if key > currentKey AND (nextKey==null OR key < nextKey)
            if (key.compareTo(currentKey) > 0 && (nextKey == null || key.compareTo(nextKey) < 0)) {
                return PredecessorResult.nodePredecessor(utxoByKey.get(currentKey), currentNode, nextKey);
            }
            if (key.equals(currentKey)) {
                throw new IllegalArgumentException("Key '" + key + "' already exists in the list.");
            }
            currentKey = nextKey;
        }

        // Should not happen if comparisons above are correct, but be safe.
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
            PlutusScript tokenizableScript, String tokenizableAddress,
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
                .issuer(HexUtil.encodeHexString(userCredential))
                .extra("{\"uverify_template_id\":\"tokenizableCertificate\"}")
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

        TokenizableDatum.Node newNodeDatum = TokenizableDatum.Node.builder()
                .key(req.getKey())
                .next(null)
                .owner(req.getOwnerPubKeyHash())
                .assetName(req.getAssetName())
                .redeemed(false)
                .build();

        PlutusData insertMintRedeemer = ConstrPlutusData.of(1,
                BytesPlutusData.of(HexUtil.decodeHexString(req.getKey())));

        // Proxy cert token: proves a UVerify cert is present in this tx
        Asset certToken = Asset.builder()
                .name("0x" + req.getKey())
                .value(BigInteger.ONE)
                .build();

        ScriptTx tx = new ScriptTx()
                .readFrom(bootstrapUtxo, proxyStateRef, stateLibraryUtxo, proxyLibraryUtxo, headUtxo)
                .collectFrom(userUtxo)
                .withdraw(stateRewardAddress, BigInteger.ZERO, mintStateRedeemer.toPlutusData())
                .mintAsset(proxyContract, List.of(stateToken), mintProxyRedeemer,
                        proxyScriptAddress, stateDatum.toPlutusData())
                .mintAsset(tokenizableScript, List.of(nodeToken), insertMintRedeemer,
                        tokenizableAddress, newNodeDatum.toPlutusData())
                .mintAsset(tokenizableScript, List.of(certToken), insertMintRedeemer);

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
                tx.payToAddress(receiverAddress, Amount.lovelace(BigInteger.valueOf(feePerReceiver)));
            }
        }
    }

    // ── PredecessorResult ─────────────────────────────────────────────────────

    /** Encapsulates the result of the predecessor-search algorithm. */
    private static class PredecessorResult {
        private final boolean headPredecessor;
        private final Utxo utxo;
        private final TokenizableDatum.Node node;
        private final String successorKey;

        private PredecessorResult(boolean headPredecessor, Utxo utxo,
                                  TokenizableDatum.Node node, String successorKey) {
            this.headPredecessor = headPredecessor;
            this.utxo = utxo;
            this.node = node;
            this.successorKey = successorKey;
        }

        static PredecessorResult headPredecessor() {
            return new PredecessorResult(true, null, null, null);
        }

        static PredecessorResult nodePredecessor(Utxo utxo, TokenizableDatum.Node node, String successorKey) {
            return new PredecessorResult(false, utxo, node, successorKey);
        }

        boolean isHeadPredecessor() { return headPredecessor; }
        Utxo getUtxo() { return utxo; }
        String getSuccessorKey() { return successorKey; }

        /** Returns the updated predecessor datum (with {@code next} pointing at {@code newKey}). */
        PlutusData buildUpdatedPredecessorDatum(String newKey) {
            return node.withNext(newKey).toPlutusData();
        }
    }
}
