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

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.yaci.core.model.Redeemer;
import com.bloxbean.cardano.yaci.core.model.RedeemerTag;
import com.bloxbean.cardano.yaci.core.model.TransactionOutput;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.events.EventMetadata;
import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import io.uverify.backend.dto.BuildStatus;
import io.uverify.backend.dto.ProxyInitResponse;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.FeeReceiverEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.enums.UVerifyScriptPurpose;
import io.uverify.backend.model.*;
import io.uverify.backend.model.converter.ProxyRedeemerConverter;
import io.uverify.backend.util.CardanoUtils;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;
import static io.uverify.backend.util.ValidatorUtils.*;

@Service
@Slf4j
public class CardanoBlockchainService {
    @Autowired
    private final BootstrapDatumService bootstrapDatumService;
    @Autowired
    private final StateDatumService stateDatumService;
    @Autowired
    private final UVerifyCertificateService uVerifyCertificateService;
    @Autowired
    private final ValidatorHelper validatorHelper;
    private final CardanoNetwork network;
    private final Address serviceUserAddress;
    @Autowired
    private final LibraryService libraryService;
    private BackendService backendService;

    @Autowired
    public CardanoBlockchainService(@Value("${cardano.service.user.address}") String serviceUserAddress,
                                    @Value("${cardano.backend.service.type}") String cardanoBackendServiceType,
                                    @Value("${cardano.backend.blockfrost.baseUrl}") String blockfrostBaseUrl,
                                    @Value("${cardano.backend.blockfrost.projectId}") String blockfrostProjectId,
                                    @Value("${cardano.network}") String network,
                                    UVerifyCertificateService uVerifyCertificateService,
                                    ValidatorHelper validatorHelper,
                                    BootstrapDatumService bootstrapDatumService, StateDatumService stateDatumService,
                                    LibraryService libraryService
    ) {
        this.bootstrapDatumService = bootstrapDatumService;
        this.stateDatumService = stateDatumService;
        this.uVerifyCertificateService = uVerifyCertificateService;
        this.network = CardanoNetwork.valueOf(network);
        this.serviceUserAddress = new Address(serviceUserAddress);
        this.validatorHelper = validatorHelper;
        this.libraryService = libraryService;

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
    }

    public Result<String> submitTransaction(Transaction transaction, Account signer) throws CborSerializationException, ApiException {
        Transaction signedTransaction = TransactionSigner.INSTANCE.sign(transaction, signer.hdKeyPair());
        return submitTransaction(signedTransaction);
    }

    public Result<String> submitTransaction(Transaction transaction) throws CborSerializationException, ApiException {
        return backendService.getTransactionService().submitTransaction(transaction.serialize());
    }

