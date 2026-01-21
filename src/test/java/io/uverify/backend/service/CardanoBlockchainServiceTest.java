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

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.model.ProxyDatum;
import io.uverify.backend.model.ProxyRedeemer;
import io.uverify.backend.model.UVerifyCertificate;
import io.uverify.backend.model.converter.ProxyRedeemerConverter;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.util.ValidatorUtils;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CardanoBlockchainServiceTest extends CardanoBlockchainTest {

    private String proxyTxHash;
    private Integer proxyOutputIndex;

    @Autowired
    public CardanoBlockchainServiceTest(@Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
                                        @Value("${cardano.test.user.mnemonic}") String testUserMnemonic,
                                        @Value("${cardano.service.fee.receiver.mnemonic}") String feeReceiverMnemonic,
                                        @Value("${cardano.facilitator.user.mnemonic}") String facilitatorMnemonic,
                                        @Value("${cardano.intent.user.mnemonic}") String intentMnemonic,
                                        CardanoBlockchainService cardanoBlockchainService,
                                        StateDatumService stateDatumService,
                                        BootstrapDatumService bootstrapDatumService,
                                        UVerifyCertificateService uVerifyCertificateService,
                                        StateDatumRepository stateDatumRepository,
                                        BootstrapDatumRepository bootstrapDatumRepository,
                                        CertificateRepository certificateRepository,
                                        ExtensionManager extensionManager) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic, intentMnemonic, cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService, stateDatumRepository, bootstrapDatumRepository, certificateRepository, extensionManager, List.of());
    }

    @Test
    @Order(0)
    public void initProxyContract() throws ApiException, CborSerializationException {
        Result<List<Utxo>> result = yaciCardanoContainer.getUtxoService().getUtxos(serviceAccount.baseAddress(), 100, 1);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertFalse(result.getValue().isEmpty());
        List<Utxo> utxos = result.getValue();

        Utxo utxo = utxos.get(0);
        PlutusScript proxyContract = ValidatorUtils.getUverifyProxyContract(utxo);
        String proxyScriptHash = ValidatorUtils.validatorToScriptHash(proxyContract);

        PlutusScript stateContract = ValidatorUtils.getUVerifyStateContract(proxyScriptHash, ValidatorUtils.getProxyStateTokenName(utxo.getTxHash(), utxo.getOutputIndex()));
        String stateScriptHash = ValidatorUtils.validatorToScriptHash(stateContract);

        Optional<byte[]> paymentCredentialHash = serviceAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

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
        String stateScriptRewardAddress = AddressProvider.getRewardAddress(stateContract, Networks.preprod()).toBech32();
        PlutusData initProxyRedeemer = new ProxyRedeemerConverter().toPlutusData(ProxyRedeemer.ADMIN_ACTION);

        String proxyUnit = proxyContract.getPolicyId() + tokenName;

        ScriptTx tx = new ScriptTx()
                .collectFrom(List.of(utxo))
                .mintAsset(proxyContract, authToken, initProxyRedeemer)
                .payToContract(proxyScriptAddress, List.of(Amount.asset(proxyUnit, 1)), proxyDatum.toPlutusData(), stateContract)
                .withChangeAddress(serviceAccount.baseAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(yaciCardanoContainer.getBackendService());
        TxResult txResult = quickTxBuilder.compose(tx)
                .feePayer(serviceAccount.baseAddress())
                .withRequiredSigners(serviceAccount.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(serviceAccount))
                .completeAndWait();

        Assertions.assertTrue(txResult.isSuccessful());

        Tx registerStakeAddressTx = new Tx()
                .from(serviceAccount.baseAddress())
                .registerStakeAddress(stateScriptRewardAddress);

        TxResult stakeRegistrationResult = quickTxBuilder.compose(registerStakeAddressTx)
                .feePayer(serviceAccount.baseAddress())
                .withRequiredSigners(serviceAccount.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(serviceAccount))
                .completeAndWait();

        Assertions.assertTrue(stakeRegistrationResult.isSuccessful());
        this.proxyTxHash = utxo.getTxHash();
        this.proxyOutputIndex = utxo.getOutputIndex();
    }

    @Test
    @Order(1)
    public void setupBootstrapTokenViaProxy() throws CborSerializationException, ApiException, InterruptedException {
        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName("uverify_proxy_test_token");
        bootstrapDatum.setFeeInterval(3);
        bootstrapDatum.setTransactionLimit(15);

        Transaction transaction = cardanoBlockchainService.mintProxyBootstrapDatum(bootstrapDatum, this.proxyTxHash, this.proxyOutputIndex);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), transaction, proxyTxHash, proxyOutputIndex);
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(2)
    public void testUseProxyStateDatum() throws ApiException, CborSerializationException, InterruptedException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test\"}")
                .build());

        Transaction transaction = cardanoBlockchainService.forkProxyStateDatum(
                userAccount.baseAddress(),
                uVerifyCertificates,
                "uverify_proxy_test_token",
                proxyTxHash,
                proxyOutputIndex);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), transaction, proxyTxHash, proxyOutputIndex);
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(3)
    public void testUpdateStateDatum() throws ApiException, CborSerializationException, InterruptedException {
        Result<String> result = updateUserStateDatum();
        Assertions.assertTrue(result.isSuccessful());

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(1, stateDatumEntities.size());

        StateDatumEntity stateDatumEntity = stateDatumEntities.get(0);
        Assertions.assertEquals(13, stateDatumEntity.getCountdown());
    }

    @Test
    @Order(4)
    public void testLegacyInitializeBootstrapDatum() throws ApiException, InterruptedException, CborSerializationException {
        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName("uverify_default_test_token");
        bootstrapDatum.setFeeInterval(3);
        bootstrapDatum.setTransactionLimit(15);
        Transaction transaction = cardanoBlockchainService.initializeBootstrapDatum(bootstrapDatum);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(5)
    public void testLegacyForkStateDatum() throws ApiException, CborSerializationException, InterruptedException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test\"}")
                .build());

        Transaction transaction = cardanoBlockchainService.forkLegacyStateDatum(
                userAccount.baseAddress(),
                uVerifyCertificates,
                "uverify_default_test_token");
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    public Result<String> updateUserStateDatum() throws ApiException, InterruptedException, CborSerializationException {
        return updateUserStateDatum("");
    }

    public Result<String> updateUserStateDatum(String bootstrapToken) throws ApiException, InterruptedException, CborSerializationException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        Transaction transaction = cardanoBlockchainService.updateStateDatum(userAccount.baseAddress(), List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test\"}")
                .build()), bootstrapToken, proxyTxHash, proxyOutputIndex);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        return result;
    }

    @Test
    @Order(6)
    public void testUpdateLegacyStateDatum() throws ApiException, CborSerializationException, InterruptedException {
        Result<String> result = updateUserStateDatum("uverify_default_test_token");
        Assertions.assertTrue(result.isSuccessful());

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(2, stateDatumEntities.size());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());

        Assertions.assertEquals(13, stateDatumEntity.get().getCountdown());
    }

    @Test
    @Order(7)
    public void testUpdateStateDatumAgain() throws CborSerializationException, InterruptedException, ApiException {
        Result<String> result = updateUserStateDatum("uverify_default_test_token");
        Assertions.assertTrue(result.isSuccessful());

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(2, stateDatumEntities.size());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());

        Assertions.assertEquals(12, stateDatumEntity.get().getCountdown());
    }

    @Test
    @Order(8)
    public void testUpdateStateDatumWithFeeNeeded() throws CborSerializationException, InterruptedException, ApiException {
        Result<String> result = updateUserStateDatum("uverify_default_test_token");
        Assertions.assertTrue(result.isSuccessful());

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(2, stateDatumEntities.size());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());

        Assertions.assertEquals(11, stateDatumEntity.get().getCountdown());

        Result<List<Utxo>> utxosResult = yaciCardanoContainer.getUtxoService().getUtxos(feeReceiverAccount.enterpriseAddress(), 100, 1);
        Assertions.
                assertTrue(utxosResult.isSuccessful());

        List<Utxo> utxos = utxosResult.getValue();
        BigInteger lovelace = utxos.stream()
                .map(Utxo::getAmount)
                .map(amount -> amount.stream()
                        .filter(asset -> asset.getUnit().equals("lovelace"))
                        .map(Amount::getQuantity)
                        .reduce(BigInteger::add).orElse(BigInteger.ZERO))
                .reduce(BigInteger::add).orElse(BigInteger.ZERO);

        Assertions.assertEquals(lovelace, BigInteger.valueOf(6_000_000));
    }

    @Test
    @Order(9)
    public void testInitializeSpecialBootstrapDatum() throws ApiException, InterruptedException, CborSerializationException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName("uverify_special_test_token");
        bootstrapDatum.setFeeInterval(20);
        bootstrapDatum.setAllowedCredentials(List.of(paymentCredentialHash.get()));
        bootstrapDatum.setFee(1_000_000);
        bootstrapDatum.setTransactionLimit(100);
        Transaction transaction = cardanoBlockchainService.initializeBootstrapDatum(bootstrapDatum);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(10)
    public void testForkStateDatumFindApplicableBootstrapDatum() throws ApiException, CborSerializationException, InterruptedException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test\"}")
                .build());

        Transaction transaction = cardanoBlockchainService.forkLegacyStateDatum(
                userAccount.baseAddress(),
                uVerifyCertificates);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(11)
    public void testPersistUVerifyCertificates() throws ApiException, CborSerializationException, InterruptedException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test\"}")
                .build());

        Transaction transaction = cardanoBlockchainService.persistUVerifyCertificates(
                userAccount.baseAddress(),
                uVerifyCertificates);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);
        Assertions.assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }
    }

    @Test
    @Order(12)
    public void testInvalidateBootstrapDatum() throws ApiException, InterruptedException, CborSerializationException {
        Transaction transaction = cardanoBlockchainService.invalidateBootstrapDatum("uverify_special_test_token");
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (!result.isSuccessful()) {
            System.out.println(result.getResponse());
        }

        Assertions.assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }
    }

    @Test
    @Order(13)
    public void testRollback() {
        Optional<BootstrapDatumEntity> bootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_special_test_token");
        Assertions.assertTrue(bootstrapDatum.isPresent());

        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificateEntity> certificates = uVerifyCertificateService.getCertificatesByCredential(Hex.encodeHexString(paymentCredentialHash.get()));
        Assertions.assertEquals(6, certificates.size());

        cardanoBlockchainService.handleRollbackToSlot(bootstrapDatum.get().getCreationSlot() - 1);
        bootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_special_test_token");
        Assertions.assertTrue(bootstrapDatum.isEmpty());

        certificates = uVerifyCertificateService.getCertificatesByCredential(Hex.encodeHexString(paymentCredentialHash.get()));
        Assertions.assertEquals(4, certificates.size());
    }

    @Test
    @Order(14)
    public void testInvalidCertificates() throws ApiException, CborSerializationException, InterruptedException {
        Optional<byte[]> paymentCredentialHash = serviceAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        // This test is expected to fail because the issuer is not the same as the transaction signer
        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test\"}")
                .build());

        Transaction transaction = cardanoBlockchainService.forkLegacyStateDatum(
                userAccount.baseAddress(),
                uVerifyCertificates);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);
        Assertions.assertFalse(result.isSuccessful());
    }

    @Test
    @Order(15)
    public void testInitializeZeroFeeBootstrapDatum() throws ApiException, InterruptedException, CborSerializationException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName("uverify_zero_fee_test_token");
        bootstrapDatum.setFeeInterval(3);
        bootstrapDatum.setAllowedCredentials(List.of(paymentCredentialHash.get()));
        bootstrapDatum.setFee(0);
        bootstrapDatum.setTransactionLimit(3);
        Transaction transaction = cardanoBlockchainService.initializeBootstrapDatum(bootstrapDatum);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(16)
    public void testUseZeroFeeBootstrapDatum() throws ApiException, CborSerializationException, InterruptedException {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"random\":\"test\"}")
                .build());

        Transaction transaction = cardanoBlockchainService.forkLegacyStateDatum(
                userAccount.baseAddress(),
                uVerifyCertificates, "uverify_zero_fee_test_token");
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);
        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }
}
