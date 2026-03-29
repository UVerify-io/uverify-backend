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

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.cip.cip30.CIP30DataSigner;
import com.bloxbean.cardano.client.cip.cip30.DataSignError;
import com.bloxbean.cardano.client.cip.cip30.DataSignature;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.util.HexUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.dto.FaucetChallengeRequest;
import io.uverify.backend.dto.FaucetChallengeResponse;
import io.uverify.backend.dto.FaucetClaimRequest;
import io.uverify.backend.dto.FaucetClaimResponse;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.extension.service.FractionizedCertificateService;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.LibraryRepository;
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
public class FaucetControllerTest extends CardanoBlockchainTest {

    private final Account faucetAccount;
    private FaucetChallengeResponse challengeResponse;

    @Autowired
    public FaucetControllerTest(
            @LocalServerPort int port,
            @Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
            @Value("${cardano.test.user.mnemonic}") String testUserMnemonic,
            @Value("${cardano.service.fee.receiver.mnemonic}") String feeReceiverMnemonic,
            @Value("${cardano.facilitator.user.mnemonic}") String facilitatorMnemonic,
            @Value("${faucet.mnemonic}") String faucetMnemonic,
            CardanoBlockchainService cardanoBlockchainService,
            StateDatumService stateDatumService,
            BootstrapDatumService bootstrapDatumService,
            UVerifyCertificateService uVerifyCertificateService,
            FractionizedCertificateService fractionizedCertificateService,
            StateDatumRepository stateDatumRepository,
            BootstrapDatumRepository bootstrapDatumRepository,
            CertificateRepository certificateRepository,
            LibraryRepository libraryRepository,
            ValidatorHelper validatorHelper,
            ExtensionManager extensionManager,
            LibraryService libraryService) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic,
                cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService,
                fractionizedCertificateService,
                stateDatumRepository, bootstrapDatumRepository, certificateRepository, libraryRepository,
                extensionManager, validatorHelper, libraryService,
                List.of(Account.createFromMnemonic(Networks.testnet(), faucetMnemonic).baseAddress(),
                        Account.createFromMnemonic(Networks.testnet(), faucetMnemonic).baseAddress()));
        this.faucetAccount = Account.createFromMnemonic(Networks.testnet(), faucetMnemonic);
        RestAssured.port = port;
    }

    @Test
    @Order(1)
    public void testRequestFaucetChallenge() {
        FaucetChallengeRequest request = new FaucetChallengeRequest();
        request.setAddress(userAccount.baseAddress());

        this.challengeResponse = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/faucet/request")
                .then()
                .statusCode(200)
                .extract()
                .as(FaucetChallengeResponse.class);

        Assertions.assertEquals(userAccount.baseAddress(), this.challengeResponse.getAddress());
        Assertions.assertNotNull(this.challengeResponse.getMessage());
        Assertions.assertNotNull(this.challengeResponse.getSignature());
        Assertions.assertNotNull(this.challengeResponse.getTimestamp());
        Assertions.assertEquals(HttpStatus.OK, this.challengeResponse.getStatus());

        String expectedMessage = "[FAUCET_REQUEST@" + this.challengeResponse.getTimestamp() +
                "] Please sign this message with your private key to verify, " +
                "that you are the owner of the address " + userAccount.baseAddress() + ".";
        Assertions.assertEquals(expectedMessage, this.challengeResponse.getMessage());

        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();
        Assertions.assertTrue(edDSASigningProvider.verify(
                HexUtil.decodeHexString(this.challengeResponse.getSignature()),
                this.challengeResponse.getMessage().getBytes(),
                this.faucetAccount.publicKeyBytes()));
    }

    @Test
    @Order(2)
    public void testClaimFaucetFunds() throws DataSignError, InterruptedException, ApiException {
        DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(
                userAccount.getBaseAddress().getBytes(),
                this.challengeResponse.getMessage().getBytes(),
                userAccount);

        FaucetClaimRequest claimRequest = new FaucetClaimRequest();
        claimRequest.setAddress(this.challengeResponse.getAddress());
        claimRequest.setMessage(this.challengeResponse.getMessage());
        claimRequest.setSignature(this.challengeResponse.getSignature());
        claimRequest.setTimestamp(this.challengeResponse.getTimestamp());
        claimRequest.setUserSignature(dataSignature.signature());
        claimRequest.setUserPublicKey(dataSignature.key());

        FaucetClaimResponse claimResponse = given()
                .contentType(ContentType.JSON)
                .body(claimRequest)
                .when()
                .post("/api/v1/faucet/claim")
                .then()
                .statusCode(200)
                .extract()
                .as(FaucetClaimResponse.class);

        Assertions.assertEquals(HttpStatus.OK, claimResponse.getStatus());
        Assertions.assertNotNull(claimResponse.getTxHash());
        Assertions.assertFalse(claimResponse.getTxHash().isBlank());

        // Verify the faucet produced the expected number of separate UTxOs instead
        // of merging them into one (mergeOutputs must be false in sendAda).
        waitForTransaction(claimResponse.getTxHash());
        Result<List<com.bloxbean.cardano.client.api.model.Utxo>> utxosResult =
                yaciCardanoContainer.getBackendService().getUtxoService()
                        .getUtxos(userAccount.baseAddress(), 100, 1);
        long faucetUtxos = utxosResult.getValue().stream()
                .filter(u -> u.getTxHash().equals(claimResponse.getTxHash()))
                .count();
        Assertions.assertEquals(3, faucetUtxos,
                "Faucet should produce 3 separate UTxOs (mergeOutputs=false)");
    }

    @Test
    @Order(3)
    public void testCooldownPreventsChallenge() {
        FaucetChallengeRequest request = new FaucetChallengeRequest();
        request.setAddress(userAccount.baseAddress());

        FaucetChallengeResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/faucet/request")
                .then()
                .statusCode(429)
                .extract()
                .as(FaucetChallengeResponse.class);

        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatus());
        Assertions.assertNotNull(response.getError());
    }

    @Test
    @Order(4)
    public void testClaimWithInvalidUserSignature() {
        // Use feeReceiverAccount (not in cooldown) to get a fresh challenge
        FaucetChallengeRequest challengeRequest = new FaucetChallengeRequest();
        challengeRequest.setAddress(feeReceiverAccount.baseAddress());

        FaucetChallengeResponse challenge = given()
                .contentType(ContentType.JSON)
                .body(challengeRequest)
                .when()
                .post("/api/v1/faucet/request")
                .then()
                .statusCode(200)
                .extract()
                .as(FaucetChallengeResponse.class);

        FaucetClaimRequest claimRequest = new FaucetClaimRequest();
        claimRequest.setAddress(challenge.getAddress());
        claimRequest.setMessage(challenge.getMessage());
        claimRequest.setSignature(challenge.getSignature());
        claimRequest.setTimestamp(challenge.getTimestamp());
        claimRequest.setUserSignature("deadbeefdeadbeef"); // invalid CIP-30 signature
        claimRequest.setUserPublicKey("cafecafecafecafe");  // invalid public key

        FaucetClaimResponse claimResponse = given()
                .contentType(ContentType.JSON)
                .body(claimRequest)
                .when()
                .post("/api/v1/faucet/claim")
                .then()
                .statusCode(400)
                .extract()
                .as(FaucetClaimResponse.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, claimResponse.getStatus());
        Assertions.assertNotNull(claimResponse.getError());
    }
}
