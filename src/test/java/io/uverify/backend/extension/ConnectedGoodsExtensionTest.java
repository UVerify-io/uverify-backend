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

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.uverify.backend.CardanoBlockchainTest;
import io.uverify.backend.extension.dto.Item;
import io.uverify.backend.extension.dto.MintConnectedGoodsRequest;
import io.uverify.backend.extension.dto.MintConnectedGoodsResponse;
import io.uverify.backend.extension.dto.SocialHub;
import io.uverify.backend.extension.entity.SocialHubEntity;
import io.uverify.backend.extension.service.ConnectedGoodsService;
import io.uverify.backend.extension.validators.SocialHubDatum;
import io.uverify.backend.extension.validators.converter.SocialHubDatumConverter;
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
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.testcontainers.shaded.org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static io.uverify.backend.extension.dto.SocialHub.fromSocialHubEntity;
import static io.uverify.backend.extension.utils.ConnectedGoodUtils.*;

@EnabledIf(
        expression = "${extensions.connected-goods.enabled}",
        loadContext = true,
        reason = "Connected Goods extension must be enabled for this test"
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConnectedGoodsExtensionTest extends CardanoBlockchainTest {
    @Autowired
    private final ConnectedGoodsService connectedGoodsService;
    private final List<String> batchDirs = List.of("batch_1", "batch_2");
    private final int batchSize = 100;
    private final Map<String, String> mintingTransactionHashes = new HashMap<>();
    private final Map<String, String> batchIds = new HashMap<>();
    private final Account connectedGoodsServiceWallet;
    private Map<String, String> items;
    private String itemName;

    @Autowired
    public ConnectedGoodsExtensionTest(
            @LocalServerPort int port,
            @Value("${extensions.connected-goods.service-wallet.mnemonic}") String serviceWalletMnemonic,
            @Value("${extensions.connected-goods.service-wallet.address}") String serviceWalletAddress,
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
            ExtensionManager extensionManager,
            ConnectedGoodsService connectedGoodsService) {
        super(testServiceUserMnemonic, testUserMnemonic, feeReceiverMnemonic, facilitatorMnemonic, cardanoBlockchainService, stateDatumService, bootstrapDatumService, uVerifyCertificateService, stateDatumRepository, bootstrapDatumRepository, certificateRepository, extensionManager,
                List.of(serviceWalletAddress));
        RestAssured.port = port;
        this.connectedGoodsServiceWallet = new Account(Networks.testnet(), serviceWalletMnemonic);

        this.connectedGoodsService = connectedGoodsService;
        this.connectedGoodsService.setBackendService(yaciCardanoContainer.getBackendService());
    }

    private void generateQRCode(String batchDir, String data, String filename) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        HashMap<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        BitMatrix bitMatrix = qrCodeWriter.encode(
                data,
                BarcodeFormat.QR_CODE,
                1200,
                1200,
                hints
        );

        String filePath = System.getProperty("user.home");
        Path path = Paths.get(filePath, ".uverify", "extension", "connected_goods", batchDir, "qr_codes", filename + ".png");
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
    }

    @Test
    @Order(1)
    public void generateItemsTest() throws IOException, WriterException {
        for (int i = 0; i < batchDirs.size(); i++) {
            readOrGenerateItems(batchDirs.get(i), i * batchSize);
        }
    }

    private void readOrGenerateItems(String batchDir, int offset) throws IOException, WriterException {
        String filePath = System.getProperty("user.home");
        Path path = Paths.get(filePath, ".uverify", "extension", "connected_goods", batchDir, "info.txt");

        items = new HashMap<>();
        if (!path.toFile().exists()) {
            boolean success = path.getParent().toFile().mkdirs();
            if (!success) {
                throw new RuntimeException("Failed to create directory: " + path);
            }
            items = generateItems(batchSize, "SHIRT", offset);
            List<String> lines = itemsToLines(items);
            Files.writeString(path, String.join("\n", lines), StandardCharsets.UTF_8);
        } else {
            Files.readString(path, StandardCharsets.UTF_8)
                    .lines()
                    .forEach(line -> {
                        String[] parts = line.split(",");
                        items.put(parts[0], parts[1]);
                    });
        }
        Path qrPath = Paths.get(filePath, ".uverify", "extension", "connected_goods", batchDir, "qr_codes");

        if (!qrPath.toFile().exists()) {
            boolean success = qrPath.toFile().mkdirs();
            if (!success) {
                throw new RuntimeException("Failed to create directory: " + qrPath);
            }

            String certificateHash = computeCertificateHashByConnectedGoodsDatum(items);
            for (Map.Entry<String, String> entry : items.entrySet()) {
                String assetName = entry.getKey();
                String url = "https://app.uverify.io/verify/" + certificateHash + "/1?item=" + entry.getValue();
                generateQRCode(batchDir, url, assetName);
            }
        }
    }

    private SocialHub requestUserInfo(String batchIdParam, String mintHash) {
        return given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/extension/connected-goods/" + batchIdParam + "/" + mintHash)
                .then()
                .extract()
                .as(SocialHub.class);
    }

    @Test
    @Order(2)
    public void testMintConnectedGoods() throws InterruptedException, ApiException, IOException, WriterException, CborSerializationException, CborDeserializationException {
        for (int i = 0; i < batchDirs.size(); i++) {
            readOrGenerateItems(batchDirs.get(i), i * batchSize);

            MintConnectedGoodsRequest mintConnectedGoodsRequest = MintConnectedGoodsRequest.builder()
                    .address(connectedGoodsServiceWallet.baseAddress())
                    .items(items.entrySet().stream().map(entry -> Item.builder()
                            .assetName(entry.getKey())
                            .password(entry.getValue())
                            .build()).toList())
                    .tokenName("BUIDLERS_FEST_2025")
                    .build();

            MintConnectedGoodsResponse response = given()
                    .contentType(ContentType.JSON)
                    .when()
                    .body(mintConnectedGoodsRequest)
                    .post("/api/v1/extension/connected-goods/mint/batch")
                    .then()
                    .extract()
                    .as(MintConnectedGoodsResponse.class);

            Result<String> result = cardanoBlockchainService.submitTransaction(Transaction.deserialize(
                    HexUtil.decodeHexString(response.getUnsignedTransaction())), connectedGoodsServiceWallet);

            if (result.isSuccessful()) {
                simulateYaciStoreBehavior(result.getValue());
                mintingTransactionHashes.put(batchDirs.get(i), result.getValue());
                batchIds.put(batchDirs.get(i), response.getBatchId());
            }

            System.out.println("Minting transaction hash: " + result.getValue());
            System.out.println("Batch ID: " + batchIds.get(batchDirs.get(i)));

            Assertions.assertTrue(result.isSuccessful());
        }
    }

    @Test
    @Order(3)
    public void testRequestItem() {
        String batchIdParam = batchIds.get(batchDirs.get(0)) + "," + batchIds.get(batchDirs.get(1));
        String password = items.get("SHIRT102");
        SocialHub socialHub = requestUserInfo(batchIdParam, password);

        Assertions.assertEquals(socialHub.getItemName(), "SHIRT102");
    }

    @Test
    @Order(4)
    public void testClaimConnectedGood() throws Exception {
        for (int i = 0; i < batchDirs.size(); i++) {
            readOrGenerateItems(batchDirs.get(i), i * batchSize);
            String assetName = "SHIRT" + String.format("%03d", (i * batchSize + 1));
            String password = items.get(assetName);

            String mintingTransactionHash = mintingTransactionHashes.get(batchDirs.get(i));

            String batchId = batchIds.get(batchDirs.get(i));
            SocialHubEntity socialHubEntity = connectedGoodsService.getSocialHubByBatchIdAndMintHash(batchId, applySHA3_256(password));

            Assertions.assertNotNull(socialHubEntity, "SocialHub should not be null");
            Assertions.assertEquals(socialHubEntity.getPassword(), applySHA3_256(password), "Password should match");

            SocialHubDatum socialHubDatum = socialHubEntity.toSocialHubDatum();
            socialHubDatum.setName(Optional.of("Mr. One".getBytes(StandardCharsets.UTF_8)));
            socialHubDatum.setGithub(Optional.of("dev_mr_one".getBytes(StandardCharsets.UTF_8)));

            Transaction unsignedTransaction = connectedGoodsService.claim(assetName, password,
                    mintingTransactionHash, 0, socialHubDatum, facilitatorAccount.baseAddress());

            Result<String> result = cardanoBlockchainService.submitTransaction(unsignedTransaction, facilitatorAccount);

            if (result.isSuccessful()) {
                simulateYaciStoreBehavior(result.getValue());
            }

            mintingTransactionHashes.put(batchDirs.get(i), result.getValue());
            Assertions.assertTrue(result.isSuccessful());

            itemName = assetName;

            socialHubEntity = connectedGoodsService.getSocialHubByBatchIdAndMintHash(batchId, applySHA3_256(password));
            SocialHub socialHub = connectedGoodsService.decryptSocialHub(fromSocialHubEntity(socialHubEntity, Networks.preprod()), password);
            Assertions.assertNotNull(socialHub, "SocialHub should not be null");
            Assertions.assertEquals(socialHub.getName(), "Mr. One", "Name should match");
        }
    }

    @Test
    @Order(5)
    public void updateSocialHub() throws Exception {
        String mintingTransactionHash = mintingTransactionHashes.get(batchDirs.get(1));
        Result<Utxo> output = yaciCardanoContainer.getUtxoService().getTxOutput(mintingTransactionHash, 0);
        Utxo utxo = output.getValue();

        SocialHubDatum socialHubDatum = new SocialHubDatumConverter().deserialize(utxo.getInlineDatum());
        String password = items.get(itemName);

        socialHubDatum.setName(Optional.of("JonathanMaxwellAnderson".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setGithub(Optional.of("jonathan-maxwell-dev-2025".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setDiscord(Optional.of("jonathan_anderson#9876".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setInstagram(Optional.of("jonathan.anderson.dev".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setYoutube(Optional.of("jonathan_anderson_channel".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setReddit(Optional.of("u_jonathan_maxwell_dev".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setTelegram(Optional.of("jonathan_maxwell_bot".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setX(Optional.of("jonathan_maxwell_x".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setAdahandle(Optional.of("jonathan_maxwell_cardano".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setWebsite(Optional.empty());
        socialHubDatum.setEmail(Optional.of("jonathan.maxwell.anderson.testing@example.com".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setSubtitle(Optional.of("Senior QA Engineer at Maxwell DevOps".getBytes(StandardCharsets.UTF_8)));
        socialHubDatum.setLinkedin(Optional.of("jonathan-maxwell-anderson-qa".getBytes(StandardCharsets.UTF_8)));

        Transaction unsignedTransaction = connectedGoodsService.update(socialHubDatum,
                mintingTransactionHash, 0, facilitatorAccount.baseAddress(), password);

        Result<String> result = cardanoBlockchainService.submitTransaction(unsignedTransaction, facilitatorAccount);

        if (result.isSuccessful()) {
            simulateYaciStoreBehavior(result.getValue());
        }

        Assertions.assertTrue(result.isSuccessful());

        String batchId = batchIds.get(batchDirs.get(1));

        SocialHubEntity socialHubEntity = connectedGoodsService.getSocialHubByBatchIdAndMintHash(batchId, applySHA3_256(password));
        SocialHub socialHub = connectedGoodsService.decryptSocialHub(fromSocialHubEntity(socialHubEntity, Networks.preprod()), password);

        Assertions.assertNotNull(socialHub, "SocialHub should not be null");
        Assertions.assertEquals(socialHub.getName(), "JonathanMaxwellAnderson", "Name should match");
        Assertions.assertEquals(socialHub.getGithub(), "jonathan-maxwell-dev-2025", "GitHub should match");
        Assertions.assertEquals(socialHub.getDiscord(), "jonathan_anderson#9876", "Discord should match");
        Assertions.assertEquals(socialHub.getInstagram(), "jonathan.anderson.dev", "Instagram should match");
        Assertions.assertEquals(socialHub.getYoutube(), "jonathan_anderson_channel", "YouTube should match");
        Assertions.assertEquals(socialHub.getReddit(), "u_jonathan_maxwell_dev", "Reddit should match");
        Assertions.assertEquals(socialHub.getTelegram(), "jonathan_maxwell_bot", "Telegram should match");
        Assertions.assertEquals(socialHub.getX(), "jonathan_maxwell_x", "X should match");
        Assertions.assertEquals(socialHub.getAdaHandle(), "jonathan_maxwell_cardano", "Ada handle should match");
        Assertions.assertEquals(socialHub.getEmail(), "jonathan.maxwell.anderson.testing@example.com", "Email should match");
        Assertions.assertEquals(socialHub.getSubtitle(), "Senior QA Engineer at Maxwell DevOps", "Subtitle should match");
        Assertions.assertEquals(socialHub.getLinkedin(), "jonathan-maxwell-anderson-qa", "LinkedIn should match");
    }

    @Test
    @Order(6)
    public void updateOthersSocialHubShouldFail() throws ApiException, CborSerializationException, InterruptedException {
        String mintingTransactionHash = mintingTransactionHashes.get(batchDirs.get(0));
        byte[] ownerCredential = userAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow();
        SocialHubDatum socialHubDatum = SocialHubDatum.builder()
                .owner(ownerCredential)
                .batchId(Hex.decode("09a401"))
                .picture(Optional.of("github".getBytes(StandardCharsets.UTF_8)))
                .name(Optional.empty())
                .subtitle(Optional.empty())
                .x(Optional.empty())
                .telegram(Optional.empty())
                .instagram(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .discord(Optional.of("take_over#1234".getBytes(StandardCharsets.UTF_8)))
                .reddit(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .youtube(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .linkedin(Optional.empty())
                .github(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .website(Optional.empty())
                .email(Optional.empty())
                .adahandle(Optional.empty())
                .build();

        String password = items.get("SHIRT200");

        Transaction unsignedTransaction = null;
        try {
            unsignedTransaction = connectedGoodsService.update(socialHubDatum, mintingTransactionHash, 0, facilitatorAccount.baseAddress(), password);
            Result<String> result = cardanoBlockchainService.submitTransaction(unsignedTransaction, facilitatorAccount);
            Assertions.assertFalse(result.isSuccessful());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        Assertions.assertNull(unsignedTransaction);
    }

    @Test
    @Order(7)
    public void transferSocialHub() throws ApiException, InterruptedException, CborSerializationException {
        String mintingTransactionHash = mintingTransactionHashes.get(batchDirs.get(0));
        byte[] ownerCredential = userAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow();
        SocialHubDatum socialHubDatum = SocialHubDatum.builder()
                .owner(ownerCredential)
                .batchId(Hex.decode("09a401"))
                .picture(Optional.of("github".getBytes(StandardCharsets.UTF_8)))
                .name(Optional.empty())
                .subtitle(Optional.empty())
                .x(Optional.empty())
                .telegram(Optional.empty())
                .instagram(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .discord(Optional.of("take_over#1234".getBytes(StandardCharsets.UTF_8)))
                .reddit(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .youtube(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .linkedin(Optional.empty())
                .github(Optional.of("take_over".getBytes(StandardCharsets.UTF_8)))
                .website(Optional.empty())
                .email(Optional.empty())
                .adahandle(Optional.empty())
                .build();

        String password = items.get("SHIRT200");

        Transaction unsignedTransaction = null;
        try {
            unsignedTransaction = connectedGoodsService.transfer(socialHubDatum, mintingTransactionHash, 0, facilitatorAccount.baseAddress(), password);
            Result<String> result = cardanoBlockchainService.submitTransaction(unsignedTransaction, facilitatorAccount);
            Assertions.assertFalse(result.isSuccessful());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        Assertions.assertNull(unsignedTransaction);

    }
}
