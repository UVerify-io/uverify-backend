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
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Cursor;
import com.bloxbean.cardano.yaci.store.common.service.CursorService;
import com.bloxbean.cardano.yaci.store.core.service.StartService;
import io.uverify.backend.devnet.YanoDevnet;
import io.uverify.backend.devnet.YanoDevnetTestConfiguration;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.ProxyInitResponse;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.extension.service.FractionizedCertificateService;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.LibraryRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.service.*;
import io.uverify.backend.util.ValidatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.uverify.backend.devnet.YanoDevnet.TEST_BOOTSTRAP_TOKEN;

/**
 * Base class for tests that need a chain. The chain is the in-process Yano devnet
 * from {@link YanoDevnet}. Each test class starts from the same restored snapshot
 * with the proxy contract initialised, the library deployed and one bootstrap datum
 * named {@link YanoDevnet#TEST_BOOTSTRAP_TOKEN} minted.
 */
@Slf4j
@SpringBootTest
@Import(YanoDevnetTestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CardanoBlockchainTest {
    private static final Duration INDEXING_TIMEOUT = Duration.ofSeconds(90);

    protected final YanoDevnet devnet = YanoDevnet.get();
    protected final BackendService backendService = devnet.backendService();

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

    private final List<String> additionalFundingAddresses;

    @Autowired
    private StartService startService;
    @Autowired
    private Flyway flyway;
    @Autowired
    private CursorService cursorService;
    @Autowired
    private PendingTransactionCache pendingTransactionCache;
    @Autowired
    private YanoDevnetTestConfiguration.ProcessedTransactions processedTransactions;

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
                                 LibraryService libraryService,
                                 List<String> additionalFundingAddresses) {
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
        this.additionalFundingAddresses = additionalFundingAddresses;

        serviceAccount = Account.createFromMnemonic(Networks.testnet(), testServiceUserMnemonic);
        userAccount = Account.createFromMnemonic(Networks.testnet(), testUserMnemonic);
        feeReceiverAccount = Account.createFromMnemonic(Networks.testnet(), feeReceiverMnemonic);
        facilitatorAccount = Account.createFromMnemonic(Networks.testnet(), facilitatorMnemonic);

        cardanoBlockchainService.setBackendService(backendService);
        libraryService.setBackendService(backendService);
        fractionizedCertificateService.ifPresent(service -> service.setBackendService(backendService));
    }

    @DynamicPropertySource
    static void yanoDevnetProperties(DynamicPropertyRegistry registry) {
        YanoDevnet devnet = YanoDevnet.get();
        String genesisDirectory = devnet.genesisDirectory().toAbsolutePath() + "/";

        registry.add("store.cardano.host", () -> "localhost");
        registry.add("store.cardano.port", devnet::nodeToNodePort);
        registry.add("store.cardano.protocol-magic", () -> "42");
        registry.add("store.cardano.sync-start-slot", () -> "0");
        registry.add("store.cardano.sync-start-blockhash", () -> "");
        registry.add("store.cardano.byron-genesis-file", () -> genesisDirectory + "byron-genesis.json");
        registry.add("store.cardano.shelley-genesis-file", () -> genesisDirectory + "shelley-genesis.json");
        registry.add("store.cardano.alonzo-genesis-file", () -> genesisDirectory + "alonzo-genesis.json");
        registry.add("store.cardano.conway-genesis-file", () -> genesisDirectory + "conway-genesis.json");
        registry.add("store.sync-auto-start", () -> "false");
        registry.add("store.cardano.sync-auto-start", () -> "false");

        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:uverify-test;DB_CLOSE_DELAY=-1");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    @BeforeAll
    void prepareChainState() throws Exception {
        if (startService.isStarted()) {
            startService.stop();
        }
        flyway.clean();
        flyway.migrate();
        resetInMemoryState();
        processedTransactions.clear();

        if (devnet.hasBaseState()) {
            devnet.restoreBaseState();
            fundAdditionalAddresses();
            validatorHelper.setProxy(devnet.proxyTransactionHash(), devnet.proxyOutputIndex());
            startService.start();
        } else {
            fundAdditionalAddresses();
            createBaseState();
        }

        awaitIndexed(() -> bootstrapDatumRepository.findAll().stream()
                        .anyMatch(datum -> TEST_BOOTSTRAP_TOKEN.equals(datum.getTokenName())),
                "bootstrap datum " + TEST_BOOTSTRAP_TOKEN);
    }

    private void resetInMemoryState() {
        libraryService.rollbackToSlot(-1);
        validatorHelper.setProxy("", 0);
        pendingTransactionCache.clear();
    }

    private void fundAdditionalAddresses() {
        for (String address : additionalFundingAddresses) {
            devnet.fundAda(address, 200);
            devnet.fundAda(address, 20);
            devnet.fundAda(address, 20);
            devnet.fundAda(address, 20);
        }
    }

    /**
     * Runs the UVerify init transactions once per JVM. The indexer has to be live
     * while they run because contract deployment and the bootstrap datum mint read
     * the library entries the indexer derives from earlier transactions.
     */
    private void createBaseState() throws Exception {
        devnet.fundAda(serviceAccount.baseAddress(), 20_000);
        devnet.fundAda(serviceAccount.baseAddress(), 50_000);
        devnet.fundAda(serviceAccount.baseAddress(), 100_000);
        devnet.fundAda(serviceAccount.baseAddress(), 60_000);
        devnet.fundAda(userAccount.baseAddress(), 2_000);
        devnet.fundAda(userAccount.baseAddress(), 5);
        devnet.fundAda(userAccount.baseAddress(), 1_000);
        devnet.fundAda(facilitatorAccount.baseAddress(), 10_000);
        devnet.fundAda(facilitatorAccount.baseAddress(), 10);

        startService.start();

        ProxyInitResponse proxyInit = cardanoBlockchainService.initProxyContract();
        assertBuildSucceeded(proxyInit.getStatus().getCode(), "proxy init");
        submitAndAwait(proxyInit.getUnsignedProxyTransaction(), serviceAccount);
        validatorHelper.setProxy(proxyInit.getProxyTxHash(), proxyInit.getProxyOutputIndex());

        BuildTransactionResponse deployment = libraryService.buildDeployTransaction();
        assertBuildSucceeded(deployment.getStatus().getCode(), "library deployment");
        submitAndAwait(deployment.getUnsignedTransaction(), serviceAccount);

        BootstrapDatum bootstrapDatum = BootstrapDatum.generateFrom(List.of(feeReceiverAccount.baseAddress()));
        bootstrapDatum.setTokenName(TEST_BOOTSTRAP_TOKEN);
        bootstrapDatum.setFeeInterval(3);
        bootstrapDatum.setTransactionLimit(15);
        Transaction mint = cardanoBlockchainService.mintProxyBootstrapDatum(bootstrapDatum);
        Result<String> mintResult = cardanoBlockchainService.submitTransaction(mint, serviceAccount);
        if (!mintResult.isSuccessful()) {
            throw new IllegalStateException("Bootstrap datum mint failed: " + mintResult.getResponse());
        }
        waitForTransaction(mintResult.getValue());

        devnet.createBaseState(proxyInit.getProxyTxHash(), proxyInit.getProxyOutputIndex());
    }

    private void submitAndAwait(String unsignedTransactionHex, Account signer)
            throws CborDeserializationException, CborSerializationException, ApiException {
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(unsignedTransactionHex));
        Result<String> result = cardanoBlockchainService.submitTransaction(transaction, signer);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Transaction submission failed: " + result.getResponse());
        }
        waitForTransaction(result.getValue());
    }

    private static void assertBuildSucceeded(BuildStatusCode code, String step) {
        if (code != BuildStatusCode.SUCCESS) {
            throw new IllegalStateException("Building the " + step + " transaction failed with " + code);
        }
    }

    /**
     * Waits until the transaction is in a Yano block and the embedded yaci-store has
     * indexed that block. The UVerify pipeline runs inside the indexer's write path, so
     * once the cursor has passed the block the backend tables are updated as well. The
     * transaction table itself is no signal: the statistics service prunes rows that
     * do not touch a stored UTxO on every commit.
     */
    protected void waitForTransaction(String transactionHash) {
        String hashOnChain = devnet.awaitTransaction(transactionHash);
        long blockHeight = awaitBlockHeight(hashOnChain);
        awaitIndexed(() -> cursorService.getCursor().map(Cursor::getBlock).orElse(-1L) >= blockHeight
                        && processedTransactions.isProcessed(hashOnChain),
                "block " + blockHeight + " with transaction " + hashOnChain);
    }

    private long awaitBlockHeight(String transactionHash) {
        return Awaitility.await("inclusion of transaction " + transactionHash)
                .atMost(INDEXING_TIMEOUT)
                .pollInterval(Duration.ofMillis(250))
                .until(() -> {
                    Result<TransactionContent> transaction = backendService.getTransactionService()
                            .getTransaction(transactionHash);
                    if (!transaction.isSuccessful() || transaction.getValue().getBlockHeight() == null) {
                        return 0L;
                    }
                    return transaction.getValue().getBlockHeight().longValue();
                }, height -> height > 0);
    }

    protected void awaitIndexed(java.util.concurrent.Callable<Boolean> condition, String description) {
        Awaitility.await("indexing of " + description)
                .atMost(INDEXING_TIMEOUT)
                .pollInterval(Duration.ofMillis(250))
                .until(condition);
    }

    /**
     * Feeds hand-built UTxOs straight into the backend pipeline. Only for legacy
     * on-chain layouts that cannot be produced on a fresh devnet.
     */
    protected void injectAddressUtxos(List<AddressUtxo> addressUtxos) {
        cardanoBlockchainService.processAddressUtxos(addressUtxos);
        extensionManager.processAddressUtxos(addressUtxos);
    }
}
