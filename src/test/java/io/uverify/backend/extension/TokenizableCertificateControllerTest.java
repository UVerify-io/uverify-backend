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
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.dto.CertificateData;
import io.uverify.backend.extension.dto.tokenizable.CertificateStatusResponse;
import io.uverify.backend.extension.dto.tokenizable.TokenizableBuildRequest;
import io.uverify.backend.extension.enums.ExtensionTransactionType;
import io.uverify.backend.extension.service.FractionizedCertificateService;
import io.uverify.backend.extension.service.TokenizableCertificateService;
import io.uverify.backend.extension.validators.tokenizable.TokenizableConfig;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.LibraryRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.sandbox.SandboxContainers;
import io.uverify.backend.service.*;
import io.uverify.backend.util.ValidatorHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.util.List;
import java.util.Optional;

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
     * <p>
     * private static final String INIT_CERT_KEY = "3898654fa33d1bed95ea1541bb77ddc6621e0a78150c55d1de8557ac163b89ea";
     * private static final String INIT_ASSET_NAME = "314750757265476f6c64"; // "INIT"
     */
    private static final String INIT_CERT_KEY = "3898654fa33d1bed95ea1541bb77ddc6621e0a78150c55d1de8557ac163b89ea";
    private static final String INIT_ASSET_NAME = "314750757265476f6c64"; // "INIT"
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
            Optional<FractionizedCertificateService> fractionizedCertificateService,
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
                extensionManager, validatorHelper, libraryService);
        RestAssured.port = port;
        this.tokenizableCertificateService = tokenizableCertificateService;
    }

    @Test
    @Order(1)
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
    @Order(2)
    public void initTokenizableContract() throws ApiException, CborSerializationException, InterruptedException, CborException, AddressExcepion, CborDeserializationException {
        // Pick a UTxO from the service account to use as the one-shot init UTxO
        BFBackendService bfBackendService = new BFBackendService(SandboxContainers.YANO.getBlockfrostBaseUrl(), "test");
        Result<List<Utxo>> utxoResult = bfBackendService.getUtxoService().getUtxos(serviceAccount.baseAddress(), 100, 1);
        Assertions.assertTrue(utxoResult.isSuccessful() && !utxoResult.getValue().isEmpty());
        Utxo selectedUtxo = utxoResult.getValue().get(0);

        initTxHash = selectedUtxo.getTxHash();
        initOutputIndex = selectedUtxo.getOutputIndex();

        String deployerCredential = HexUtil.encodeHexString(
                serviceAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        String inserterCredential = HexUtil.encodeHexString(
                userAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());

        TokenizableConfig config = TokenizableConfig.builder()
                .allowedInserters(List.of(inserterCredential))
                .deployer(deployerCredential)
                .build();

        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.CREATE);
        buildRequest.setSenderAddress(serviceAccount.baseAddress());
        buildRequest.setInitUtxoTxHash(initTxHash);
        buildRequest.setInitUtxoOutputIndex(initOutputIndex);
        buildRequest.setConfig(config);
        buildRequest.setCertificate(CertificateData.builder().hash(INIT_CERT_KEY).build());
        buildRequest.setOwnerPubKeyHash(deployerCredential);
        buildRequest.setAssetName(INIT_ASSET_NAME);

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

        waitForTransaction(result.getValue());
        awaitIndexed(() -> !stateDatumService.findByOwner(serviceAccount.baseAddress(), 2).isEmpty());
    }

    @Test
    @Order(3)
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
    @Order(4)
    public void inserterAddsFirstCertificate() throws ApiException, CborSerializationException,
            InterruptedException, CborDeserializationException {
        String ownerPubKeyHash = HexUtil.encodeHexString(
                userAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());

        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.CREATE);
        buildRequest.setSenderAddress(userAccount.baseAddress());
        buildRequest.setCertificate(CertificateData.builder().hash(CERT_KEY).build());
        buildRequest.setOwnerPubKeyHash(ownerPubKeyHash);
        buildRequest.setAssetName(TC_ASSET_NAME_HEX);
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

        waitForTransaction(result.getValue());
    }

    @Test
    @Order(5)
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
    @Order(6)
    public void userRedeemsCertificate() throws ApiException, CborSerializationException,
            InterruptedException, CborDeserializationException {
        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.REDEEM);
        buildRequest.setSenderAddress(userAccount.baseAddress());
        buildRequest.setCertificate(CertificateData.builder().hash(CERT_KEY).build());
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

        waitForTransaction(result.getValue());
    }

    @Test
    @Order(7)
    public void redeemingAlreadyRedeemedCertificateShouldFail() {
        TokenizableBuildRequest buildRequest = new TokenizableBuildRequest();
        buildRequest.setType(ExtensionTransactionType.REDEEM);
        buildRequest.setSenderAddress(userAccount.baseAddress());
        buildRequest.setCertificate(CertificateData.builder().hash(CERT_KEY).build());
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
