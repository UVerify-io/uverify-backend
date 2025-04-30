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

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
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
import io.uverify.backend.service.BootstrapDatumService;
import io.uverify.backend.service.CardanoBlockchainService;
import io.uverify.backend.service.StateDatumService;
import io.uverify.backend.service.UVerifyCertificateService;
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
            ExtensionManager extensionManager) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic, cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService, stateDatumRepository, bootstrapDatumRepository, certificateRepository, extensionManager, List.of());
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
        UserActionResponse response = requestUserInfo(userAccount.baseAddress());

        Assertions.assertEquals(UserAction.USER_INFO, response.getAction());
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertNotNull(response.getSignature());
        Assertions.assertNotNull(response.getTimestamp());

        String message = "[" + response.getAction() + "@" + response.getTimestamp() + "] Please sign this message with your private key to verify, " +
                "that you are the owner of the address " + userAccount.baseAddress() + ".";

        Assertions.assertEquals(message, response.getMessage());

        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();
        Assertions.assertTrue(edDSASigningProvider.verify(HexUtil.decodeHexString(response.getSignature()),
                response.getMessage().getBytes(), this.facilitatorAccount.publicKeyBytes()));
    }

    @Test
    @Order(2)
    public void testExecuteGetUserInfo() {
        /*
        TODO: Check why Eternl generates a different signature than the one generated by the CIP30DataSigner
        DataSignature userSignature = CIP30DataSigner.INSTANCE.signData(userAccount.baseAddress().getBytes(),
                response.getMessage().getBytes(), userAccount);
        */
        String userSignature = "845846a201276761646472657373583900e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a535278d1d7fe86d291a4089dd79e650b04543c04967114e80123e6f03a166686173686564f458e75b555345525f494e464f40313733353834393336383931395d20506c65617365207369676e2074686973206d657373616765207769746820796f75722070726976617465206b657920746f207665726966792c207468617420796f752061726520746865206f776e6572206f6620746865206164647265737320616464725f746573743171727376666134747371656d78763567787a326e6774786a71733632617864387268667370663772663266633066663479377833366c6c6764353533357379666d3475377635397367347075716a74387a39386771793337647570736b36736e706b2e58404d4085474ac85dfcdd3ea66b636a617215d041848ba0feec98c0fa4203556d675e9c4d124b1b8e52a65045b1a2aaad5f35375400bc85220ec2ab761823224909";
        String userSignatureKey = "a40101032720062158204d53425ae3a113a73e35615cc9530ff0ed311a3b3fb44ea166050e933010e8a3";

        ExecuteUserActionRequest request = new ExecuteUserActionRequest();
        request.setAction(UserAction.USER_INFO);
        request.setAddress(userAccount.baseAddress());
        request.setSignature("14c44f1ec2f9f3062dfb39ffcceb5cab444b9a76c971053983c66428bce68628812bd58eacc99ee01313fb30bd1cfb449d1654eb12cdf3f7d484dab158819f05");
        request.setTimestamp(1735849368919L);
        request.setMessage("[USER_INFO@1735849368919] Please sign this message with your private key to verify, that you are the owner of the address addr_test1qrsvfa4tsqemxv5gxz2ngtxjqs62axd8rhfspf7rf2fc0ff4y7x36llgd5535syfm4u7v59sg4puqjt8z98gqy37dupsk6snpk.");
        request.setUserSignature(userSignature);
        request.setUserPublicKey(userSignatureKey);

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
    public void testBuildBootstrapDatum() throws ApiException, InterruptedException, CborDeserializationException, CborSerializationException {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.BOOTSTRAP);
        request.setBootstrapDatum(BootstrapData.builder()
                .name("default")
                .whitelistedAddresses(List.of())
                .ttl(1767434217794L)
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
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(4)
    public void testCreateState() throws ApiException, CborDeserializationException, CborSerializationException, InterruptedException {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.DEFAULT);
        request.setAddress(userAccount.baseAddress());
        request.setCertificates(List.of(CertificateData.builder()
                .hash("b652f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a51c92e480e246897")
                .metadata("{\"test\":\"test\"}")
                .algorithm("SHA256")
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
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    @Order(5)
    public void testUserInfoAfterUsage() {
        /*
        TODO: Check why Eternl generates a different signature than the one generated by the CIP30DataSigner
        DataSignature userSignature = CIP30DataSigner.INSTANCE.signData(userAccount.baseAddress().getBytes(),
                response.getMessage().getBytes(), userAccount);
        */
        String userSignature = "845846a201276761646472657373583900e0c4f6ab8033b332883095342cd20434ae99a71dd300a7c34a9387a535278d1d7fe86d291a4089dd79e650b04543c04967114e80123e6f03a166686173686564f458e75b555345525f494e464f40313733353834393336383931395d20506c65617365207369676e2074686973206d657373616765207769746820796f75722070726976617465206b657920746f207665726966792c207468617420796f752061726520746865206f776e6572206f6620746865206164647265737320616464725f746573743171727376666134747371656d78763567787a326e6774786a71733632617864387268667370663772663266633066663479377833366c6c6764353533357379666d3475377635397367347075716a74387a39386771793337647570736b36736e706b2e58404d4085474ac85dfcdd3ea66b636a617215d041848ba0feec98c0fa4203556d675e9c4d124b1b8e52a65045b1a2aaad5f35375400bc85220ec2ab761823224909";
        String userSignatureKey = "a40101032720062158204d53425ae3a113a73e35615cc9530ff0ed311a3b3fb44ea166050e933010e8a3";

        ExecuteUserActionRequest request = new ExecuteUserActionRequest();
        request.setAction(UserAction.USER_INFO);
        request.setAddress(userAccount.baseAddress());
        request.setSignature("14c44f1ec2f9f3062dfb39ffcceb5cab444b9a76c971053983c66428bce68628812bd58eacc99ee01313fb30bd1cfb449d1654eb12cdf3f7d484dab158819f05");
        request.setTimestamp(1735849368919L);
        request.setMessage("[USER_INFO@1735849368919] Please sign this message with your private key to verify, that you are the owner of the address addr_test1qrsvfa4tsqemxv5gxz2ngtxjqs62axd8rhfspf7rf2fc0ff4y7x36llgd5535syfm4u7v59sg4puqjt8z98gqy37dupsk6snpk.");
        request.setUserSignature(userSignature);
        request.setUserPublicKey(userSignatureKey);

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
    @Order(6)
    public void testCreateAnotherState() throws ApiException, CborDeserializationException, CborSerializationException, InterruptedException {
        BuildTransactionRequest request = new BuildTransactionRequest();
        request.setType(TransactionType.DEFAULT);
        request.setAddress(userAccount.baseAddress());
        request.setCertificates(List.of(CertificateData.builder()
                .hash("c152f076fb4feeb1f934ac9b8c0606852e93d3a73fb2596a66c92e480e1268a5")
                .metadata("{\"hello\":\"world\"}")
                .algorithm("SHA256")
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
