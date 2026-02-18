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

package io.uverify.backend.controller;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.cip.cip30.CIP30DataSigner;
import com.bloxbean.cardano.client.cip.cip30.DataSignError;
import com.bloxbean.cardano.client.cip.cip30.DataSignature;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.dto.*;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.TransactionType;
import io.uverify.backend.enums.UserAction;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.service.*;
import io.uverify.backend.util.ValidatorHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import java.util.List;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserStateControllerTest extends CardanoBlockchainTest {

    private final String feeReceiverPartnerAddress;
    private UserActionResponse userInfoResponse;

    @Autowired
    public UserStateControllerTest(
            @LocalServerPort int port,
            @Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
            @Value("${cardano.test.user.mnemonic}") String testUserMnemonic,
            @Value("${cardano.service.fee.receiver.mnemonic}") String feeReceiverMnemonic,
            @Value("${cardano.facilitator.user.mnemonic}") String facilitatorMnemonic,
            @Value("${cardano.service.fee.partner.address}") String feeReceiverPartnerAddress,
            CardanoBlockchainService cardanoBlockchainService,
            StateDatumService stateDatumService,
            BootstrapDatumService bootstrapDatumService,
            UVerifyCertificateService uVerifyCertificateService,
            StateDatumRepository stateDatumRepository,
            BootstrapDatumRepository bootstrapDatumRepository,
            CertificateRepository certificateRepository,
            ValidatorHelper validatorHelper,
            ExtensionManager extensionManager,
            LibraryService libraryService) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic, cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService, stateDatumRepository, bootstrapDatumRepository, certificateRepository, extensionManager, validatorHelper, libraryService, List.of());
        RestAssured.port = port;
        this.feeReceiverPartnerAddress = feeReceiverPartnerAddress;
    }

    private UserActionResponse requestUserInfo(String address) {
        UserActionRequest request = new UserActionRequest();
        request.setAction(UserAction.USER_INFO);
        request.setAddress(address);

        return given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/user/request/action")
                .then()
                .extract()
                .as(UserActionResponse.class);
    }

    @Test
    @Order(1)
    public void testRequestUserStateInfo() {
        this.userInfoResponse = requestUserInfo(userAccount.baseAddress());

        Assertions.assertEquals(UserAction.USER_INFO, this.userInfoResponse.getAction());
        Assertions.assertEquals(HttpStatus.OK, this.userInfoResponse.getStatus());
        Assertions.assertNotNull(this.userInfoResponse.getSignature());
        Assertions.assertNotNull(this.userInfoResponse.getTimestamp());

        String message = "[" + this.userInfoResponse.getAction() + "@" + this.userInfoResponse.getTimestamp() + "] Please sign this message with your private key to verify, " +
                "that you are the owner of the address " + userAccount.baseAddress() + ".";

        Assertions.assertEquals(message, this.userInfoResponse.getMessage());

        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();
        Assertions.assertTrue(edDSASigningProvider.verify(HexUtil.decodeHexString(this.userInfoResponse.getSignature()),
                this.userInfoResponse.getMessage().getBytes(), this.facilitatorAccount.publicKeyBytes()));
    }

    @Test
    @Order(2)
    public void testExecuteGetUserInfo() throws DataSignError {

        DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(userAccount.getBaseAddress().getBytes(), this.userInfoResponse.getMessage().getBytes(), userAccount);

        ExecuteUserActionRequest request = new ExecuteUserActionRequest();
        request.setAction(this.userInfoResponse.getAction());
        request.setAddress(this.userInfoResponse.getAddress());
        request.setSignature(this.userInfoResponse.getSignature());
        request.setTimestamp(this.userInfoResponse.getTimestamp());
        request.setMessage(this.userInfoResponse.getMessage());
        request.setUserSignature(dataSignature.signature());
        request.setUserPublicKey(dataSignature.key());

        ExecuteUserActionResponse executeUserActionResponse = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/user/state/action")
                .then()
                .extract()
                .as(ExecuteUserActionResponse.class);

        Assertions.assertEquals(HttpStatus.OK, executeUserActionResponse.getStatus());
        Assertions.assertEquals(0, executeUserActionResponse.getState().getBootstrapDatums().size());
        Assertions.assertEquals(0, executeUserActionResponse.getState().getStates().size());
    }

    @Test
    @Order(3)
    public void initProxyContract() throws ApiException, CborSerializationException, CborDeserializationException, InterruptedException {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.INIT);

        ProxyInitResponse buildTransactionResponse = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/transaction/build")
                .then()
                .extract()
                .as(ProxyInitResponse.class);

        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(buildTransactionResponse.getUnsignedProxyTransaction()));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);
        Assertions.assertTrue(result.isSuccessful());

        Thread.sleep(1000);

        validatorHelper.setProxy(buildTransactionResponse.getProxyTxHash(), buildTransactionResponse.getProxyOutputIndex());
        Thread.sleep(1000);
    }

    @Test
    @Order(4)
    public void deployUVerifyContracts() throws CborSerializationException, ApiException, InterruptedException, CborDeserializationException, CborException, AddressExcepion {
        BuildTransactionResponse buildTransactionResponse = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/library/deploy/proxy")
                .then()
                .extract()
                .as(BuildTransactionResponse.class);

        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(buildTransactionResponse.getUnsignedTransaction()));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);
        Assertions.assertTrue(result.isSuccessful());

        Thread.sleep(1000);

        if (result.isSuccessful()) {
            // The signed transaction needs to be submitted as the processor
            // ensures it has been signed by the service account
            Transaction signedTransaction = TransactionSigner.INSTANCE.sign(transaction, serviceAccount.hdKeyPair());
            simulateYaciStoreBehavior(result.getValue(), signedTransaction);
        }

        Thread.sleep(1000);
    }

    @Test
    @Order(5)
    public void testBuildBootstrapDatum() throws ApiException, InterruptedException, CborDeserializationException, CborSerializationException, CborException, AddressExcepion {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.BOOTSTRAP);
        request.setBootstrapDatum(BootstrapData.builder()
                .name("default")
                .whitelistedAddresses(List.of())
                .ttl(3967434217794L)
                .fee(2000000)
                .feeReceiverAddresses(List.of(
                        feeReceiverAccount.baseAddress(),
                        feeReceiverPartnerAddress
                ))
                .transactionLimit(100)
                .batchSize(1)
                .feeInterval(10)
                .build());

        BuildTransactionResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/transaction/build")
                .then()
                .extract()
                .as(BuildTransactionResponse.class);

        Assertions.assertEquals(BuildStatusCode.SUCCESS, response.getStatus().getCode());

        Transaction signedTransaction = TransactionSigner.INSTANCE.sign(Transaction.deserialize(HexUtil.decodeHexString(response.getUnsignedTransaction())), serviceAccount.hdKeyPair());
        Result<String> result = yaciCardanoContainer.getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), signedTransaction);
        }

        Assertions.assertTrue(result.isSuccessful());
        Thread.sleep(1000);
    }

    @Test
    @Order(6)
    public void testCreateState() throws ApiException, CborDeserializationException, CborSerializationException, InterruptedException, CborException, AddressExcepion {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.DEFAULT);
        request.setAddress(userAccount.baseAddress());
        request.setCertificates(List.of(CertificateData.builder()
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .metadata("{\"test\":\"test\"}")
                .algorithm("SHA-256")
                .build()));

        BuildTransactionResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/transaction/build")
                .then()
                .extract()
                .as(BuildTransactionResponse.class);

        Assertions.assertEquals(BuildStatusCode.SUCCESS, response.getStatus().getCode());

        Transaction signedTransaction = TransactionSigner.INSTANCE.sign(Transaction.deserialize(HexUtil.decodeHexString(response.getUnsignedTransaction())), userAccount.hdKeyPair());
        Result<String> result = yaciCardanoContainer.getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue(), signedTransaction);
        }

        Assertions.assertTrue(result.isSuccessful());
        Thread.sleep(1000);
    }

    @Test
    @Order(7)
    public void testUserInfoAfterUsage() throws DataSignError {
        DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(userAccount.getBaseAddress().getBytes(), this.userInfoResponse.getMessage().getBytes(), userAccount);

        ExecuteUserActionRequest request = new ExecuteUserActionRequest();
        request.setAction(this.userInfoResponse.getAction());
        request.setAddress(this.userInfoResponse.getAddress());
        request.setSignature(this.userInfoResponse.getSignature());
        request.setTimestamp(this.userInfoResponse.getTimestamp());
        request.setMessage(this.userInfoResponse.getMessage());
        request.setUserSignature(dataSignature.signature());
        request.setUserPublicKey(dataSignature.key());

        ExecuteUserActionResponse executeUserActionResponse = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/user/state/action")
                .then()
                .extract()
                .as(ExecuteUserActionResponse.class);

        Assertions.assertEquals(HttpStatus.OK, executeUserActionResponse.getStatus());
        Assertions.assertEquals(1, executeUserActionResponse.getState().getBootstrapDatums().size());
        Assertions.assertEquals(1, executeUserActionResponse.getState().getStates().size());

        StateData stateDatum = executeUserActionResponse.getState().getStates().get(0);
        Assertions.assertEquals(99, stateDatum.getCountdown());
        Assertions.assertEquals(1, stateDatum.getBatchSize());
        Assertions.assertEquals(2000000, stateDatum.getFee());
        Assertions.assertEquals(10, stateDatum.getFeeInterval());
        Assertions.assertEquals("default", stateDatum.getBootstrapDatumName());
    }

    @Test
    @Order(8)
    public void testCreateAnotherState() throws ApiException, CborDeserializationException, CborSerializationException, InterruptedException {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.DEFAULT);
        request.setAddress(userAccount.baseAddress());
        request.setCertificates(List.of(CertificateData.builder()
                .hash("c152f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a66c92e480e1268a5")
                .metadata("{\"hello\":\"world\"}")
                .algorithm("SHA-256")
                .build()));

        BuildTransactionResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/transaction/build")
                .then()
                .extract()
                .as(BuildTransactionResponse.class);

        if (response.getStatus().getCode() == BuildStatusCode.ERROR) {
            System.out.println(response.getStatus().getMessage());
        }

        Assertions.assertEquals(BuildStatusCode.SUCCESS, response.getStatus().getCode());

        Transaction signedTransaction = TransactionSigner.INSTANCE.sign(Transaction.deserialize(HexUtil.decodeHexString(response.getUnsignedTransaction())), userAccount.hdKeyPair());
        Result<String> result = yaciCardanoContainer.getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }
}
