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

package io.uverify.backend.devnet;

import com.bloxbean.cardano.client.backend.api.BackendService;
import lombok.extern.slf4j.Slf4j;
import org.yanoproject.api.ProducerControl;
import org.yanoproject.api.config.UpstreamConfig;
import org.yanoproject.api.config.UpstreamTxConfig;
import org.yanoproject.api.config.YanoPropertyKeys;
import org.yanoproject.devnet.YanoDevnetAssembly;
import org.yanoproject.runtime.assembly.Yano;
import org.yanoproject.runtime.tx.TransactionBootstrapOptions;
import org.yanoproject.testkit.ccl.YanoBackendService;
import org.yanoproject.testkit.devnet.UVerifyTestKits;
import org.yanoproject.testkit.devnet.YanoDevnetTestConfig;
import org.yanoproject.testkit.devnet.YanoDevnetTestKit;
import org.yanoproject.tx.DefaultTransactionServicesFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One in-process Yano devnet per test JVM. The first chain-backed test class runs
 * the UVerify init transactions once and snapshots the result. Every later class
 * restores that snapshot instead of replaying the transactions.
 */
@Slf4j
public final class YanoDevnet {

    public static final String BASE_STATE_SNAPSHOT = "uverify-base-state";
    public static final String TEST_BOOTSTRAP_TOKEN = "uverify_proxy_test_token";
    private static final String SCRIPT_EVALUATOR = "scalus";
    private static final Duration TRANSACTION_TIMEOUT = Duration.ofSeconds(60);

    private static YanoDevnet instance;

    private final YanoDevnetTestConfig config;
    private final Yano node;
    private final YanoDevnetTestKit kit;
    private final BackendService backendService;
    private final Map<String, String> submittedTransactions = new ConcurrentHashMap<>();

    private String proxyTransactionHash;
    private int proxyOutputIndex;
    private boolean baseStateReady;

    public static synchronized YanoDevnet get() {
        if (instance == null) {
            instance = new YanoDevnet();
        }
        return instance;
    }

    private YanoDevnet() {
        // With epoch parameter tracking on, the runtime only serves protocol parameters per
        // epoch once the tracker has produced a snapshot. The devnet never changes its
        // parameters, so serve the static protocol-param.json from the start instead.
        config = YanoDevnetTestConfig.builder()
                .temporaryRocksDbStorage()
                .runtimeOption(YanoPropertyKeys.Ledger.EPOCH_PARAMS_TRACKING_ENABLED, false)
                .runtimeOption(YanoPropertyKeys.BlockProducer.TX_EVALUATION, true)
                .runtimeOption(YanoPropertyKeys.BlockProducer.SCRIPT_EVALUATOR, SCRIPT_EVALUATOR)
                .build();
        config.yanoConfig().setTxEvaluationEnabled(true);
        // A devnet has no upstream peer. With the default forwarding policy the runtime still
        // builds a peer endpoint from the empty remote host after admitting a transaction and
        // throws on the invalid port, which fails the submit call.
        config.yanoConfig().setUpstream(UpstreamConfig.builder()
                .tx(UpstreamTxConfig.builder().forwarding("disabled").build())
                .build());

        node = YanoDevnetAssembly.devnet(config.yanoConfig())
                .runtimeOptions(config.runtimeOptions())
                .transactionBootstrap(
                        TransactionBootstrapOptions.enabled(false, false, SCRIPT_EVALUATOR),
                        DefaultTransactionServicesFactory::create)
                .build();

        kit = UVerifyTestKits.wrap(node);
        kit.start();
        kit.await().untilReady();
        awaitProtocolParameters();
        backendService = new YanoDevnetBackendService(YanoBackendService.from(kit), kit,
                submitted -> submittedTransactions.put(submitted.submittedHash(), submitted.hashOnChain()));

        if (!kit.transactions().evaluationAvailable()) {
            log.error("Yano devnet started WITHOUT transaction evaluation. Plutus scripts will not be validated.");
        }
        log.info("Yano devnet ready: n2n port {}, genesis {}", nodeToNodePort(), genesisDirectory());

        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "yano-devnet-shutdown"));
    }

    public YanoDevnetTestKit kit() {
        return kit;
    }

    public BackendService backendService() {
        return backendService;
    }

    public int nodeToNodePort() {
        return config.yanoConfig().getServerPort();
    }

    public Path genesisDirectory() {
        return config.devnetProfileDir()
                .orElseThrow(() -> new IllegalStateException("Yano devnet has no genesis directory"));
    }

    public void fundAda(String address, long ada) {
        kit.faucet().fund(address, ada * 1_000_000L);
    }

    /**
     * Waits until the transaction is in a block and returns the hash it carries on
     * chain, which differs from the submitted hash when Yano rebuilt the body (see
     * {@link YanoDevnetBackendService}).
     */
    public String awaitTransaction(String transactionHash) {
        String hashOnChain = submittedTransactions.getOrDefault(transactionHash, transactionHash);
        kit.await().withTimeout(TRANSACTION_TIMEOUT).untilTxVisible(hashOnChain);
        return hashOnChain;
    }

    public boolean hasBaseState() {
        return baseStateReady;
    }

    public void createBaseState(String proxyTransactionHash, int proxyOutputIndex) {
        this.proxyTransactionHash = proxyTransactionHash;
        this.proxyOutputIndex = proxyOutputIndex;
        var snapshot = kit.snapshots().create(BASE_STATE_SNAPSHOT);
        baseStateReady = true;
        log.info("Created base state snapshot at slot {} block {}", snapshot.slot(), snapshot.blockNumber());
    }

    public void restoreBaseState() {
        if (!baseStateReady) {
            throw new IllegalStateException("Base state snapshot has not been created yet");
        }
        kit.producerControl().ifPresent(ProducerControl::stopProducer);
        var tip = kit.snapshots().restoreAndGetTip(BASE_STATE_SNAPSHOT);
        evictPendingTransactions();
        kit.producerControl().ifPresent(ProducerControl::startProducer);
        awaitProtocolParameters();
        log.info("Restored base state snapshot, tip is slot {} block {}", tip.slot(), tip.blockNumber());
    }

    /**
     * A restore behaves like a rollback: transactions from the discarded blocks return
     * to the mempool and would be mined again right after the restore, spending UTxOs
     * the next test class relies on. Block production is paused around the restore so
     * they can be evicted before the next block.
     */
    private void evictPendingTransactions() {
        for (String transactionHash : submittedTransactions.keySet()) {
            if (kit.txGateway().isTransactionInMemPool(transactionHash)) {
                log.info("Evicting transaction {} that returned to the mempool after restore", transactionHash);
                node.mempoolAdminGateway().evictTransaction(transactionHash);
            }
        }
        submittedTransactions.clear();
    }

    /**
     * The ledger publishes protocol parameters asynchronously after start and after a
     * restore. Transaction building fails with a 404 until they are there.
     */
    private void awaitProtocolParameters() {
        kit.await().withTimeout(TRANSACTION_TIMEOUT).until(() -> {
            String parameters = kit.queries().protocolParameters();
            return parameters != null && !parameters.isBlank();
        }, "protocol parameters to be available");
    }

    public String proxyTransactionHash() {
        return proxyTransactionHash;
    }

    public int proxyOutputIndex() {
        return proxyOutputIndex;
    }

    private void close() {
        try {
            kit.close();
        } catch (RuntimeException exception) {
            log.warn("Failed to close Yano devnet cleanly: {}", exception.getMessage());
        } finally {
            config.close();
        }
    }
}
