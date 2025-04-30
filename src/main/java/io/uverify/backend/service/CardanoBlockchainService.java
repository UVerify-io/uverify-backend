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
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.FeeReceiverEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.model.StateDatum;
import io.uverify.backend.model.UVerifyCertificate;
import io.uverify.backend.util.CardanoUtils;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
                                    BootstrapDatumService bootstrapDatumService, StateDatumService stateDatumService
    ) {
        this.bootstrapDatumService = bootstrapDatumService;
        this.stateDatumService = stateDatumService;
        this.uVerifyCertificateService = uVerifyCertificateService;
        this.network = CardanoNetwork.valueOf(network);
        this.serviceUserAddress = new Address(serviceUserAddress);

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

    public Transaction updateStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates) throws ApiException {
        return updateStateDatum(address, uVerifyCertificates, "");
    }

    public Transaction updateStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String bootstrapTokenName) throws ApiException {
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
        return updateStateDatum(address, stateDatum, uVerifyCertificates);
    }

    public Transaction updateStateDatum(String address, StateDatumEntity stateDatum, List<UVerifyCertificate> uVerifyCertificates) throws ApiException {
        Address userAddress = new Address(address);
        Optional<byte[]> optionalUserPaymentCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserPaymentCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        byte[] userAccountCredential = optionalUserPaymentCredential.get();

        String unit = getMintStateTokenHash(network) + Hex.encodeHexString(userAccountCredential);

        Optional<Utxo> optionalUtxo = ValidatorUtils.getUtxoByTransactionAndUnit(stateDatum.getTransactionId(), unit, backendService);

        if (optionalUtxo.isEmpty()) {
            throw new IllegalArgumentException("State token not found in transaction outputs");
        }

        Utxo utxo = optionalUtxo.get();

        PlutusScript updateTestStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getUpdateStateTokenCode(network), PlutusVersion.v3);
        String updateTestStateScriptAddress = AddressProvider.getEntAddress(updateTestStateTokenScript, fromCardanoNetwork(network)).toBech32();

        StateDatum nextStateDatum = StateDatum.fromPreviousStateDatum(utxo.getInlineDatum());
        nextStateDatum.setUVerifyCertificates(uVerifyCertificates);
        ScriptTx updateStateTokenTx = new ScriptTx()
                .collectFrom(utxo, PlutusData.unit())
                .payToContract(updateTestStateScriptAddress, utxo.getAmount(), nextStateDatum.toPlutusData(network))
                .attachSpendingValidator(updateTestStateTokenScript);

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
                .build();
    }

    public Transaction persistUVerifyCertificates(String address, List<UVerifyCertificate> uVerifyCertificate) throws ApiException {
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(address);
        if (stateDatumEntities.isEmpty()) {
            log.debug("No state datum found for address " + address + ". Start forking a new state datum.");
            return forkStateDatum(address, uVerifyCertificate);
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
                    return forkStateDatum(address, uVerifyCertificate, bootstrapDatum.get().getTokenName());
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

    public Transaction forkStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates) throws ApiException {
        Address userAddress = new Address(address);
        Optional<byte[]> optionalUserAccountCredential = userAddress.getPaymentCredentialHash();

        if (optionalUserAccountCredential.isEmpty()) {
            throw new IllegalArgumentException("Invalid Cardano payment address");
        }

        Optional<BootstrapDatum> optionalBootstrapDatum = bootstrapDatumService.selectCheapestBootstrapDatum(optionalUserAccountCredential.get());

        if (optionalBootstrapDatum.isEmpty()) {
            throw new IllegalArgumentException("No applicable bootstrap datum found for user account");
        }

        return forkStateDatum(address, uVerifyCertificates, optionalBootstrapDatum.get().getTokenName());
    }

    public Transaction forkStateDatum(String address, List<UVerifyCertificate> uVerifyCertificates, String bootstrapTokenName) throws ApiException {
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

        String unit = getMintOrBurnAuthTokenHash(network) + HexUtil.encodeHexString(bootstrapTokenName.getBytes());
        Optional<TxContentUtxoOutputs> optionalTxContentUtxoOutput = transactionUtxosResult.getValue().getOutputs().stream().filter(utxo -> utxo.getAmount().stream().anyMatch(amount -> amount.getUnit().equals(unit))).findFirst();

        if (optionalTxContentUtxoOutput.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap token or datum not found in transaction outputs");
        }

        TxContentUtxoOutputs txContentUtxoOutput = optionalTxContentUtxoOutput.get();
        Utxo utxo = txContentUtxoOutput.toUtxos(bootstrapDatumEntity.getTransactionId());

        StateDatum stateDatum = StateDatum.fromBootstrapDatum(utxo.getInlineDatum(), userAccountCredential);
        stateDatum.setUVerifyCertificates(uVerifyCertificates);
        stateDatum.setCountdown(stateDatum.getCountdown() - 1);

        PlutusScript mintStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getMintStateTokenCode(network), PlutusVersion.v3);
        Asset userStateToken = Asset.builder()
                .name("0x" + Hex.encodeHexString(userAccountCredential))
                .value(BigInteger.ONE)
                .build();

        PlutusScript updateTestStateTokenScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getUpdateStateTokenCode(network), PlutusVersion.v3);
        String updateTestStateScriptAddress = AddressProvider.getEntAddress(updateTestStateTokenScript, fromCardanoNetwork(network)).toBech32();

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

        ScriptTx scriptTransaction = new ScriptTx()
                .readFrom(utxo)
                .collectFrom(userUtxo)
                .mintAsset(mintStateTokenScript, List.of(userStateToken), PlutusData.unit(), updateTestStateScriptAddress, stateDatum.toPlutusData(network));

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
                .collateralPayer(address)
                .feePayer(address)
                .withRequiredSigners(userAddress)
                .build();
    }

    public Transaction invalidateBootstrapDatum(String bootstrapTokenName) throws ApiException {
        Optional<BootstrapDatumEntity> optionalBootstrapDatumEntity = bootstrapDatumService.getBootstrapDatum(bootstrapTokenName);

        if (optionalBootstrapDatumEntity.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap datum with name " + bootstrapTokenName + " doesn't exists");
        }

        BootstrapDatumEntity bootstrapDatum = optionalBootstrapDatumEntity.get();
        Result<TxContentUtxo> transactionUtxosResult = backendService.getTransactionService().getTransactionUtxos(bootstrapDatum.getTransactionId());

        String unit = getMintOrBurnAuthTokenHash(network) + HexUtil.encodeHexString(bootstrapTokenName.getBytes());
        Optional<TxContentUtxoOutputs> optionalTxContentUtxoOutput = transactionUtxosResult.getValue().getOutputs().stream().filter(utxo -> utxo.getAmount().stream().anyMatch(amount -> amount.getUnit().equals(unit))).findFirst();

        if (optionalTxContentUtxoOutput.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap token or datum not found in transaction outputs");
        }

        TxContentUtxoOutputs txContentUtxoOutput = optionalTxContentUtxoOutput.get();
        Utxo utxo = txContentUtxoOutput.toUtxos(bootstrapDatum.getTransactionId());

        PlutusScript authorizationScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getMintOrBurnAuthTokenCode(network), PlutusVersion.v3);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Asset authorizationToken = Asset.builder()
                .name(bootstrapDatum.getTokenName())
                .value(BigInteger.valueOf(-1))
                .build();


        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        List<Utxo> userUtxos = utxoSupplier.getAll(serviceUserAddress.getAddress());

        if (userUtxos.isEmpty()) {
            throw new IllegalArgumentException("No UTXOs found for user address");
        }

        Utxo userUtxo = userUtxos.get(0);

        ScriptTx scriptTx = new ScriptTx()
                .attachSpendingValidator(authorizationScript)
                .collectFrom(utxo, PlutusData.unit())
                .collectFrom(userUtxo)
                .mintAsset(authorizationScript, List.of(authorizationToken), PlutusData.unit());

        return quickTxBuilder.compose(scriptTx)
                .feePayer(serviceUserAddress.getAddress())
                .withRequiredSigners(serviceUserAddress)
                .build();
    }

    public Transaction initializeBootstrapDatum(BootstrapDatum bootstrapDatum) throws ApiException {
        if (bootstrapDatumService.bootstrapDatumAlreadyExists(bootstrapDatum.getTokenName())) {
            throw new IllegalArgumentException("Bootstrap datum with name " + bootstrapDatum.getTokenName() + " already exists");
        }

        PlutusScript authorizationScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(getMintOrBurnAuthTokenCode(network), PlutusVersion.v3);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Asset authorizationToken = Asset.builder()
                .name(bootstrapDatum.getTokenName())
                .value(BigInteger.ONE)
                .build();

        String scriptAddress = AddressProvider.getEntAddress(authorizationScript, fromCardanoNetwork(network)).toBech32();
        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(authorizationScript, List.of(authorizationToken), PlutusData.unit(), scriptAddress, bootstrapDatum.toPlutusData(network));

        return quickTxBuilder.compose(scriptTx)
                .feePayer(serviceUserAddress.getAddress())
                .withRequiredSigners(serviceUserAddress)
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

    public void processAddressUtxos(List<AddressUtxo> addressUtxos) {
        for (AddressUtxo addressUtxo : addressUtxos) {
            if (includesBootstrapToken(addressUtxo, network)) {
                if (isMintingTransaction(addressUtxo, ValidatorUtils.getMintOrBurnAuthTokenHash(network))) {
                    BootstrapDatumEntity bootstrapDatumEntity = BootstrapDatumEntity.fromAddressUtxo(addressUtxo, network);
                    bootstrapDatumService.save(bootstrapDatumEntity);
                } else if (isBurningTransaction(addressUtxo, ValidatorUtils.getMintOrBurnAuthTokenHash(network))) {
                    bootstrapDatumService.markAsInvalid(getBootstrapTokenName(addressUtxo, network), addressUtxo.getSlot());
                } else {
                    throw new IllegalArgumentException("Invalid bootstrap token transaction amount!");
                }
            } else if (includesStateToken(addressUtxo, network)) {
                StateDatum stateDatum = StateDatum.fromUtxoDatum(addressUtxo.getInlineDatum());
                Optional<StateDatumEntity> optionalStateDatumEntity = stateDatumService.findByAddressUtxo(addressUtxo);

                if (isBurningTransaction(addressUtxo, ValidatorUtils.getMintStateTokenHash(network))) {
                    stateDatumService.invalidateStateDatum(stateDatum.getId(), addressUtxo.getSlot());
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

                    List<UVerifyCertificate> uVerifyCertificates = stateDatum.getUVerifyCertificates();
                    List<UVerifyCertificateEntity> uVerifyCertificatesEntities = new ArrayList<>();
                    for (UVerifyCertificate uVerifyCertificate : uVerifyCertificates) {
                        UVerifyCertificateEntity uVerifyCertificateEntity = UVerifyCertificateEntity.fromUVerifyCertificate(uVerifyCertificate);
                        uVerifyCertificateEntity.setSlot(addressUtxo.getSlot());
                        uVerifyCertificateEntity.setStateDatum(stateDatumEntity);
                        uVerifyCertificateEntity.setTransactionId(addressUtxo.getTxHash());
                        uVerifyCertificateEntity.setOutputIndex(addressUtxo.getOutputIndex());
                        uVerifyCertificateEntity.setBlockHash(addressUtxo.getBlockHash());
                        uVerifyCertificateEntity.setBlockNumber(addressUtxo.getBlockNumber());
                        uVerifyCertificateEntity.setCreationTime(new Date(addressUtxo.getBlockTime() * 1000));
                        uVerifyCertificatesEntities.add(uVerifyCertificateEntity);
                    }
                    uVerifyCertificateService.saveAllCertificates(uVerifyCertificatesEntities);
                }
            }
        }
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
}
