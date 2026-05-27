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

package io.uverify.backend;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.extension.service.FractionizedCertificateService;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.LibraryRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.sandbox.SandboxContainers;
import io.uverify.backend.sandbox.SandboxResetExtension;
import io.uverify.backend.service.*;
import io.uverify.backend.util.ValidatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.awaitility.Awaitility.await;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("devnet")
@ExtendWith(SandboxResetExtension.class)
public class CardanoBlockchainTest {

    protected final Account serviceAccount;
    protected final Account userAccount;
    protected final Account feeReceiverAccount;
    protected final Account facilitatorAccount;

    @Autowired
    protected final CardanoBlockchainService cardanoBlockchainService;
    @Autowired
    protected final StateDatumService stateDatumService;
    @Autowired
    protected final BootstrapDatumService bootstrapDatumService;
    @Autowired
    protected final UVerifyCertificateService uVerifyCertificateService;
    @Autowired
    protected final StateDatumRepository stateDatumRepository;
    @Autowired
    protected final BootstrapDatumRepository bootstrapDatumRepository;
    @Autowired
    protected final CertificateRepository certificateRepository;
    @Autowired
    protected final LibraryRepository libraryRepository;
    @Autowired
    protected final ExtensionManager extensionManager;
    @Autowired
    protected final ValidatorHelper validatorHelper;
    @Autowired
    protected final LibraryService libraryService;
    protected final Optional<FractionizedCertificateService> fractionizedCertificateService;

    @Autowired
    public CardanoBlockchainTest(@Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
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
                                 LibraryService libraryService) {
        this.cardanoBlockchainService = cardanoBlockchainService;
        this.libraryService = libraryService;
        this.stateDatumService = stateDatumService;
        this.bootstrapDatumService = bootstrapDatumService;
        this.uVerifyCertificateService = uVerifyCertificateService;
        this.fractionizedCertificateService = fractionizedCertificateService;
        this.stateDatumRepository = stateDatumRepository;
        this.bootstrapDatumRepository = bootstrapDatumRepository;
        this.certificateRepository = certificateRepository;
        this.libraryRepository = libraryRepository;
        this.extensionManager = extensionManager;
        this.validatorHelper = validatorHelper;

        serviceAccount = Account.createFromMnemonic(Networks.testnet(), testServiceUserMnemonic);
        userAccount = Account.createFromMnemonic(Networks.testnet(), testUserMnemonic);
        feeReceiverAccount = Account.createFromMnemonic(Networks.testnet(), feeReceiverMnemonic);
        facilitatorAccount = Account.createFromMnemonic(Networks.testnet(), facilitatorMnemonic);
    }

    @DynamicPropertySource
    static void sandboxProperties(DynamicPropertyRegistry registry) {
        registry.add("REMOTE_NODE_URL", SandboxContainers.YANO::getHost);
        registry.add("REMOTE_NODE_PORT", SandboxContainers.YANO::getN2NPort);
        registry.add("PROTOCOL_MAGIC", () -> "42");
        registry.add("YACI_STORE_AUTO_START", () -> "true");
        registry.add("store.cardano.protocol-magic", () -> "42");
        registry.add("store.cardano.sync-start-slot", () -> "0");
        registry.add("store.cardano.sync-start-blockhash", () -> "");

        registry.add("BLOCKFROST_BASE_URL", SandboxContainers.YANO::getBlockfrostBaseUrl);
        registry.add("BLOCKFROST_PROJECT_ID", () -> "test");
        registry.add("CARDANO_BACKEND_TYPE", () -> "blockfrost");

        registry.add("proxy.transaction-hash", () -> "0667d4932b2a2ae6259a5ff1e0b78b8b650568980b1d95e5bd859c2a019bcc0e");
        registry.add("proxy.output-index", () -> "0");

        registry.add("spring.datasource.url", SandboxContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", SandboxContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", SandboxContainers.POSTGRES::getPassword);
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.flyway.locations[0]", () -> "classpath:db/store/{vendor}");
        registry.add("spring.flyway.locations[1]", () -> "classpath:db/migration/postgres/uverify");

        String genesisBase = "classpath:genesis/devnet/";
        registry.add("store.cardano.byron-genesis-file", () -> genesisBase + "byron-genesis.json");
        registry.add("store.cardano.shelley-genesis-file", () -> genesisBase + "shelley-genesis.json");
        registry.add("store.cardano.alonzo-genesis-file", () -> genesisBase + "alonzo-genesis.json");
        registry.add("store.cardano.conway-genesis-file", () -> genesisBase + "conway-genesis.json");
    }

    @BeforeAll
    public void fundTestAccounts() throws Exception {
        SandboxContainers.YANO.fundAddress(serviceAccount.baseAddress(), 230_000_000_000L);
        SandboxContainers.YANO.fundAddress(userAccount.baseAddress(), 10_000_000_000L);
        SandboxContainers.YANO.fundAddress(feeReceiverAccount.baseAddress(), 1_000_000_000L);
        SandboxContainers.YANO.fundAddress(facilitatorAccount.baseAddress(), 20_000_000_000L);
        waitForUtxos(serviceAccount.baseAddress());
        waitForUtxos(facilitatorAccount.baseAddress());
    }

    protected void waitForUtxos(String address) {
        await()
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> {
                    String url = SandboxContainers.YANO.getBlockfrostBaseUrl()
                            + "addresses/" + address + "/utxos";
                    HttpResponse<String> response = HttpClient.newHttpClient().send(
                            HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    return response.statusCode() == 200 && !response.body().equals("[]");
                });
    }

    protected void waitForTransaction(String txHash) {
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> {
                    String url = SandboxContainers.YANO.getBlockfrostBaseUrl() + "txs/" + txHash + "/utxos";
                    HttpResponse<String> response = HttpClient.newHttpClient().send(
                            HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    return response.statusCode() == 200;
                });
    }

    /**
     * Waits for the transaction to land on-chain and then gives the embedded Yaci Store
     * time to process the block and fire application events.
     */
    protected void simulateYaciStoreBehavior(String transactionId) throws InterruptedException {
        waitForTransaction(transactionId);
        Thread.sleep(3000);
    }

    /**
     * Same as simulateYaciStoreBehavior(txHash) — the transaction object is no longer needed
     * because the embedded Yaci Store processes script redeemers from real block data.
     */
    protected void simulateYaciStoreBehavior(String transactionId,
                                             com.bloxbean.cardano.client.transaction.spec.Transaction transaction)
            throws InterruptedException {
        simulateYaciStoreBehavior(transactionId);
    }

    /**
     * Legacy path: directly injects UTXOs into the backend processing pipeline.
     * Used by tests that verify old-format UTXO parsing with hardcoded CBOR data.
     * These UTXOs have non-existent tx hashes so the embedded Yaci Store will never
     * emit events for them; calling processAddressUtxos directly is safe.
     */
    protected void simulateYaciStoreBehavior(List<AddressUtxo> addressUtxos) {
        cardanoBlockchainService.processAddressUtxos(addressUtxos);
        extensionManager.processAddressUtxos(addressUtxos);
    }

    protected void fundAddress(String address, long lovelaceAmount) throws IOException, InterruptedException {
        SandboxContainers.YANO.fundAddress(address, lovelaceAmount);
    }
}
