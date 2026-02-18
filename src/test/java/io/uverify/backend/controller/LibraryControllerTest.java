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
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.dto.BuildTransactionRequest;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.LibraryDeploymentResponse;
import io.uverify.backend.dto.ProxyInitResponse;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.TransactionType;
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

import java.util.List;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LibraryControllerTest extends CardanoBlockchainTest {

    @Autowired
    public LibraryControllerTest(
            @LocalServerPort int port,
            @Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
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
            ValidatorHelper validatorHelper,
            ExtensionManager extensionManager,
            LibraryService libraryService) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic, cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService, stateDatumRepository, bootstrapDatumRepository, certificateRepository, extensionManager, validatorHelper, libraryService, List.of());
        RestAssured.port = port;
    }

    @Test
    @Order(0)
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

        Assertions.assertEquals(BuildStatusCode.SUCCESS, buildTransactionResponse.getStatus().getCode());

        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(buildTransactionResponse.getUnsignedProxyTransaction()));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);
        Assertions.assertTrue(result.isSuccessful());

        Thread.sleep(1000);

        validatorHelper.setProxy(buildTransactionResponse.getProxyTxHash(), buildTransactionResponse.getProxyOutputIndex());
        Thread.sleep(1000);
    }

    @Test
    @Order(1)
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

        Utxo proxyLibraryUtxo = libraryService.getProxyLibraryUtxo();
        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();

        Assertions.assertNotNull(proxyLibraryUtxo);
        Assertions.assertNotNull(stateLibraryUtxo);
    }

    @Test
    @Order(2)
    public void testUndeployActiveContract() {
        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();
        Assertions.assertNotNull(stateLibraryUtxo);

        BuildTransactionResponse buildTransactionResponse = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/library/undeploy/" + stateLibraryUtxo.getTxHash() + "/" + stateLibraryUtxo.getOutputIndex())
                .then()
                .extract()
                .as(BuildTransactionResponse.class);

        Assertions.assertEquals(BuildStatusCode.ERROR, buildTransactionResponse.getStatus().getCode());
    }

    @Test
    @Order(3)
    public void upgradeProxyContract() throws ApiException, CborSerializationException, CborDeserializationException, InterruptedException, CborException, AddressExcepion {
        String nextVersionStateContract = "5904af010100229800aba4aba2aba1aba0aab9faab9eaab9dab9cab9a9bae00248888888888cc8966002646465300130093754003300d00398068012444b30013370e9002001c4c96600200515980099b8748000c034dd500144c9660020030098992cc00400601500a805402a26644b300100180644c96600200300d806c03601b13259800980c001c6600260286ea80264464b30013370e9001180b1baa0018a5eb7bdb18226eacc068c05cdd5000a02832330010010032259800800c5300103d87a8000899192cc004cdc8802800c56600266e3c014006266e9520003301c301a0024bd7045300103d87a8000405d133004004301e003405c6eb8c060004c06c00501948c060c064005222332259800acc0056600200514a31001405914a31533017491436f72207b0a2020696e766f6b655f6d696e745f76616c69646174696f6e2c0a2020696e766f6b655f7370656e645f76616c69646174696f6e2c0a7d203f2046616c73650014a080b22b300159800acc00400a264b300100180ac4c966002603e005159800acc004cdc39bad301b0014800a29462a6603292113616d6f756e74203d3d2031203f2046616c73650014a080c22b30013371e6eb8c068004dd7180f180d9baa00f8a518a9980ca492961737365745f6e616d65203d3d2072656465656d65722e746f6b656e5f6e616d65203f2046616c73650014a080c22941018405901c180e800a0363300437566038603a603a603a603a60326ea803805629450164528c54cc05d2415576616c69646174655f6d696e742872656465656d65722c2070726f78795f706f6c6963795f69642c207472616e73616374696f6e2c20696e766f6b655f6d696e745f76616c69646174696f6e29203f2046616c73650014a080b22b300159800800c4cdc79bae30033019375401a9110d48656c6c6f2c20576f726c6421008a51405914a315330174915776616c69646174655f7370656e642872656465656d65722c2070726f78795f706f6c6963795f69642c207472616e73616374696f6e2c20696e766f6b655f7370656e645f76616c69646174696f6e29203f2046616c73650014a080b229410164528202c9800acc004cc008dd5980d180d980d980d980d801809c528c5282030a50a51405064660020026eb0c06c010896600200314a115980099baf301c30193754603860326ea8c00cc064dd5180e00099ba548008cc06cdd480aa5eb822946266004004603a00280b101a201c80a8dd7000a0303015001404c6eb8004c0500090151809000a020300e3754005008402d00880440220108098c040c034dd5002456600266e1d2006003899192cc004cdc3a4000601c6ea8c048c04c00a29462c8060dd6980880098069baa0048b20144028300c300d001300c0013007375401b149a2a6600a92011856616c696461746f722072657475726e65642066616c7365001365640102a6600692016d657870656374205b506169722861737365745f6e616d652c20616d6f756e74295d203d0a2020202020206d696e740a20202020202020207c3e206173736574732e746f6b656e7328706f6c6963795f6964290a20202020202020207c3e20646963742e746f5f70616972732829001615330034911472656465656d65723a204d7952656465656d6572001601";
        // Upgrades the proxy by another business logic contract.
        BuildTransactionResponse buildTransactionResponse = given()
                .contentType(ContentType.JSON)
                .body(nextVersionStateContract)
                .when()
                .post("/api/v1/library/upgrade/proxy")
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
    @Order(4)
    public void testGetLibraryEntries() {
        LibraryDeploymentResponse libraryDeploymentResponse = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/library/deployments")
                .then()
                .extract()
                .as(LibraryDeploymentResponse.class);

        Assertions.assertEquals(3, libraryDeploymentResponse.getEntries().size());
    }

    @Test
    @Order(5)
    public void testUndeployAllInactiveEntries() throws CborDeserializationException, CborSerializationException, ApiException {
        BuildTransactionResponse undeployEntriesResponse = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/library/undeploy/unused")
                .then()
                .extract()
                .as(BuildTransactionResponse.class);

        Assertions.assertEquals(BuildStatusCode.SUCCESS, undeployEntriesResponse.getStatus().getCode());

        String cborUnsignedTransaction = undeployEntriesResponse.getUnsignedTransaction();

        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(cborUnsignedTransaction));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);

        Assertions.assertTrue(result.isSuccessful());
    }
}
