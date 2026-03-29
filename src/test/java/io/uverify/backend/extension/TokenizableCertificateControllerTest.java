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

package io.uverify.backend.extension;

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
import io.restassured.response.Response;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.dto.BuildTransactionRequest;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.ProxyInitResponse;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.TransactionType;
import io.uverify.backend.extension.dto.tokenizable.CertificateStatusResponse;
import io.uverify.backend.extension.dto.tokenizable.TokenizableBuildRequest;
import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.service.FractionizedCertificateService;
import io.uverify.backend.extension.service.TokenizableCertificateService;
import io.uverify.backend.extension.validators.tokenizable.TokenizableConfig;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.LibraryRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.service.*;
import io.uverify.backend.util.ValidatorHelper;
import io.uverify.backend.util.ValidatorUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.util.List;

import static io.restassured.RestAssured.given;

@EnabledIf(
        expression = "${extensions.tokenizable-certificate.enabled}",
        loadContext = true,
        reason = "Tokenizable Certificate extension must be enabled for this test"
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenizableCertificateControllerTest extends CardanoBlockchainTest {

    /**
     * First node inserted during Init (deployer's cert — must sort before CERT_KEY).
     */
    private static final String INIT_CERT_KEY = "aabb000011223344aabb000011223344";
    private static final String INIT_ASSET_NAME = "494e4954"; // "INIT"
    /**
     * Second node inserted by the allowed inserter (userAccount).
     */
    private static final String CERT_KEY = "ddccddcc11223344ddccddcc11223344";
    private static final String TC_ASSET_NAME_HEX = "544f4b454e"; // "TOKEN"
    // Shared state between tests
    private static String initTxHash;
    private static int initOutputIndex;
    private final TokenizableCertificateService tokenizableCertificateService;

    @Autowired
    public TokenizableCertificateControllerTest(
            @LocalServerPort int port,
            @Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
            @Value("${cardano.test.user.mnemonic}") String testUserMnemonic,
            @Value("${cardano.service.fee.receiver.mnemonic}") String feeReceiverMnemonic,
            @Value("${cardano.facilitator.user.mnemonic}") String facilitatorMnemonic,
            CardanoBlockchainService cardanoBlockchainService,
            StateDatumService stateDatumService,
            BootstrapDatumService bootstrapDatumService,
            UVerifyCertificateService uVerifyCertificateService,
            FractionizedCertificateService fractionizedCertificateService,
            StateDatumRepository stateDatumRepository,
            BootstrapDatumRepository bootstrapDatumRepository,
            CertificateRepository certificateRepository,
            LibraryRepository libraryRepository,
            ExtensionManager extensionManager,
            ValidatorHelper validatorHelper,
            TokenizableCertificateService tokenizableCertificateService,
            LibraryService libraryService) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic,
                cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService,
                fractionizedCertificateService,
                stateDatumRepository, bootstrapDatumRepository, certificateRepository, libraryRepository,
                extensionManager, validatorHelper, libraryService, List.of());
        RestAssured.port = port;
        this.tokenizableCertificateService = tokenizableCertificateService;
        this.tokenizableCertificateService.setBackendService(yaciCardanoContainer.getBackendService());
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

        waitForTransaction(result.getValue());
        validatorHelper.setProxy(buildTransactionResponse.getProxyTxHash(), buildTransactionResponse.getProxyOutputIndex());
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

        if (result.isSuccessful()) {
            Transaction signedTransaction = TransactionSigner.INSTANCE.sign(transaction, serviceAccount.hdKeyPair());
            simulateYaciStoreBehavior(result.getValue(), signedTransaction);
        }

        Utxo proxyLibraryUtxo = libraryService.getProxyLibraryUtxo();
        Utxo stateLibraryUtxo = libraryService.getStateLibraryUtxo();

        Assertions.assertNotNull(proxyLibraryUtxo);
        Assertions.assertNotNull(stateLibraryUtxo);
    }

    @Test
    @Order(2)
    public void setupBootstrapToken() throws CborSerializationException, ApiException, InterruptedException, CborException, AddressExcepion {
        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName("tc_test_bootstrap_token");
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
    public void extensionRegistryShouldListTokenizableAsEnabled() {
        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/extensions")
                .then()
                .statusCode(200)
                .extract()
                .response();

        Assertions.assertEquals(Boolean.TRUE, response.jsonPath().getBoolean("tokenizable-certificate"));
    }

    @Test
    @Order(4)
    public void initTokenizableContract() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion, CborDeserializationException {
        // Pick a UTxO from the service account to use as the one-shot init UTxO
        Result<List<Utxo>> utxoResult = yaciCardanoContainer.getUtxoService().getUtxos(serviceAccount.baseAddress(), 100, 1);
        Assertions.assertTrue(utxoResult.isSuccessful() && !utxoResult.getValue().isEmpty());
        Utxo selectedUtxo = utxoResult.getValue().get(0);

        initTxHash = selectedUtxo.getTxHash();
        initOutputIndex = selectedUtxo.getOutputIndex();

        String deployerCredential = HexUtil.encodeHexString(
                serviceAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        String inserterCredential = HexUtil.encodeHexString(
                userAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());

        String uverifyValidatorHash = ValidatorUtils.validatorToScriptHash(
                validatorHelper.getParameterizedUVerifyStateContract());

        TokenizableConfig config = TokenizableConfig.builder()
                .uverifyValidatorHash(uverifyValidatorHash)
                .allowedInserters(List.of(inserterCredential))
                .deployer(deployerCredential)
                .cip68ScriptAddress(null)
                .build();

        String deployerPubKeyHash = HexUtil.encodeHexString(
                serviceAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());

        // Init always creates HEAD + first node in one atomic tx
        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.CREATE);
        buildRequest.setSenderAddress(serviceAccount.baseAddress());
        buildRequest.setInitUtxoTxHash(initTxHash);
        buildRequest.setInitUtxoOutputIndex(initOutputIndex);
        buildRequest.setConfig(config);
        buildRequest.setKey(INIT_CERT_KEY);
        buildRequest.setOwnerPubKeyHash(deployerPubKeyHash);
        buildRequest.setAssetName(INIT_ASSET_NAME);
        buildRequest.setBootstrapTokenName("tc_test_bootstrap_token");

        String unsignedTxCbor = given()
                .contentType(ContentType.JSON)
                .body(buildRequest)
                .when()
                .post("/api/v1/extension/tokenizable-certificate/build")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Remove surrounding quotes from JSON string response
        unsignedTxCbor = unsignedTxCbor.replaceAll("^\"|\"$", "");

        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(unsignedTxCbor));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, serviceAccount);
        Assertions.assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }
    }

    @Test
    @Order(5)
    public void statusShouldReturnNotFound() {
        String unknownKey = "deadbeefdeadbeef";

        CertificateStatusResponse status = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/extension/tokenizable-certificate/status/{key}"
                                + "?initUtxoTxHash={txHash}&initUtxoOutputIndex={idx}",
                        unknownKey, initTxHash, initOutputIndex)
                .then()
                .statusCode(200)
                .extract()
                .as(CertificateStatusResponse.class);

        Assertions.assertFalse(status.isExists());
    }

    @Test
    @Order(6)
    public void inserterAddsFirstCertificate() throws ApiException, CborSerializationException,
            InterruptedException, CborDeserializationException {
        String ownerPubKeyHash = HexUtil.encodeHexString(
                userAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());

        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.CREATE);
        buildRequest.setSenderAddress(userAccount.baseAddress());
        buildRequest.setKey(CERT_KEY);
        buildRequest.setOwnerPubKeyHash(ownerPubKeyHash);
        buildRequest.setAssetName(TC_ASSET_NAME_HEX);
        buildRequest.setInitUtxoTxHash(initTxHash);
        buildRequest.setInitUtxoOutputIndex(initOutputIndex);
        buildRequest.setBootstrapTokenName("tc_test_bootstrap_token");

        String unsignedTxCbor = given()
                .contentType(ContentType.JSON)
                .body(buildRequest)
                .when()
                .post("/api/v1/extension/tokenizable-certificate/build")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        unsignedTxCbor = unsignedTxCbor.replaceAll("^\"|\"$", "");
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(unsignedTxCbor));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);
        Assertions.assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }
    }

    @Test
    @Order(7)
    public void statusShouldShowCertificateExists() {
        CertificateStatusResponse status = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/extension/tokenizable-certificate/status/{key}"
                                + "?initUtxoTxHash={txHash}&initUtxoOutputIndex={idx}",
                        CERT_KEY, initTxHash, initOutputIndex)
                .then()
                .statusCode(200)
                .extract()
                .as(CertificateStatusResponse.class);

        Assertions.assertTrue(status.isExists());
        Assertions.assertFalse(status.isClaimed());
    }

    @Test
    @Order(8)
    public void userRedeemsCertificate() throws ApiException, CborSerializationException,
            InterruptedException, CborDeserializationException {
        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.REDEEM);
        buildRequest.setSenderAddress(userAccount.baseAddress());
        buildRequest.setKey(CERT_KEY);
        buildRequest.setInitUtxoTxHash(initTxHash);
        buildRequest.setInitUtxoOutputIndex(initOutputIndex);

        String unsignedTxCbor = given()
                .contentType(ContentType.JSON)
                .body(buildRequest)
                .when()
                .post("/api/v1/extension/tokenizable-certificate/build")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        unsignedTxCbor = unsignedTxCbor.replaceAll("^\"|\"$", "");
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(unsignedTxCbor));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, userAccount);
        Assertions.assertTrue(result.isSuccessful());

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }
    }

    @Test
    @Order(9)
    public void redeemingAlreadyRedeemedCertificateShouldFail() {
        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.REDEEM);
        buildRequest.setSenderAddress(userAccount.baseAddress());
        buildRequest.setKey(CERT_KEY);
        buildRequest.setInitUtxoTxHash(initTxHash);
        buildRequest.setInitUtxoOutputIndex(initOutputIndex);

        given()
                .contentType(ContentType.JSON)
                .body(buildRequest)
                .when()
                .post("/api/v1/extension/tokenizable-certificate/build")
                .then()
                .statusCode(400);
    }
}
