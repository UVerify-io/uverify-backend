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
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.model.RedeemerTag;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.script.domain.TxScript;
import io.uverify.backend.dto.ProxyInitResponse;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.FeeReceiverEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.enums.UVerifyScriptPurpose;
import io.uverify.backend.model.*;
import io.uverify.backend.model.converter.ProxyRedeemerConverter;
import io.uverify.backend.util.CardanoUtils;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
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
    private BackendService backendService;

    @Autowired
    public CardanoBlockchainService(@Value("${cardano.service.user.address}") String serviceUserAddress,
                                    @Value("${cardano.backend.service.type}") String cardanoBackendServiceType,
                                    @Value("${cardano.backend.blockfrost.baseUrl}") String blockfrostBaseUrl,
                                    @Value("${cardano.backend.blockfrost.projectId}") String blockfrostProjectId,
                                    @Value("${cardano.network}") String network,
                                    UVerifyCertificateService uVerifyCertificateService,
                                    ValidatorHelper validatorHelper,
                                    BootstrapDatumService bootstrapDatumService, StateDatumService stateDatumService
    ) {
        this.bootstrapDatumService = bootstrapDatumService;
        this.stateDatumService = stateDatumService;
        this.uVerifyCertificateService = uVerifyCertificateService;
        this.network = CardanoNetwork.valueOf(network);
        this.serviceUserAddress = new Address(serviceUserAddress);
        this.validatorHelper = validatorHelper;

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
        return updateStateDatum(address, uVerifyCertificates, bootstrapTokenName, validatorHelper.getProxyTransactionHash(), validatorHelper.getProxyOutputIndex());
    }

    public Transaction updateStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String bootstrapTokenName, String proxyTxHash, int proxyOutputIndex) throws ApiException {
        Optional<StateDatumEntity> stateDatumEntity = Optional.empty();
        if (bootstrapTokenName.isEmpty()) {
            List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(address);
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
            return updateStateDatum(address, stateDatum, uVerifyCertificates, proxyTxHash, proxyOutputIndex);
        }
    }

    public Transaction persistUVerifyCertificates(String address, List<UVerifyCertificate> uVerifyCertificate) throws ApiException, CborSerializationException {
        String proxyTxHash = validatorHelper.getProxyTransactionHash();
        int proxyOutputIndex = validatorHelper.getProxyOutputIndex();

        return persistUVerifyCertificates(address, uVerifyCertificate, proxyTxHash, proxyOutputIndex);
    }

    public Transaction persistUVerifyCertificates(String address, List<UVerifyCertificate> uVerifyCertificate, String proxyTxHash, Integer proxyOutputIndex) throws ApiException, CborSerializationException {
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(address);
        if (stateDatumEntities.isEmpty()) {
            log.debug("No state datum found for address " + address + ". Start forking a new state datum.");
            return forkProxyStateDatum(address, uVerifyCertificate, proxyTxHash, proxyOutputIndex);
        } else {
            StateDatumEntity stateDatumEntity = stateDatumService.selectCheapestStateDatum(stateDatumEntities);
            boolean needsToPayFee = (stateDatumEntity.getCountdown() + 1) % stateDatumEntity.getBootstrapDatum().getFeeInterval() == 0;
            if (needsToPayFee) {
                log.debug("Fee required for updating state datum. Checking for better conditions.");
                Address userAddress = new Address(address);
                Optional<byte[]> optionalUserAccountCredential = userAddress.getPaymentCredentialHash();

                if (optionalUserAccountCredential.isEmpty()) {
                    throw new IllegalArgumentException("Invalid Cardano payment address");
                }
                Optional<BootstrapDatum> bootstrapDatum = bootstrapDatumService.selectCheapestBootstrapDatum(optionalUserAccountCredential.get());

                if (bootstrapDatum.isEmpty()) {
                    return updateStateDatum(address, stateDatumEntity, uVerifyCertificate, proxyTxHash, proxyOutputIndex);
                }

                double bootstrapFeeEveryHundredTransactions = (100.0 / bootstrapDatum.get().getFeeInterval()) * bootstrapDatum.get().getFee();
                double stateFeeEveryHundredTransactions = (100.0 / stateDatumEntity.getBootstrapDatum().getFeeInterval()) * stateDatumEntity.getBootstrapDatum().getFee();
                if (bootstrapFeeEveryHundredTransactions < stateFeeEveryHundredTransactions) {
                    log.debug("Forking state datum with better conditions.");
                    return forkProxyStateDatum(address, uVerifyCertificate, bootstrapDatum.get().getTokenName(), proxyTxHash, proxyOutputIndex);
                } else {
                    log.debug("Updating state datum with current conditions.");
                    return updateStateDatum(address, stateDatumEntity, uVerifyCertificate, proxyTxHash, proxyOutputIndex);
                }
            } else {
                log.debug("No fee required for updating state datum");
                return updateStateDatum(address, stateDatumEntity, uVerifyCertificate, proxyTxHash, proxyOutputIndex);
            }
        }
    }

    public ProxyInitResponse initProxyContract() throws ApiException, CborSerializationException {
        ProxyInitResponse proxyInitResponse = new ProxyInitResponse();
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

            String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, Networks.preprod()).toBech32();
            PlutusData initProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.ADMIN_ACTION);

            String proxyUnit = proxyContract.getPolicyId() + tokenName;

            ScriptTx tx = new ScriptTx()
                    .collectFrom(List.of(utxo))
                    .mintAsset(proxyContract, authToken, initProxyRedeemer)
                    .payToContract(proxyScriptAddress, List.of(Amount.asset(proxyUnit, 1)), proxyDatum.toPlutusData(), stateContract)
                    .withChangeAddress(serviceAddress);

            Transaction transaction = quickTxBuilder.compose(tx)
                    .feePayer(serviceAddress)
                    .withRequiredSigners(serviceUserAddress)
                    .build();

            proxyInitResponse.setUnsignedProxyTransaction(transaction.serializeToHex());
            proxyInitResponse.setProxyOutputIndex(utxo.getOutputIndex());
            proxyInitResponse.setProxyTxHash(utxo.getTxHash());
        } else {
            log.info("It seems that there was a previous proxy deployment. Reusing the existing proxy contract.");
            stateContract = validatorHelper.getParameterizedUVerifyStateContract();
        }

        String stateScriptRewardAddress = AddressProvider.getRewardAddress(stateContract, Networks.preprod()).toBech32();
        Tx registerStakeAddressTx = new Tx()
                .from(serviceAddress)
                .registerStakeAddress(stateScriptRewardAddress);

        Transaction stakeRegistrationTx = quickTxBuilder.compose(registerStakeAddressTx)
                .feePayer(serviceAddress)
                .withRequiredSigners(serviceUserAddress)
                .build();

        proxyInitResponse.setUnsignedStakeRegistrationTransaction(stakeRegistrationTx.serializeToHex());
        return proxyInitResponse;
    }

    public Transaction updateStateDatum(String address, StateDatumEntity stateDatum, List<UVerifyCertificate> uVerifyCertificates, String proxyTxHash, int proxyOutputIndex) throws ApiException {
        Address userAddress = new Address(address);

        PlutusScript uverifyProxyContract = getUverifyProxyContract(proxyTxHash, proxyOutputIndex);
        String proxyScriptHash = validatorToScriptHash(uverifyProxyContract);
        PlutusScript uverifyStateContract = getUVerifyStateContract(proxyTxHash, proxyOutputIndex);

        String unit = proxyScriptHash + stateDatum.getId();
        Optional<Utxo> optionalUtxo = ValidatorUtils.getUtxoByTransactionAndUnit(stateDatum.getTransactionId(), unit, backendService);

        if (optionalUtxo.isEmpty()) {
            throw new IllegalArgumentException("State token not found in transaction outputs");
        }

        Utxo utxo = optionalUtxo.get();
        String proxyScriptAddress = AddressProvider.getEntAddress(uverifyProxyContract, fromCardanoNetwork(network)).toBech32();

        StateDatum nextStateDatum = StateDatum.fromStateDatum(utxo.getInlineDatum());
        nextStateDatum.setCertificates(uVerifyCertificates);

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = getProxyStateUtxo(proxyTxHash, proxyOutputIndex);
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

        ScriptTx updateStateTokenTx = new ScriptTx()
                .readFrom(proxyStateUtxo)
                .collectFrom(utxo, spendProxyRedeemer)
                .attachSpendingValidator(uverifyProxyContract)
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
                .feePayer(address)
                .withRequiredSigners(userAddress)
                .withReferenceScripts(uverifyStateContract)
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

    public List<TxScript> processTxScripts(List<TxScript> txScripts) throws CborDeserializationException {
        return processTxScripts(txScripts, validatorHelper.getProxyTransactionHash(), validatorHelper.getProxyOutputIndex());
    }

    private Optional<TxScript> findScriptTxByProxyRedeemer(List<TxScript> txScripts, ProxyRedeemer proxyRedeemer, com.bloxbean.cardano.yaci.core.model.RedeemerTag purpose) {
        if (proxyRedeemer.equals(ProxyRedeemer.USER_ACTION)) {
            if (purpose.equals(RedeemerTag.Mint) || purpose.equals(RedeemerTag.Spend)) {
                return txScripts.stream().filter(txScript -> txScript.getPurpose().equals(RedeemerTag.Reward)).findFirst();
            }
        }
        return Optional.empty();
    }

    public List<TxScript> processTxScripts(List<TxScript> txScripts, String proxyTxHash, int proxyOutputIndex) throws CborDeserializationException {
        PlutusScript uverifyProxyContract = getUverifyProxyContract(proxyTxHash, proxyOutputIndex);
        final String uverifyProxyScriptHash = ValidatorUtils.validatorToScriptHash(uverifyProxyContract);
        PlutusScript uverifyStateContract = getUVerifyStateContract(uverifyProxyScriptHash, getProxyStateTokenName(proxyTxHash, proxyOutputIndex));
        final String uverifyStateScriptHash = ValidatorUtils.validatorToScriptHash(uverifyStateContract);

        List<TxScript> processedTxScripts = new ArrayList<>();
        ArrayList<TxScript> uverifyStateTransactions = new ArrayList<>(txScripts.stream().filter(txScript -> txScript.getScriptHash().equals(uverifyStateScriptHash)).toList());
        for (TxScript txScript : txScripts) {
            if (txScript.getScriptHash().equals(uverifyProxyScriptHash)) {
                ProxyRedeemer proxyRedeemer = new ProxyRedeemerConverter().deserialize(HexUtil.decodeHexString(txScript.getRedeemerCbor()));

                Optional<TxScript> maybeUverifyStateTx = findScriptTxByProxyRedeemer(uverifyStateTransactions, proxyRedeemer, txScript.getPurpose());
                maybeUverifyStateTx.ifPresent(uverifyStateTransactions::remove);

                if (maybeUverifyStateTx.isPresent()) {
                    TxScript uverifyStateTx = maybeUverifyStateTx.get();
                    PlutusData deserialize = PlutusData.deserialize(HexUtil.decodeHexString(uverifyStateTx.getRedeemerCbor()));
                    StateRedeemer stateRedeemer = StateRedeemer.fromPlutusData(deserialize);

                    if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.MINT_STATE) ||
                            stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.RENEW_STATE)) {
                        StateDatum stateDatum = StateDatum.fromTxScript(txScript, stateRedeemer);
                        Optional<StateDatumEntity> optionalStateDatumEntity = stateDatumService.findById(stateDatum.getId());

                        if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.RENEW_STATE)) {
                            stateDatumService.invalidateStateDatum(stateDatum.getId(), uverifyStateTx.getSlot());
                            processedTxScripts.add(uverifyStateTx);
                        }

                        final StateDatumEntity stateDatumEntity;
                        if (optionalStateDatumEntity.isEmpty()) {
                            BootstrapDatumEntity bootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(stateDatum.getBootstrapDatumName())
                                    .orElseThrow(() -> new IllegalArgumentException("Bootstrap datum not found"));
                            stateDatumEntity = StateDatumEntity.fromStateDatum(stateDatum, uverifyStateTx.getTxHash(), bootstrapDatumEntity, uverifyStateTx.getSlot());
                            stateDatumService.save(stateDatumEntity);
                        } else {
                            stateDatumEntity = optionalStateDatumEntity.get();
                            stateDatumEntity.setTransactionId(uverifyStateTx.getTxHash());
                            stateDatumEntity.setCountdown(stateDatum.getCountdown());
                            stateDatumService.updateStateDatum(stateDatumEntity, uverifyStateTx.getSlot());
                        }

                        List<UVerifyCertificate> uVerifyCertificates = stateDatum.getCertificates();
                        List<UVerifyCertificateEntity> uVerifyCertificatesEntities = new ArrayList<>();
                        for (UVerifyCertificate uVerifyCertificate : uVerifyCertificates) {
                            UVerifyCertificateEntity uVerifyCertificateEntity = UVerifyCertificateEntity.fromUVerifyCertificate(uVerifyCertificate);
                            uVerifyCertificateEntity.setSlot(uverifyStateTx.getSlot());
                            uVerifyCertificateEntity.setStateDatum(stateDatumEntity);
                            uVerifyCertificateEntity.setTransactionId(uverifyStateTx.getTxHash());
                            uVerifyCertificateEntity.setBlockHash(uverifyStateTx.getBlockHash());
                            uVerifyCertificateEntity.setBlockNumber(uverifyStateTx.getBlockNumber());
                            uVerifyCertificateEntity.setCreationTime(new Date(uverifyStateTx.getBlockTime() * 1000));
                            uVerifyCertificatesEntities.add(uVerifyCertificateEntity);
                        }
                        uVerifyCertificateService.saveAllCertificates(uVerifyCertificatesEntities);
                        processedTxScripts.add(uverifyStateTx);
                    } else if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.MINT_BOOTSTRAP)) {
                        BootstrapDatumEntity bootstrapDatumEntity = BootstrapDatumEntity.fromTxScript(txScript, network);
                        bootstrapDatumService.save(bootstrapDatumEntity);
                        processedTxScripts.add(uverifyStateTx);
                    } else if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.BURN_BOOTSTRAP)) {
                        BootstrapDatumEntity bootstrapDatumEntity = BootstrapDatumEntity.fromTxScript(uverifyStateTx, network);
                        bootstrapDatumService.markAsInvalid(bootstrapDatumEntity.getTokenName(), uverifyStateTx.getSlot());
                        processedTxScripts.add(uverifyStateTx);
                    } else if (stateRedeemer.getPurpose().equals(UVerifyScriptPurpose.UPDATE_STATE)) {
                        StateDatum stateDatum = StateDatum.fromTxScript(txScript, stateRedeemer);
                        Optional<StateDatumEntity> optionalStateDatumEntity = stateDatumService.findById(stateDatum.getId());
                        if (optionalStateDatumEntity.isEmpty()) {
                            log.error("State datum not found for id " + stateDatum.getId() + " in transaction " + uverifyStateTx.getTxHash());
                            continue;
                        }
                        final StateDatumEntity stateDatumEntity = optionalStateDatumEntity.get();
                        stateDatumEntity.setTransactionId(uverifyStateTx.getTxHash());
                        stateDatumEntity.setCountdown(stateDatum.getCountdown());
                        stateDatumService.updateStateDatum(stateDatumEntity, uverifyStateTx.getSlot());

                        List<UVerifyCertificate> uVerifyCertificates = stateDatum.getCertificates();
                        List<UVerifyCertificateEntity> uVerifyCertificatesEntities = new ArrayList<>();
                        for (UVerifyCertificate uVerifyCertificate : uVerifyCertificates) {
                            UVerifyCertificateEntity uVerifyCertificateEntity = UVerifyCertificateEntity.fromUVerifyCertificate(uVerifyCertificate);
                            uVerifyCertificateEntity.setSlot(uverifyStateTx.getSlot());
                            uVerifyCertificateEntity.setStateDatum(stateDatumEntity);
                            uVerifyCertificateEntity.setTransactionId(uverifyStateTx.getTxHash());
                            uVerifyCertificateEntity.setBlockHash(uverifyStateTx.getBlockHash());
                            uVerifyCertificateEntity.setBlockNumber(uverifyStateTx.getBlockNumber());
                            uVerifyCertificateEntity.setCreationTime(new Date(uverifyStateTx.getBlockTime() * 1000));
                            uVerifyCertificatesEntities.add(uVerifyCertificateEntity);
                        }
                        uVerifyCertificateService.saveAllCertificates(uVerifyCertificatesEntities);
                        processedTxScripts.add(uverifyStateTx);
                    }
                }
            }
        }
        return processedTxScripts;
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
                        BootstrapDatumEntity bootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(stateDatum.getBootstrapDatumName())
                                .orElseThrow(() -> new IllegalArgumentException("Bootstrap datum not found"));
                        stateDatumEntity = StateDatumEntity.fromAddressUtxo(addressUtxo, bootstrapDatumEntity);
                        stateDatumService.save(stateDatumEntity);
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

    public Utxo getProxyStateUtxo(String proxyTxHash, int proxyOutputIndex) throws DecoderException, ApiException {
        String stateTokenName = getProxyStateTokenName(proxyTxHash, proxyOutputIndex);
        PlutusScript proxyContract = ValidatorUtils.getUverifyProxyContract(proxyTxHash, proxyOutputIndex);
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, Networks.preprod()).toBech32();
        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(proxyContract);
        String stateTokenUnit = proxyScriptHash + stateTokenName;

        Result<List<Utxo>> stateUtxRequest = backendService.getUtxoService().getUtxos(proxyScriptAddress, stateTokenUnit, 1, 1);
        return stateUtxRequest.getValue().get(0);
    }

    public Transaction mintProxyBootstrapDatum(BootstrapDatum bootstrapDatum) {
        return mintProxyBootstrapDatum(bootstrapDatum, validatorHelper.getProxyTransactionHash(), validatorHelper.getProxyOutputIndex());
    }

    public Transaction mintProxyBootstrapDatum(BootstrapDatum bootstrapDatum, String proxyTxHash, int proxyOutputIndex) {
        if (bootstrapDatumService.bootstrapDatumAlreadyExists(bootstrapDatum.getTokenName())) {
            throw new IllegalArgumentException("Bootstrap datum with name " + bootstrapDatum.getTokenName() + " already exists");
        }

        PlutusScript proxyContract = ValidatorUtils.getUverifyProxyContract(proxyTxHash, proxyOutputIndex);
        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(proxyContract);

        PlutusScript stateContract = ValidatorUtils.getUVerifyStateContract(proxyScriptHash, getProxyStateTokenName(proxyTxHash, proxyOutputIndex));

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Asset authorizationToken = Asset.builder()
                .name(bootstrapDatum.getTokenName())
                .value(BigInteger.ONE)
                .build();

        String stateScriptRewardAddress = AddressProvider.getRewardAddress(stateContract, fromCardanoNetwork(network)).toBech32();
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, fromCardanoNetwork(network)).toBech32();

        StateRedeemer redeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.MINT_BOOTSTRAP)
                .certificates(Collections.emptyList())
                .build();

        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = getProxyStateUtxo(proxyTxHash, proxyOutputIndex);
        } catch (Exception exception) {
            log.error("Unable to fetch proxy state utxo: " + exception.getMessage());
            return null;
        }

        ScriptTx scriptTx = new ScriptTx()
                .readFrom(proxyStateUtxo)
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

    public Transaction forkProxyStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String proxyTxHash, int proxyOutputIndex) throws ApiException, CborSerializationException {
        Address userAddress = new Address(address);
        Optional<byte[]> optionalUserAccountCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserAccountCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        Optional<BootstrapDatum> optionalBootstrapDatum = bootstrapDatumService.selectCheapestBootstrapDatum(optionalUserAccountCredential.get());

        if (optionalBootstrapDatum.isEmpty()) {
            throw new IllegalArgumentException("No applicable bootstrap datum found for user account");
        }

        return forkProxyStateDatum(address, uVerifyCertificates, optionalBootstrapDatum.get().getTokenName(), proxyTxHash, proxyOutputIndex);
    }

    public Transaction forkProxyStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String bootstrapTokenName, String proxyTxHash, int proxyOutputIndex) throws ApiException, CborSerializationException {
        Optional<BootstrapDatumEntity> optionalBootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(bootstrapTokenName);

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

        String unit = getUverifyProxyContract(proxyTxHash, proxyOutputIndex).getPolicyId() + HexUtil.encodeHexString(bootstrapTokenName.getBytes());
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

        PlutusScript proxyContract = ValidatorUtils.getUverifyProxyContract(proxyTxHash, proxyOutputIndex);
        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(proxyContract);

        PlutusScript stateContract = ValidatorUtils.getUVerifyStateContract(proxyScriptHash, getProxyStateTokenName(proxyTxHash, proxyOutputIndex));

        String stateScriptRewardAddress = AddressProvider.getRewardAddress(stateContract, fromCardanoNetwork(network)).toBech32();
        String proxyScriptAddress = AddressProvider.getEntAddress(proxyContract, fromCardanoNetwork(network)).toBech32();

        StateRedeemer redeemer = StateRedeemer.builder()
                .purpose(UVerifyScriptPurpose.MINT_STATE)
                .certificates(uVerifyCertificates)
                .build();
        PlutusData mintProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.USER_ACTION);

        Utxo proxyStateUtxo;
        try {
            proxyStateUtxo = getProxyStateUtxo(proxyTxHash, proxyOutputIndex);
        } catch (Exception exception) {
            log.error("Unable to fetch proxy state utxo: " + exception.getMessage());
            return null;
        }

        ScriptTx scriptTransaction = new ScriptTx()
                .readFrom(utxo, proxyStateUtxo)
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
}
