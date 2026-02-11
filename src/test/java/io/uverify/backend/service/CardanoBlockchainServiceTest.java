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

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.dto.ProxyInitResponse;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.model.UVerifyCertificate;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static io.uverify.backend.simulation.SimulationUtils.simulateAddressUtxo;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
public class CardanoBlockchainServiceTest extends CardanoBlockchainTest {

    @Autowired
    public CardanoBlockchainServiceTest(@Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
                                        @Value("${cardano.test.user.mnemonic}") String testUserMnemonic,
                                        @Value("${cardano.service.fee.receiver.mnemonic}") String feeReceiverMnemonic,
                                        @Value("${cardano.facilitator.user.mnemonic}") String facilitatorMnemonic,
                                        CardanoBlockchainService cardanoBlockchainService,
                                        StateDatumService stateDatumService,
                                        BootstrapDatumService bootstrapDatumService,
                                        UVerifyCertificateService uVerifyCertificateService,
                                        StateDatumRepository stateDatumRepository,
                                        BootstrapDatumRepository bootstrapDatumRepository,
                                        CertificateRepository certificateRepository,
                                        ExtensionManager extensionManager,
                                        ValidatorHelper validatorHelper) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic, cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService, stateDatumRepository, bootstrapDatumRepository, certificateRepository, extensionManager, validatorHelper, List.of());
    }

    private StateDatumEntity getFirstUserStateDatum(String address) {
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(address);
        Assertions.assertTrue(stateDatumEntities.size() > 0);
        return stateDatumEntities.get(0);
    }

    private StateDatumEntity getFirstUserStateDatum(String address, String bootstrapTokenName) {
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals(bootstrapTokenName))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());
        return stateDatumEntity.get();
    }

    private int getFeeReceived(String address) throws ApiException {
        Result<List<Utxo>> utxosResult = yaciCardanoContainer.getUtxoService().getUtxos(address, 100, 1);

        List<Utxo> utxos = utxosResult.getValue();
        BigInteger lovelace = utxos.stream()
                .map(Utxo::getAmount)
                .map(amount -> amount.stream()
                        .filter(asset -> asset.getUnit().equals("lovelace"))
                        .map(Amount::getQuantity)
                        .reduce(BigInteger::add).orElse(BigInteger.ZERO))
                .reduce(BigInteger::add).orElse(BigInteger.ZERO);

        return lovelace.intValue();
    }

    @Test
    @Order(0)
    public void initProxyContract() throws ApiException, CborSerializationException, CborDeserializationException, InterruptedException {
        ProxyInitResponse proxyInitResponse = cardanoBlockchainService.initProxyContract();
        Result<String> result = cardanoBlockchainService.submitTransaction(Transaction.deserialize(HexUtil.decodeHexString(proxyInitResponse.getUnsignedProxyTransaction())), serviceAccount);
        Assertions.assertTrue(result.isSuccessful());

        validatorHelper.setProxy(proxyInitResponse.getProxyTxHash(), proxyInitResponse.getProxyOutputIndex());
        Thread.sleep(1000);
    }

    @Test
    @Order(1)
    public void deployUVerifyContracts() throws CborSerializationException, ApiException, InterruptedException {
        Transaction transaction = cardanoBlockchainService.deployUVerifyContracts();
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        Assertions.assertTrue(result.isSuccessful());
        String libraryTxHash = result.getValue();

        Thread.sleep(1000);

        Result<TxContentUtxo> transactionUtxos = this.yaciCardanoContainer.getBackendService().getTransactionService().getTransactionUtxos(libraryTxHash);
        Assertions.assertTrue(transactionUtxos.isSuccessful());

        List<TxContentUtxoOutputs> outputs = transactionUtxos.getValue().getOutputs();
        Integer libraryStateOutputIndex = null;
        Integer libraryProxyOutputIndex = null;

        for (TxContentUtxoOutputs output : outputs) {
            if (output.getReferenceScriptHash() != null) {
                if (output.getReferenceScriptHash().equals(ValidatorUtils.validatorToScriptHash(validatorHelper.getParameterizedUVerifyStateContract()))) {
                    libraryStateOutputIndex = output.getOutputIndex();
                } else if (output.getReferenceScriptHash().equals(ValidatorUtils.validatorToScriptHash(validatorHelper.getParameterizedProxyContract()))) {
                    libraryProxyOutputIndex = output.getOutputIndex();
                }
            }
        }

        Assertions.assertNotNull(libraryStateOutputIndex);
        Assertions.assertNotNull(libraryProxyOutputIndex);

        validatorHelper.setLibrary(libraryTxHash, libraryProxyOutputIndex, libraryStateOutputIndex);
    }

    @Test
    @Order(2)
    public void setupBootstrapTokenViaProxy() throws CborSerializationException, ApiException, InterruptedException, CborException, AddressExcepion {
        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName("uverify_proxy_test_token");
        bootstrapDatum.setFeeInterval(3);
        bootstrapDatum.setTransactionLimit(15);

        Transaction transaction = cardanoBlockchainService.mintProxyBootstrapDatum(bootstrapDatum);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), transaction);
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(3)
    public void testUseProxyStateDatum() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion {
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
                "uverify_proxy_test_token");
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), transaction);
        }

        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertEquals(getFeeReceived(feeReceiverAccount.enterpriseAddress()), 2_000_000L);

        StateDatumEntity userStateDatum = getFirstUserStateDatum(userAccount.baseAddress());
        Assertions.assertEquals(14, userStateDatum.getCountdown());
    }

    @Test
    @Order(4)
    public void testUpdateStateDatum() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion {
        Result<String> result = updateUserStateDatum("");

        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertEquals(getFeeReceived(feeReceiverAccount.enterpriseAddress()), 2_000_000L);

        StateDatumEntity userStateDatum = getFirstUserStateDatum(userAccount.baseAddress());
        Assertions.assertEquals(13, userStateDatum.getCountdown());
    }

    @Test
    @Order(5)
    public void testUpdateStateDatumAgain() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion {
        Result<String> result = updateUserStateDatum("");
        Assertions.assertTrue(result.isSuccessful());

        Assertions.assertEquals(getFeeReceived(feeReceiverAccount.enterpriseAddress()), 2_000_000L);
        StateDatumEntity userStateDatum = getFirstUserStateDatum(userAccount.baseAddress());
        Assertions.assertEquals(12, userStateDatum.getCountdown());
    }

    @Test
    @Order(6)
    public void testPersistUVerifyProxyCertificates() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion {
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
            simulateYaciStoreBehavior(result.getValue(), transaction);
        }

        Assertions.assertEquals(getFeeReceived(feeReceiverAccount.enterpriseAddress()), 4_000_000L);

        StateDatumEntity userStateDatum = getFirstUserStateDatum(userAccount.baseAddress());
        Assertions.assertEquals(11, userStateDatum.getCountdown());
    }

    @Test
    @Order(7)
    public void testLegacyInitializeBootstrapDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("96d815a564438d55bdb9f8398dcdfb1944648c6d68f7a4ba633a53328f26da2a", 0,
                        298L, "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "uverify_default_test_token", "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203757665726966795f64656661756c745f746573745f746f6b656e",
                        "d8799f80581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581a757665726966795f64656661756c745f746573745f746f6b656e581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a001e8480039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35cef04d10f01ff"
                ),
                simulateAddressUtxo("96d815a564438d55bdb9f8398dcdfb1944648c6d68f7a4ba633a53328f26da2a", 1,
                        298L, "3259cc545ae3defca96aacaed599af0219780ca1af2b9fcf55403932e7cacade",
                        "11bcb8e4a79304e23bcd5e2bf628907f9b998f43902f41d1db439981", "",
                        "", "")
        );
        simulateYaciStoreBehavior(addressUtxos);
        Optional<BootstrapDatumEntity> optionalBootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_default_test_token", 1);

        Assertions.assertTrue(optionalBootstrapDatum.isPresent());
        Assertions.assertEquals(optionalBootstrapDatum.get().getTransactionLimit(), 15);
    }

    @Test
    @Order(8)
    public void testLegacyForkStateDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("0175bfcf9d32886bba242a45685ab8180c43b47575958c6b9a9839e28ca10835", 0,
                        405L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f58205f8a4be2d3873b135cfcba1c05ccb64d5b88470e689bd8f754aec3f91543d939581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a001e8480039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35cef04d10e9fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f4f7b2274657374223a2274657374227dffffff01581a757665726966795f64656661756c745f746573745f746f6b656eff"
                ),
                simulateAddressUtxo("0175bfcf9d32886bba242a45685ab8180c43b47575958c6b9a9839e28ca10835", 1,
                        405L, "92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445",
                        "", "",
                        "", ""
                ),
                simulateAddressUtxo("0175bfcf9d32886bba242a45685ab8180c43b47575958c6b9a9839e28ca10835", 2,
                        405L, "e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "", "",
                        "", "")
        );

        simulateYaciStoreBehavior(addressUtxos);

        StateDatumEntity stateDatumEntity = getFirstUserStateDatum(userAccount.baseAddress(), "uverify_default_test_token");
        Assertions.assertEquals(14, stateDatumEntity.getCountdown());
    }

    public Result<String> updateUserStateDatum(String bootstrapToken) throws ApiException, InterruptedException, CborSerializationException, CborException, AddressExcepion {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        Transaction transaction = cardanoBlockchainService.updateStateDatum(userAccount.baseAddress(), List.of(UVerifyCertificate.builder()
                .algorithm("SHA256")
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                .extra("{\"test\":\"test2\"}")
                .build()), bootstrapToken);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);

        if (result.isSuccessful()) {
            if (transaction.getBody().getWithdrawals().size() == 0) {
                simulateYaciStoreBehavior(result.getValue());
            } else {
                simulateYaciStoreBehavior(result.getValue(), transaction);
            }
        }

        return result;
    }

    @Test
    @Order(9)
    public void testUpdateLegacyStateDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("25772c6adee724d23d8796fd112501aa39ad25184908e3a35038c24698ce0be0", 0,
                        560L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f58205f8a4be2d3873b135cfcba1c05ccb64d5b88470e689bd8f754aec3f91543d939581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a001e8480039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35cef04d10d9fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f4f7b2274657374223a2274657374227dffffff01581a757665726966795f64656661756c745f746573745f746f6b656eff"
                ),
                simulateAddressUtxo("25772c6adee724d23d8796fd112501aa39ad25184908e3a35038c24698ce0be0", 1,
                        560L, "e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "", "",
                        "", ""
                )
        );

        simulateYaciStoreBehavior(addressUtxos);

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(2, stateDatumEntities.size());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());

        Assertions.assertEquals(13, stateDatumEntity.get().getCountdown());
    }

    @Test
    @Order(10)
    public void testUpdateLegacyStateDatumAgain() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("aca4edabcd02745fe31e1aabe04a0907f51d1cd31125b3be92c357fcbf2d2d6f", 0,
                        782L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f58205f8a4be2d3873b135cfcba1c05ccb64d5b88470e689bd8f754aec3f91543d939581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a001e8480039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e10d0b10c9fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f4f7b2274657374223a2274657374227dffffff01581a757665726966795f64656661756c745f746573745f746f6b656eff"),
                simulateAddressUtxo("aca4edabcd02745fe31e1aabe04a0907f51d1cd31125b3be92c357fcbf2d2d6f", 1,
                        782L, "e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "", "",
                        "", "")
        );

        simulateYaciStoreBehavior(addressUtxos);

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(2, stateDatumEntities.size());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());

        Assertions.assertEquals(12, stateDatumEntity.get().getCountdown());
    }

    @Test
    @Order(11)
    public void testUpdateLegacyStateDatumWithFeeNeeded() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("1463a7c90c73d6e1d6fc9ff26e7b44a9adb382ffe8c0500cb54dd9df86482042", 0,
                        876L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f58205f8a4be2d3873b135cfcba1c05ccb64d5b88470e689bd8f754aec3f91543d939581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a001e8480039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e10d0b10b9fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f4f7b2274657374223a2274657374227dffffff01581a757665726966795f64656661756c745f746573745f746f6b656eff"),
                simulateAddressUtxo("1463a7c90c73d6e1d6fc9ff26e7b44a9adb382ffe8c0500cb54dd9df86482042", 1,
                        876L, "92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445",
                        "", "",
                        "", ""),
                simulateAddressUtxo("1463a7c90c73d6e1d6fc9ff26e7b44a9adb382ffe8c0500cb54dd9df86482042", 2,
                        876L, "e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "", "",
                        "", "")
        );

        simulateYaciStoreBehavior(addressUtxos);

        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(2, stateDatumEntities.size());

        Optional<StateDatumEntity> stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst();
        Assertions.assertTrue(stateDatumEntity.isPresent());

        Assertions.assertEquals(11, stateDatumEntity.get().getCountdown());
    }

    @Test
    @Order(12)
    public void testInitializeSpecialLegacyBootstrapDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("b7faeb20279c44ff3c5e53cc1a30d6c80465ced12413f2eccb2c57d93d03f331", 0,
                        1974L, "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "uverify_special_test_token", "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203757665726966795f7370656369616c5f746573745f746f6b656e",
                        "d8799f9f581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5ff581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581a757665726966795f7370656369616c5f746573745f746f6b656e581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a000f4240149f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e155fc4186401ff"
                ),
                simulateAddressUtxo("b7faeb20279c44ff3c5e53cc1a30d6c80465ced12413f2eccb2c57d93d03f331", 1,
                        1974L, "11bcb8e4a79304e23bcd5e2bf628907f9b998f43902f41d1db439981",
                        "", "",
                        "", "")
        );

        simulateYaciStoreBehavior(addressUtxos);
        Optional<BootstrapDatumEntity> optionalBootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_special_test_token", 1);
        Assertions.assertTrue(optionalBootstrapDatum.isPresent());
    }

    @Test
    @Order(13)
    public void testForkLegacyStateDatumFindApplicableBootstrapDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("30aa4c70af38b5b83bb94e390437ec7a728ce025107bf0969348f0b2d476c17b", 0,
                        2041L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f5820456d439f7df41aabf82dce761189401b0001e27851fa84fa35e0c380da9d6ad2581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a000f4240149f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e155fc418639fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f4f7b2274657374223a2274657374227dffffff01581a757665726966795f7370656369616c5f746573745f746f6b656eff"
                ),
                simulateAddressUtxo("30aa4c70af38b5b83bb94e390437ec7a728ce025107bf0969348f0b2d476c17b", 1,
                        2041L, "92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445",
                        "", "",
                        "", ""),
                simulateAddressUtxo("30aa4c70af38b5b83bb94e390437ec7a728ce025107bf0969348f0b2d476c17b", 2,
                        2041L, "e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "", "",
                        "", "")
        );
        simulateYaciStoreBehavior(addressUtxos);
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        Assertions.assertEquals(3, stateDatumEntities.size());
    }

    @Test
    @Order(14)
    public void testPersistUVerifyCertificates() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("d4a264edfb70dca55d603f88da92cddf788cce9725c3c89f6a47ee03e6abe0cd", 0,
                        2120L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f5820e30311d92c4eed79e7447936961f866104d39173eee467ba2d20bb3360f78d68581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed161a001e8480039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e10d0b10a9fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f4f7b2274657374223a2274657374227dffffff01581a757665726966795f64656661756c745f746573745f746f6b656eff"
                ),
                simulateAddressUtxo("d4a264edfb70dca55d603f88da92cddf788cce9725c3c89f6a47ee03e6abe0cd", 1,
                        2120L, "92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445",
                        "", "",
                        "", "")
        );
        simulateYaciStoreBehavior(addressUtxos);
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        StateDatumEntity stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_default_test_token"))
                .findFirst().orElseThrow();

        Assertions.assertEquals(11, stateDatumEntity.getCountdown());
    }

    @Test
    @Order(15)
    public void testInvalidateBootstrapDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("ef16468c221630e2b2609b4f6b75a1d26bb870df5635504ed2ecc784212fda59", 1,
                        2272L, "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "", "",
                        "", "")
        );
        simulateYaciStoreBehavior(addressUtxos);
        Optional<BootstrapDatumEntity> optionalBootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_special_test_token", 1);
        Assertions.assertTrue(optionalBootstrapDatum.isPresent());
        Assertions.assertNull(optionalBootstrapDatum.get().getInvalidationSlot());
    }

    @Test
    @Order(16)
    public void testRollback() {
        Optional<BootstrapDatumEntity> bootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_special_test_token", 1);
        Assertions.assertTrue(bootstrapDatum.isPresent());

        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificateEntity> certificates = uVerifyCertificateService.getCertificatesByCredential(Hex.encodeHexString(paymentCredentialHash.get()));
        Assertions.assertEquals(10, certificates.size());

        cardanoBlockchainService.handleRollbackToSlot(bootstrapDatum.get().getCreationSlot() - 1);
        bootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_special_test_token", 1);
        Assertions.assertTrue(bootstrapDatum.isEmpty());

        certificates = uVerifyCertificateService.getCertificatesByCredential(Hex.encodeHexString(paymentCredentialHash.get()));
        Assertions.assertEquals(8, certificates.size());
    }

    @Test
    @Order(17)
    public void testInitializeZeroFeeBootstrapDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("44b3f42af43373183646f9372adbae0e9be13355909a0c927a4aeccf4f00dc5a", 0,
                        4414L, "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "uverify_zero_fee_test_token", "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203",
                        "d3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203757665726966795f7a65726f5f6665655f746573745f746f6b656e",
                        "d8799f9f581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5ff581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581b757665726966795f7a65726f5f6665655f746573745f746f6b656e581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed1600039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e1c177f0301ff"),
                simulateAddressUtxo("44b3f42af43373183646f9372adbae0e9be13355909a0c927a4aeccf4f00dc5a", 1,
                        4414L, "11bcb8e4a79304e23bcd5e2bf628907f9b998f43902f41d1db439981",
                        "", "",
                        "", "")
        );
        simulateYaciStoreBehavior(addressUtxos);
        Optional<BootstrapDatumEntity> optionalBootstrapDatum = bootstrapDatumService.getBootstrapDatum("uverify_zero_fee_test_token", 1);
        Assertions.assertTrue(optionalBootstrapDatum.isPresent());
    }

    @Test
    @Order(18)
    public void testUseZeroFeeBootstrapDatum() throws InterruptedException {
        List<AddressUtxo> addressUtxos = List.of(
                simulateAddressUtxo("971f13819c5c4dc8b3477e5d1f0da068b87d35fb98c7e80a316f667b266862e7", 0,
                        4517L, "f2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed16",
                        "�����3�2�0�4,�\u00044���\u001d�\u0000��J���", "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62",
                        "6b3a786359bfb307baa3be8cc4fa9872c219c42b76226a4f97d90a62e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "d8799f58209d933b077f09812655174bf8056a950af218ceacc762b4f97ca6a018ac6741f8581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5581cd3c25617941168e238038f1cf6542229b919bbd6fd2b8292ce176203581cf2f25cfcc4e1665bad2477fc1aa6e9960ae59ef1681eae28a0bfed1600039f581c92e2ae51fb03dcc55c471506fe35bdedad9c266b0d09c2b8bc7cb445ff1b000001a35e1c177f029fd8799f5820b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e24689746534841323536581ce0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a59f517b2272616e646f6d223a2274657374227dffffff01581b757665726966795f7a65726f5f6665655f746573745f746f6b656eff"),
                simulateAddressUtxo("971f13819c5c4dc8b3477e5d1f0da068b87d35fb98c7e80a316f667b266862e7", 2,
                        4517L, "e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a5",
                        "", "",
                        "", "")
        );
        simulateYaciStoreBehavior(addressUtxos);
        List<StateDatumEntity> stateDatumEntities = stateDatumService.findByOwner(userAccount.baseAddress());
        StateDatumEntity stateDatumEntity = stateDatumEntities.stream()
                .filter(stateDatum -> stateDatum.getBootstrapDatum().getTokenName().equals("uverify_zero_fee_test_token"))
                .findFirst().orElseThrow();
        Assertions.assertEquals(2, stateDatumEntity.getCountdown());
    }

    @Test
    @Order(19)
    public void testInitializeSpecialBootstrapDatum() throws InterruptedException, CborSerializationException, ApiException, CborException, AddressExcepion {
        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of());
        bootstrapDatum.setTokenName("special_partner_token");
        bootstrapDatum.setFeeInterval(100);
        bootstrapDatum.setFee(0);
        bootstrapDatum.setBatchSize(3);
        bootstrapDatum.setTransactionLimit(10);

        Transaction transaction = cardanoBlockchainService.mintProxyBootstrapDatum(bootstrapDatum);
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), transaction);
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(20)
    public void testPersistUVerifyBatchCertificates() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion {
        Optional<byte[]> paymentCredentialHash = userAccount.getBaseAddress().getPaymentCredentialHash();
        Assertions.assertTrue(paymentCredentialHash.isPresent());

        List<UVerifyCertificate> uVerifyCertificates = List.of(UVerifyCertificate.builder()
                        .algorithm("SHA256")
                        .hash("a652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                        .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                        .extra("{\"test\":\"test\"}")
                        .build(),
                UVerifyCertificate.builder()
                        .algorithm("SHA256")
                        .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                        .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                        .extra("{\"hello\":\"world\"}")
                        .build(),
                UVerifyCertificate.builder()
                        .algorithm("SHA256")
                        .hash("c652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                        .issuer(Hex.encodeHexString(paymentCredentialHash.get()))
                        .extra("{\"one\":\"two\"}")
                        .build());

        Transaction transaction = cardanoBlockchainService.forkProxyStateDatum(
                userAccount.baseAddress(),
                uVerifyCertificates,
                "special_partner_token");
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);
        Assertions.assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), transaction);
        }
    }
}