    public void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    public Transaction updateStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String bootstrapTokenName) throws ApiException {
        Optional<StateDatumEntity> stateDatumEntity = Optional.empty();
        if (bootstrapTokenName.isEmpty()) {
            List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(address, 2);
            if (stateDatumEntities.size() == 1) {
                stateDatumEntity = Optional.of(stateDatumEntities.get(0));
            } else if (stateDatumEntities.size() > 1) {
                stateDatumEntity = Optional.of(stateDatumService.selectCheapestStateDatum(stateDatumEntities));
            }
        } else {
            stateDatumEntity = stateDatumService.findByUserAndBootstrapToken(address, bootstrapTokenName);
        }

        if (stateDatumEntity.isEmpty()) {
            throw new IllegalArgumentException("No applicable state datum found for user account");
        }

        StateDatumEntity stateDatum = stateDatumEntity.get();
        if (stateDatum.getVersion() == 1) {
            throw new IllegalArgumentException("No applicable state datum found for user account");
        } else {
            return updateStateDatum(address, stateDatum, uVerifyCertificates);
        }
    }

    public Transaction persistUVerifyCertificates(String address, List<UVerifyCertificate> uVerifyCertificate) throws ApiException, CborSerializationException {
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(address, 2);
        if (stateDatumEntities.isEmpty()) {
            log.debug("No state datum found for address " + address + ". Start forking a new state datum.");
            return forkProxyStateDatum(address, uVerifyCertificate);
        } else {
            StateDatumEntity stateDatumEntity = stateDatumService.selectCheapestStateDatum(stateDatumEntities);
            boolean needsToPayFee = stateDatumEntity.getCountdown() % stateDatumEntity.getBootstrapDatum().getFeeInterval() == 0;
            if (needsToPayFee) {
                log.debug("Fee required for updating state datum. Checking for better conditions.");
                Address userAddress = new Address(address);
                Optional<byte[]> optionalUserAccountCredential = userAddress.getPaymentCredentialHash();

                if (optionalUserAccountCredential.isEmpty()) {
                    throw new IllegalArgumentException("Invalid Cardano payment address");
                }
                Optional<BootstrapDatum> bootstrapDatum = bootstrapDatumService.selectCheapestBootstrapDatum(optionalUserAccountCredential.get());

                if (bootstrapDatum.isEmpty()) {
                    return updateStateDatum(address, stateDatumEntity, uVerifyCertificate);
                }

                double bootstrapFeeEveryHundredTransactions = (100.0 / bootstrapDatum.get().getFeeInterval()) * bootstrapDatum.get().getFee();
                double stateFeeEveryHundredTransactions = (100.0 / stateDatumEntity.getBootstrapDatum().getFeeInterval()) * stateDatumEntity.getBootstrapDatum().getFee();
                if (bootstrapFeeEveryHundredTransactions < stateFeeEveryHundredTransactions) {
                    log.debug("Forking state datum with better conditions.");
                    return forkProxyStateDatum(address, uVerifyCertificate, bootstrapDatum.get().getTokenName());
                } else {
                    log.debug("Updating state datum with current conditions.");
                    return updateStateDatum(address, stateDatumEntity, uVerifyCertificate);
                }
            } else {
                log.debug("No fee required for updating state datum");
                return updateStateDatum(address, stateDatumEntity, uVerifyCertificate);
            }
        }
    }

    public ProxyInitResponse initProxyContract() throws ApiException, CborSerializationException {
        ProxyInitResponse proxyInitResponse = new ProxyInitResponse();
        proxyInitResponse.setStatus(BuildStatus.builder()
                .code(BuildStatusCode.ERROR)
                .build());
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        String serviceAddress = this.serviceUserAddress.getAddress();
        PlutusScript stateContract;
        String existingProxyTxHash = validatorHelper.getProxyTransactionHash();
        if (existingProxyTxHash.equals("")) {
            Result<List<Utxo>> result = this.backendService.getUtxoService().getUtxos(serviceAddress, 100, 1);
            List<Utxo> utxos = result.getValue();

            Utxo utxo = utxos.get(0);
            PlutusScript proxyContract = ValidatorUtils.getUverifyProxyContract(utxo);
            String proxyScriptHash = ValidatorUtils.validatorToScriptHash(proxyContract);

            stateContract = ValidatorUtils.getUVerifyStateContract(proxyScriptHash, ValidatorUtils.getProxyStateTokenName(utxo.getTxHash(), utxo.getOutputIndex()));
            String stateScriptHash = ValidatorUtils.validatorToScriptHash(stateContract);

            Optional<byte[]> paymentCredentialHash = this.serviceUserAddress.getPaymentCredentialHash();
            if (paymentCredentialHash.isEmpty()) {
                throw new IllegalArgumentException("Invalid service user address");
            }

            ProxyDatum proxyDatum = ProxyDatum.builder()
                    .ScriptOwner(Hex.encodeHexString(paymentCredentialHash.get()))
                    .ScriptPointer(stateScriptHash)
                    .build();

            String tokenName = ValidatorUtils.getProxyStateTokenName(utxo.getTxHash(), utxo.getOutputIndex());
            Asset authToken = Asset.builder()
                    .name("0x" + tokenName)
                    .value(BigInteger.valueOf(1))
                    .build();

            String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, network.toCardaoNetwork()).toBech32();
            PlutusData initProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.ADMIN_ACTION);

            String proxyUnit = proxyContract.getPolicyId() + tokenName;

            ScriptTx tx = new ScriptTx()
                    .collectFrom(List.of(utxo))
                    .mintAsset(proxyContract, authToken, initProxyRedeemer)
                    .payToContract(proxyScriptAddress, List.of(Amount.asset(proxyUnit, 1)), proxyDatum.toPlutusData())
                    .withChangeAddress(serviceAddress);

            Transaction transaction = quickTxBuilder.compose(tx)
                    .feePayer(serviceAddress)
                    .withRequiredSigners(serviceUserAddress)
                    .build();

            proxyInitResponse.setUnsignedProxyTransaction(transaction.serializeToHex());
            proxyInitResponse.setProxyOutputIndex(utxo.getOutputIndex());
            proxyInitResponse.setProxyTxHash(utxo.getTxHash());
            proxyInitResponse.setStatus(BuildStatus.builder()
                    .code(BuildStatusCode.SUCCESS)
                    .build());
        } else {
            log.info("It seems that there was a previous proxy deployment. Reusing the existing proxy contract.");
            proxyInitResponse.setStatus(BuildStatus.builder()
                    .code(BuildStatusCode.ERROR)
                    .message("Existing proxy contract found with transaction hash " + existingProxyTxHash + ". Please use this transaction hash to build the transaction.")
                    .build());
        }

        return proxyInitResponse;
    }

    public Transaction updateStateDatum(String address, StateDatumEntity stateDatum, List<UVerifyCertificate> uVerifyCertificates) throws ApiException {
        Address userAddress = new Address(address);

        PlutusScript uverifyProxyContract = validatorHelper.getParameterizedProxyContract();
        String proxyScriptHash = validatorToScriptHash(uverifyProxyContract);
        PlutusScript uverifyStateContract = validatorHelper.getParameterizedUVerifyStateContract();

        String unit = proxyScriptHash + stateDatum.getId();
        Optional<Utxo> optionalUtxo = ValidatorUtils.getUtxoByTransactionAndUnit(stateDatum.getTransactionId(), unit, backendService);

        if (optionalUtxo.isEmpty()) {
            throw new IllegalArgumentException("State token not found in transaction outputs");
        }

        Utxo utxo = optionalUtxo.get();
        String proxyScriptAddress = AddressProvider.getEntAddress(uverifyProxyContract, fromCardanoNetwork(network)).toBech32();

        StateDatum nextStateDatum = StateDatum.fromPreviousStateDatum(utxo.getInlineDatum());
        nextStateDatum.setCertificates(uVerifyCertificates);

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = validatorHelper.resolveProxyStateUtxo(backendService);
        } catch (Exception exception) {
            log.error("Unable to fetch proxy state utxo: " + exception.getMessage());
            return null;
        }

        StateRedeemer redeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.UPDATE_STATE)
                .certificates(uVerifyCertificates)
                .build();

        PlutusData spendProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);
        String stateScriptRewardAddress = AddressProvider.getRewardAddress(uverifyStateContract, fromCardanoNetwork(network)).toBech32();

        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();
        Utxo proxyLibraryUtxo = libraryService.getProxyLibraryUtxo();

        ScriptTx updateStateTokenTx = new ScriptTx()
                .readFrom(proxyStateUtxo, stateLibraryUtxo, proxyLibraryUtxo)
                .collectFrom(utxo, spendProxyRedeemer)
                .payToContract(proxyScriptAddress, utxo.getAmount(), nextStateDatum.toPlutusData())
                .withdraw(stateScriptRewardAddress, BigInteger.valueOf(0), redeemer.toPlutusData());

        BootstrapDatumEntity bootstrapDatum = stateDatum.getBootstrapDatum();
        if (stateDatum.getCountdown() % bootstrapDatum.getFeeInterval() == 0) {
            long fee = bootstrapDatum.getFee() / bootstrapDatum.getFeeReceivers().size();
            for (FeeReceiverEntity feeReceiver : bootstrapDatum.getFeeReceivers()) {
                Credential credential = Credential.fromKey(feeReceiver.getCredential());
                Address feeReceiverAddress = AddressProvider.getEntAddress(credential, fromCardanoNetwork(network));
                updateStateTokenTx.payToAddress(feeReceiverAddress.getAddress(), Amount.lovelace(BigInteger.valueOf(fee)));
            }
        }

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        long validFrom = currentSlot - 10;
        long transactionTtl = currentSlot + 600; // 10 minutes

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        return quickTxBuilder.compose(updateStateTokenTx)
                .validFrom(validFrom)
                .validTo(transactionTtl)
                .collateralPayer(address)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .preBalanceTx((context, txn) -> {
                    log.info("Pre balance callback invoked for transaction " + txn + " in context " + context);
                })
                .postBalanceTx((context, txn) -> {
                    log.info("Post balance callback invoked for transaction " + txn + " in context " + context);
                })
                .feePayer(address)
                .withRequiredSigners(userAddress)
                .withReferenceScripts(uverifyStateContract, uverifyProxyContract)
                .build();
    }

    public Transaction invalidateState(Address userAddress, String transactionId) throws ApiException {
        Optional<byte[]> optionalUserPaymentCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        byte[] userAccountCredential = optionalUserPaymentCredential.get();
        String unit = getMintStateTokenHash(network) + Hex.encodeHexString(userAccountCredential);

        Optional<Utxo> optionalUtxo = ValidatorUtils.getUtxoByTransactionAndUnit(transactionId, unit, backendService);

        if (optionalUtxo.isEmpty()) {
            throw new IllegalArgumentException("State token not found in transaction outputs");
        }

        Utxo utxo = optionalUtxo.get();
        PlutusScript updateTestStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getUpdateStateTokenCode(network), PlutusVersion.v3);
        PlutusScript mintStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getMintStateTokenCode(network), PlutusVersion.v3);

        Asset userStateToken = Asset.builder()
                .name("0x" + Hex.encodeHexString(userAccountCredential))
                .value(BigInteger.valueOf(-1))
                .build();

        ScriptTx updateStateTokenTx = new ScriptTx()
                .collectFrom(utxo, PlutusData.unit())
                .attachSpendingValidator(updateTestStateTokenScript)
                .mintAsset(mintStateTokenScript, List.of(userStateToken), PlutusData.unit());

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        long validFrom = currentSlot - 10;
        long transactionTtl = currentSlot + 600; // 10 minutes

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        return quickTxBuilder.compose(updateStateTokenTx)
                .validFrom(validFrom)
                .validTo(transactionTtl)
                .feePayer(userAddress.getAddress())
                .withRequiredSigners(userAddress)
                .build();
    }

    public void processUVerifyProxyTx(StateRedeemer stateRedeemer, String txHash, String blockHash, long blockNumber,
                                      long blockTime, long slot, String inlineDatum) {
        if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.MINT_STATE)) {
            StateDatum stateDatum = StateDatum.fromUtxoDatum(inlineDatum);
            stateDatum.setCertificates(stateRedeemer.getCertificates());
            Optional<StateDatumEntity> optionalStateDatumEntity = stateDatumService.findById(stateDatum.getId());

            final StateDatumEntity stateDatumEntity;
            if (optionalStateDatumEntity.isEmpty()) {
                BootstrapDatumEntity bootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(stateDatum.getBootstrapDatumName(), 2)
                        .orElseThrow(() -> new IllegalArgumentException("Bootstrap datum not found"));
                stateDatumEntity = StateDatumEntity.fromStateDatum(stateDatum, txHash, bootstrapDatumEntity, slot);
                stateDatumService.updateStateDatum(stateDatumEntity, slot);
            } else {
                stateDatumEntity = optionalStateDatumEntity.get();
                stateDatumEntity.setTransactionId(txHash);
                stateDatumEntity.setCountdown(stateDatum.getCountdown());
                stateDatumService.updateStateDatum(stateDatumEntity, slot);
            }

            List<UVerifyCertificate> uVerifyCertificates = stateDatum.getCertificates();
            List<UVerifyCertificateEntity> uVerifyCertificatesEntities = new ArrayList<>();
            for (UVerifyCertificate uVerifyCertificate : uVerifyCertificates) {
                UVerifyCertificateEntity uVerifyCertificateEntity = UVerifyCertificateEntity.fromUVerifyCertificate(uVerifyCertificate);
                uVerifyCertificateEntity.setSlot(slot);
                uVerifyCertificateEntity.setStateDatum(stateDatumEntity);
                uVerifyCertificateEntity.setTransactionId(txHash);
                uVerifyCertificateEntity.setBlockHash(blockHash);
                uVerifyCertificateEntity.setBlockNumber(blockNumber);
                uVerifyCertificateEntity.setCreationTime(new Date(blockTime * 1000));
                uVerifyCertificatesEntities.add(uVerifyCertificateEntity);
            }
            uVerifyCertificateService.saveAllCertificates(uVerifyCertificatesEntities);
        } else if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.MINT_BOOTSTRAP)) {
            BootstrapDatumEntity bootstrapDatumEntity = BootstrapDatumEntity.fromInlineDatum(inlineDatum, txHash, slot, network);
            bootstrapDatumService.save(bootstrapDatumEntity);
        } else if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.BURN_BOOTSTRAP)) {
            BootstrapDatumEntity bootstrapDatumEntity = BootstrapDatumEntity.fromInlineDatum(inlineDatum, txHash, slot, network);
            bootstrapDatumService.markAsInvalid(bootstrapDatumEntity.getTokenName(), slot);
        } else if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.UPDATE_STATE)) {
            StateDatum stateDatum = StateDatum.fromUtxoDatum(inlineDatum);
            stateDatum.setCertificates(stateRedeemer.getCertificates());
            Optional<StateDatumEntity> optionalStateDatumEntity = stateDatumService.findById(stateDatum.getId());
            if (optionalStateDatumEntity.isEmpty()) {
                log.error("State datum not found for id " + stateDatum.getId() + " in transaction " + txHash);
                return;
            }
            final StateDatumEntity stateDatumEntity = optionalStateDatumEntity.get();
            stateDatumEntity.setTransactionId(txHash);
            stateDatumEntity.setCountdown(stateDatum.getCountdown());
            stateDatumService.updateStateDatum(stateDatumEntity, slot);

            List<UVerifyCertificate> uVerifyCertificates = stateDatum.getCertificates();
            List<UVerifyCertificateEntity> uVerifyCertificatesEntities = new ArrayList<>();
            for (UVerifyCertificate uVerifyCertificate : uVerifyCertificates) {
                UVerifyCertificateEntity uVerifyCertificateEntity = UVerifyCertificateEntity.fromUVerifyCertificate(uVerifyCertificate);
                uVerifyCertificateEntity.setSlot(slot);
                uVerifyCertificateEntity.setStateDatum(stateDatumEntity);
                uVerifyCertificateEntity.setTransactionId(txHash);
                uVerifyCertificateEntity.setBlockHash(blockHash);
                uVerifyCertificateEntity.setBlockNumber(blockNumber);
                uVerifyCertificateEntity.setCreationTime(new Date(blockTime * 1000));
                uVerifyCertificatesEntities.add(uVerifyCertificateEntity);
            }
            uVerifyCertificateService.saveAllCertificates(uVerifyCertificatesEntities);
        }
    }

    public List<AddressUtxo> processAddressUtxos(List<AddressUtxo> addressUtxos) {
        List<AddressUtxo> processedUtxos = new ArrayList<>();
        for (AddressUtxo addressUtxo : addressUtxos) {
            if (includesBootstrapToken(addressUtxo, network)) {
                if (isMintingTransaction(addressUtxo, ValidatorUtils.getMintOrBurnAuthTokenHash(network))) {
                    BootstrapDatumEntity bootstrapDatumEntity = BootstrapDatumEntity.fromAddressUtxo(addressUtxo, network);
                    bootstrapDatumService.save(bootstrapDatumEntity);
                    processedUtxos.add(addressUtxo);
                } else if (isBurningTransaction(addressUtxo, ValidatorUtils.getMintOrBurnAuthTokenHash(network))) {
                    bootstrapDatumService.markAsInvalid(getBootstrapTokenName(addressUtxo, network), addressUtxo.getSlot());
                    processedUtxos.add(addressUtxo);
                } else {
                    throw new IllegalArgumentException("Invalid bootstrap token transaction amount!");
                }
            } else if (includesStateToken(addressUtxo, network)) {
                StateDatum stateDatum = StateDatum.fromLegacyUtxoDatum(addressUtxo.getInlineDatum());
                Optional<StateDatumEntity> optionalStateDatumEntity = stateDatumService.findByAddressUtxo(addressUtxo);

                if (isBurningTransaction(addressUtxo, ValidatorUtils.getMintStateTokenHash(network))) {
                    stateDatumService.invalidateStateDatum(stateDatum.getId(), addressUtxo.getSlot());
                    processedUtxos.add(addressUtxo);
                } else {
                    final StateDatumEntity stateDatumEntity;
                    if (optionalStateDatumEntity.isEmpty()) {
                        BootstrapDatumEntity bootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(stateDatum.getBootstrapDatumName(), 1)
                                .orElseThrow(() -> new IllegalArgumentException("Bootstrap datum not found"));
                        stateDatumEntity = StateDatumEntity.fromAddressUtxo(addressUtxo, bootstrapDatumEntity);
                        stateDatumService.updateStateDatum(stateDatumEntity, addressUtxo.getSlot());
                    } else {
                        stateDatumEntity = optionalStateDatumEntity.get();
                        stateDatumEntity.setTransactionId(addressUtxo.getTxHash());
                        stateDatumEntity.setCountdown(stateDatum.getCountdown());
                        stateDatumService.updateStateDatum(stateDatumEntity, addressUtxo.getSlot());
                    }

                    List<UVerifyCertificate> uVerifyCertificates = stateDatum.getCertificates();
                    List<UVerifyCertificateEntity> uVerifyCertificatesEntities = new ArrayList<>();
                    for (UVerifyCertificate uVerifyCertificate : uVerifyCertificates) {
                        UVerifyCertificateEntity uVerifyCertificateEntity = UVerifyCertificateEntity.fromUVerifyCertificate(uVerifyCertificate);
                        uVerifyCertificateEntity.setSlot(addressUtxo.getSlot());
                        uVerifyCertificateEntity.setStateDatum(stateDatumEntity);
                        uVerifyCertificateEntity.setTransactionId(addressUtxo.getTxHash());
                        uVerifyCertificateEntity.setBlockHash(addressUtxo.getBlockHash());
                        uVerifyCertificateEntity.setBlockNumber(addressUtxo.getBlockNumber());
                        uVerifyCertificateEntity.setCreationTime(new Date(addressUtxo.getBlockTime() * 1000));
                        uVerifyCertificatesEntities.add(uVerifyCertificateEntity);
                    }
                    uVerifyCertificateService.saveAllCertificates(uVerifyCertificatesEntities);
                    processedUtxos.add(addressUtxo);
                }
            }
        }
        return processedUtxos;
    }

    public void handleRollbackToSlot(long slot) {
        bootstrapDatumService.undoInvalidationBeforeSlot(slot);
        uVerifyCertificateService.deleteAllCertificatesAfterSlot(slot);
        stateDatumService.undoInvalidationBeforeSlot(slot);
        stateDatumService.handleRollbackToSlot(slot);
        bootstrapDatumService.deleteAllAfterSlot(slot);
    }

    public Transaction invalidateStates(Address userAddress, List<String> transactionIds) throws ApiException {
        Optional<byte[]> optionalUserPaymentCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        byte[] userAccountCredential = optionalUserPaymentCredential.get();
        String unit = getMintStateTokenHash(network) + Hex.encodeHexString(userAccountCredential);

        PlutusScript updateTestStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getUpdateStateTokenCode(network), PlutusVersion.v3);
        PlutusScript mintStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getMintStateTokenCode(network), PlutusVersion.v3);
        Asset userStateToken = Asset.builder()
                .name("0x" + Hex.encodeHexString(userAccountCredential))
                .value(BigInteger.valueOf(-1))
                .build();

        List<ScriptTx> invalidateStateTransactions = new ArrayList<>();
        for (String transactionId : transactionIds) {
            Optional<Utxo> optionalUtxo = ValidatorUtils.getUtxoByTransactionAndUnit(transactionId, unit, backendService);
            if (optionalUtxo.isEmpty()) {
                throw new IllegalArgumentException("State token not found in transaction outputs");
            }
            Utxo utxo = optionalUtxo.get();

            invalidateStateTransactions.add(new ScriptTx()
                    .collectFrom(utxo, PlutusData.unit())
                    .attachSpendingValidator(updateTestStateTokenScript)
                    .mintAsset(mintStateTokenScript, List.of(userStateToken), PlutusData.unit()));
        }

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        long validFrom = currentSlot - 10;
        long transactionTtl = currentSlot + 600; // 10 minutes

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        return quickTxBuilder.compose(invalidateStateTransactions.toArray(new ScriptTx[0]))
                .validFrom(validFrom)
                .validTo(transactionTtl)
                .feePayer(userAddress.getAddress())
                .withRequiredSigners(userAddress)
                .build();
    }

    public Long getLatestSlot() throws ApiException {
        return CardanoUtils.getLatestSlot(backendService);
    }

    public Transaction mintProxyBootstrapDatum(BootstrapDatum bootstrapDatum) {
        if (bootstrapDatumService.bootstrapDatumAlreadyExists(bootstrapDatum.getTokenName(), 1)) {
            throw new IllegalArgumentException("Bootstrap datum with name " + bootstrapDatum.getTokenName() + " already exists");
        }

        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Asset authorizationToken = Asset.builder()
                .name(bootstrapDatum.getTokenName())
                .value(BigInteger.ONE)
                .build();

        String stateScriptRewardAddress = validatorHelper.getStateContractAddress();
        String proxyScriptAddress = validatorHelper.getProxyContractAddress();

        StateRedeemer redeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.MINT_BOOTSTRAP)
                .certificates(Collections.emptyList())
                .build();

        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = validatorHelper.resolveProxyStateUtxo(backendService);
        } catch (Exception exception) {
            log.error("Unable to fetch proxy state utxo: " + exception.getMessage());
            return null;
        }

        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();

        ScriptTx scriptTx = new ScriptTx()
                .readFrom(proxyStateUtxo, stateLibraryUtxo)
                .withdraw(stateScriptRewardAddress, BigInteger.valueOf(0), redeemer.toPlutusData())
                .mintAsset(proxyContract, List.of(authorizationToken),
                        mintProxyRedeemer, proxyScriptAddress,
                        bootstrapDatum.toPlutusData());

        return quickTxBuilder.compose(scriptTx)
                .withReferenceScripts(stateContract)
                .feePayer(serviceUserAddress.getAddress())
                .withRequiredSigners(serviceUserAddress)
                .build();
    }

    public Transaction forkProxyStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates) throws ApiException, CborSerializationException {
        Address userAddress = new Address(address);
        Optional<byte[]> optionalUserAccountCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserAccountCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        Optional<BootstrapDatum> optionalBootstrapDatum = bootstrapDatumService.selectCheapestBootstrapDatum(optionalUserAccountCredential.get());

        if (optionalBootstrapDatum.isEmpty()) {
            throw new IllegalArgumentException("No applicable bootstrap datum found for user account");
        }

        return forkProxyStateDatum(address, uVerifyCertificates, optionalBootstrapDatum.get().getTokenName());
    }

    public Transaction forkProxyStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String bootstrapTokenName) throws ApiException, CborSerializationException {
        Optional<BootstrapDatumEntity> optionalBootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(bootstrapTokenName, 2);

        if (optionalBootstrapDatumEntity.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap datum with name " + bootstrapTokenName + " not found");
        }

        BootstrapDatumEntity bootstrapDatumEntity = optionalBootstrapDatumEntity.get();

        Address userAddress = new Address(address);
        Optional<byte[]> optionalUserAccountCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserAccountCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        byte[] userAccountCredential = optionalUserAccountCredential.get();
        Result<TxContentUtxo> transactionUtxosResult = backendService.getTransactionService().getTransactionUtxos(bootstrapDatumEntity.getTransactionId());

        PlutusScript proxyContract = validatorHelper.getParameterizedProxyContract();
        String unit = proxyContract.getPolicyId() + HexUtil.encodeHexString(bootstrapTokenName.getBytes());
        Optional<TxContentUtxoOutputs> optionalTxContentUtxoOutput = transactionUtxosResult.getValue().getOutputs().stream().filter(utxo -> utxo.getAmount().stream().anyMatch(amount -> amount.getUnit().equals(unit))).findFirst();

        if (optionalTxContentUtxoOutput.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap token or datum not found in transaction outputs");
        }

        TxContentUtxoOutputs txContentUtxoOutput = optionalTxContentUtxoOutput.get();
        Utxo utxo = txContentUtxoOutput.toUtxos(bootstrapDatumEntity.getTransactionId());

        StateDatum stateDatum = StateDatum.fromBootstrapDatum(utxo.getInlineDatum(), userAccountCredential);
        stateDatum.setCertificateDataHash(uVerifyCertificates);
        stateDatum.setCountdown(stateDatum.getCountdown() - 1);

        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        List<Utxo> userUtxos = utxoSupplier.getAll(address);

        if (userUtxos.isEmpty()) {
            throw new IllegalArgumentException("No UTXOs found for user address");
        }

        Utxo userUtxo = userUtxos.get(0);
        String index = HexUtil.encodeHexString(ByteBuffer.allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) userUtxo.getOutputIndex())
                .array());

        stateDatum.setId(DigestUtils.sha256Hex(HexUtil.decodeHexString(userUtxo.getTxHash() + index)));

        Asset userStateToken = Asset.builder()
                .name("0x" + stateDatum.getId())
                .value(BigInteger.ONE)
                .build();

        PlutusScript stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        String stateScriptRewardAddress = AddressProvider.getRewardAddress(stateContract, fromCardanoNetwork(network)).toBech32();
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, fromCardanoNetwork(network)).toBech32();

        StateRedeemer redeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.MINT_STATE)
                .certificates(uVerifyCertificates)
                .build();
        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = validatorHelper.resolveProxyStateUtxo(backendService);
        } catch (Exception exception) {
            log.error("Unable to fetch proxy state utxo: " + exception.getMessage());
            return null;
        }

        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();

        ScriptTx scriptTransaction = new ScriptTx()
                .readFrom(utxo, proxyStateUtxo, stateLibraryUtxo)
                .collectFrom(userUtxo)
                .withdraw(stateScriptRewardAddress, BigInteger.valueOf(0), redeemer.toPlutusData())
                .mintAsset(proxyContract, List.of(userStateToken),
                        mintProxyRedeemer, proxyScriptAddress,
                        stateDatum.toPlutusData());

        if (bootstrapDatumEntity.getFee() > 0) {
            long fee = bootstrapDatumEntity.getFee() / stateDatum.getFeeReceivers().size();
            for (byte[] paymentCredential : stateDatum.getFeeReceivers()) {
                Credential credential = Credential.fromKey(paymentCredential);
                Address feeReceiverAddress = AddressProvider.getEntAddress(credential, fromCardanoNetwork(network));
                scriptTransaction.payToAddress(feeReceiverAddress.getAddress(), Amount.lovelace(BigInteger.valueOf(fee)));
            }
        }

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        long currentSlot = CardanoUtils.getLatestSlot(backendService);
        long validFrom = currentSlot - 10;
        long transactionTtl = currentSlot + 600; // 10 minutes

        return quickTxBuilder.compose(scriptTransaction)
                .validFrom(validFrom)
                .validTo(transactionTtl)
                .withReferenceScripts(stateContract)
                .collateralPayer(address)
                .feePayer(address)
                .withRequiredSigners(userAddress)
                .build();
    }

    private Optional<StateRedeemer> findWithdrawalRedeemer(Map<String, BigInteger> withdrawals, List<Redeemer> redeemers, Address address) {
        if (withdrawals == null || withdrawals.size() == 0)
            return Optional.empty();

        ArrayList<String> rewardAddresses = new ArrayList<>(withdrawals.keySet().stream().map(String::toLowerCase).toList());
        Collections.sort(rewardAddresses);

        int redeemerIndex = rewardAddresses.indexOf(HexUtil.encodeHexString(address.getBytes()).toLowerCase());
        Optional<Redeemer> optionalRedeemer = redeemers.stream().filter(redeemer -> redeemer.getIndex() == redeemerIndex).findFirst();
        if (optionalRedeemer.isEmpty()) {
            return Optional.empty();
        }
        Redeemer redeemer = optionalRedeemer.get();
        try {
            PlutusData deserialize = PlutusData.deserialize(HexUtil.decodeHexString(redeemer.getData().getCbor()));
            return Optional.of(StateRedeemer.fromPlutusData(deserialize));
        } catch (Exception e) {
            log.error("Error deserializing StateRedeemer from redeemer data cbor: {}", redeemer.getData().getCbor(), e);
            return Optional.empty();
        }
    }

    private boolean signedByAddress(com.bloxbean.cardano.yaci.helper.model.Transaction transaction, String address) {
        return transaction.getWitnesses().getVkeyWitnesses().stream()
                .anyMatch(vkeyWitness -> {
                    byte[] vkeyHash = Blake2bUtil.blake2bHash224(HexUtil.decodeHexString(vkeyWitness.getKey()));
                    Optional<byte[]> paymentCredentialHash = new Address(address).getPaymentCredentialHash();
                    return paymentCredentialHash.isPresent() && Arrays.equals(vkeyHash, paymentCredentialHash.get());
                });
    }

    public void processTransactionEvent(TransactionEvent transactionEvent) {
        EventMetadata metadata = transactionEvent.getMetadata();
        if (metadata.isParallelMode()) {
            return;
        }

        for (com.bloxbean.cardano.yaci.helper.model.Transaction transaction : transactionEvent.getTransactions()) {
            if (transaction.isInvalid())
                continue;

            final String libraryContractAddress = libraryService.getLibraryAddress();
            boolean hasLibraryInteraction = transaction.getBody().getOutputs() != null && transaction.getBody().getOutputs().stream().anyMatch(utxo -> utxo.getAddress().equals(libraryContractAddress));

            if (!hasLibraryInteraction && (transaction.getWitnesses().getRedeemers() == null || transaction.getWitnesses().getRedeemers().size() == 0))
                continue;

            final String proxyTxHash = validatorHelper.getProxyTransactionHash();
            final Integer proxyOutputIndex = validatorHelper.getProxyOutputIndex();

            final PlutusScript uverifyProxyContract = getUverifyProxyContract(proxyTxHash, proxyOutputIndex);
            final String uverifyProxyScriptHash = ValidatorUtils.validatorToScriptHash(uverifyProxyContract);

            String hexStateContractAddress = HexUtil.encodeHexString(new Address(validatorHelper.getStateContractAddress()).getBytes());

            List<com.bloxbean.cardano.yaci.core.model.Amount> mints = transaction.getBody().getMint();

            Optional<com.bloxbean.cardano.yaci.core.model.Amount> maybeMint = mints.stream().filter(amount -> amount.getPolicyId().equals(uverifyProxyScriptHash)).findFirst();
            boolean hasStateContractInteraction = transaction.getBody().getWithdrawals() != null && transaction.getBody().getWithdrawals().containsKey(hexStateContractAddress);

            Optional<byte[]> optionalUserPaymentCredential = serviceUserAddress.getPaymentCredentialHash();

            if (optionalUserPaymentCredential.isEmpty()) {
                throw new IllegalArgumentException("Invalid Cardano payment address");
            }

            if (maybeMint.isPresent()) {
                com.bloxbean.cardano.yaci.core.model.Amount mint = maybeMint.get();

                List<String> distinctPolicies = transaction.getBody().getMint().stream()
                        .map(com.bloxbean.cardano.yaci.core.model.Amount::getPolicyId)
                        .distinct().toList();

                int redeemerIndex = distinctPolicies.indexOf(mint.getPolicyId());
                Optional<Redeemer> optionalRedeemer = transaction.getWitnesses().getRedeemers().stream().filter(redeemer -> redeemer.getTag().equals(RedeemerTag.Mint) && redeemer.getIndex() == redeemerIndex).findFirst();

                if (optionalRedeemer.isEmpty()) {
                    log.warn("No redeemer found for minting UVerify Proxy Token in tx: {}", transaction.getTxHash());
                    continue;
                }

                Redeemer redeemer = optionalRedeemer.get();
                ProxyRedeemer proxyRedeemer = new ProxyRedeemerConverter().deserialize(redeemer.getData().getCbor());

                Map<String, BigInteger> withdrawals = transaction.getBody().getWithdrawals();
                List<Redeemer> rewardRedeemers = transaction.getWitnesses().getRedeemers().stream().filter(potentialRedeemer -> potentialRedeemer.getTag().equals(RedeemerTag.Reward)).toList();
                String stateContractAddress = validatorHelper.getStateContractAddress();
                Optional<StateRedeemer> stateRedeemer = findWithdrawalRedeemer(withdrawals, rewardRedeemers, new Address(stateContractAddress));

                if (stateRedeemer.isEmpty()) {
                    log.warn("No StateRedeemer found for withdrawal in tx: {}", transaction.getTxHash());
                    continue;
                }

                if (proxyRedeemer.equals(ProxyRedeemer.USER_ACTION)) {
                    Optional<TransactionOutput> optionalUtxo = transaction.getBody().getOutputs().stream().filter(utxo -> utxo.getAmounts().stream()
                            .anyMatch(amount -> amount.getPolicyId() != null &&
                                    amount.getPolicyId().equals(uverifyProxyScriptHash))).findFirst();
                    if (optionalUtxo.isEmpty()) {
                        log.warn("No UTXO found with UVerify Proxy Token in tx: {}", transaction.getTxHash());
                        continue;
                    }
                    TransactionOutput utxo = optionalUtxo.get();
                    processUVerifyProxyTx(stateRedeemer.get(), transaction.getTxHash(),
                            metadata.getBlockHash(), transaction.getBlockNumber(), metadata.getBlockTime(), metadata.getSlot(), utxo.getInlineDatum());
                }
            } else if (hasStateContractInteraction) {
                final String proxyContractAddress = validatorHelper.getProxyContractAddress();
                Optional<TransactionOutput> maybeTransactionOutput = transaction.getBody().getOutputs().stream().filter(txOutput -> txOutput.getAddress().equals(proxyContractAddress)
                        && txOutput.getAmounts().stream().anyMatch(amount -> amount.getPolicyId() != null && amount.getPolicyId().equals(uverifyProxyScriptHash))).findFirst();

                if (maybeTransactionOutput.isEmpty()) {
                    log.warn("No output found with UVerify Proxy Token in tx: {}", transaction.getTxHash());
                    continue;
                }

                Map<String, BigInteger> withdrawals = transaction.getBody().getWithdrawals();
                List<Redeemer> rewardRedeemers = transaction.getWitnesses().getRedeemers().stream().filter(potentialRedeemer -> potentialRedeemer.getTag().equals(RedeemerTag.Reward)).toList();

                String stateContractAddress = validatorHelper.getStateContractAddress();
                Optional<StateRedeemer> stateRedeemer = findWithdrawalRedeemer(withdrawals, rewardRedeemers, new Address(stateContractAddress));

                if (stateRedeemer.isEmpty()) {
                    log.warn("No StateRedeemer found for withdrawal in tx: {}", transaction.getTxHash());
                    continue;
                }

                TransactionOutput transactionOutput = maybeTransactionOutput.get();
                processUVerifyProxyTx(stateRedeemer.get(), transaction.getTxHash(),
                        metadata.getBlockHash(), transaction.getBlockNumber(), metadata.getBlockTime(), metadata.getSlot(), transactionOutput.getInlineDatum());
            }

            if (hasLibraryInteraction) {
                boolean signedByServiceUser = signedByAddress(transaction, serviceUserAddress.getAddress());
                if (signedByServiceUser) {
                    ArrayList<com.bloxbean.cardano.yaci.helper.model.Utxo> utxos = new ArrayList<>(transaction.getUtxos().stream()
                            .filter(utxo -> utxo.getAddress().equals(libraryContractAddress)).toList());
                    if (utxos.size() > 0) {
                        libraryService.deployToLibrary(utxos, transaction.getSlot());
                    }
                }
            }
        }
    }
}
