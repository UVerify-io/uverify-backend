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

package io.uverify.backend.sandbox;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import io.uverify.backend.repository.TransactionRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

public class YanoContainer extends GenericContainer<YanoContainer> {

    // Service account address hardcoded in bootstrap.sh and application-devnet.yml.
    // Guaranteed to have UTxOs in the snapshot; used as the Blockfrost readiness probe
    // after a restore (yano rebuilds its address index from RocksDB asynchronously).
    public static final String SNAPSHOT_SERVICE_ADDRESS =
            "addr_test1qqgmew8y57fsfc3me40zha3gjplehxv0gwgz7sw3mdpenqgs8flgvgd7y0mwwkk5p96a8hfdptxrawepr2evqhl2aj3sr9vgye";
    private static final String SNAPSHOT = "uverify-base-state";
    private BFBackendService backendService;

    private TransactionRepository transactionRepository;

    public YanoContainer() {
        super("uverify/sandbox-node:latest");
        withCommand("sh", "-c",
                "cp -a /app/snapshots/" + SNAPSHOT + "/checkpoint/. /app/chainstate/ && " +
                        "java -Dquarkus.profile=devnet " +
                        "-Dyano.block-producer.tx-evaluation=true " +
                        "-Dyano.block-producer.script-evaluator=aiken " +
                        "-jar yano.jar");
        withExposedPorts(7070, 13337);
        waitingFor(Wait.forHttp("/q/health/ready").forPort(7070)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public String getBlockfrostBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/";
    }

    public int getN2NPort() {
        return getMappedPort(13337);
    }

    public BackendService getBackendService() {
        return this.backendService;
    }

    public void restoreSnapshot() throws IOException, InterruptedException {
        applySnapshot();
        waitForHealthy();
        waitForAddressIndexReady();
        waitForProtocolParamsReady();
        waitForNewBlock();
    }

    /**
     * Polls the Blockfrost address endpoint for the snapshot service address until it returns UTxOs.
     * Yano rebuilds its address index from RocksDB after a restore, which can take 60–120 s.
     */
    private void waitForAddressIndexReady() throws InterruptedException {
        String url = "http://" + getHost() + ":" + getMappedPort(7070)
                + "/api/v1/addresses/" + SNAPSHOT_SERVICE_ADDRESS + "/utxos";
        for (int i = 0; i < 120; i++) {
            try {
                HttpResponse<String> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && !response.body().equals("[]")) {
                    this.backendService = new BFBackendService(getBlockfrostBaseUrl(), "");
                    return;
                }
            } catch (IOException ignored) {
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException(
                "Yano Blockfrost address index not ready within 120 s after snapshot restore");
    }

    public String fundAddress(String address, long lovelaceAmount) throws IOException, InterruptedException {
        long adaAmount = lovelaceAmount / 1_000_000;
        String body = "{\"address\":\"" + address + "\",\"ada\":" + adaAmount + "}";
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/devnet/fund"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fund address: " + response.body());
        }
        String responseBody = response.body();
        int idx = responseBody.indexOf("\"tx_hash\":\"");
        if (idx < 0) throw new RuntimeException("No tx_hash in fund response: " + responseBody);
        int start = idx + 11;
        int end = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }

    private void waitForProtocolParamsReady() throws InterruptedException {
        String latestEpochUrl = "http://" + getHost() + ":" + getMappedPort(7070)
                + "/api/v1/epochs/latest/parameters";
        for (int i = 0; i < 120; i++) {
            try {
                HttpResponse<String> epochParameterResponse = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder().uri(URI.create(latestEpochUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (epochParameterResponse.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException(
                "Protocol params not available within 120 s after snapshot restore");
    }

    private int parseEpochNumber(String body) {
        int idx = body.indexOf("\"epoch\":");
        if (idx < 0) return -1;
        int start = idx + 8;
        while (start < body.length() && body.charAt(start) == ' ') start++;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        if (start == end) return -1;
        return Integer.parseInt(body.substring(start, end));
    }

    private void waitForNewBlock() throws IOException, InterruptedException {
        String url = "http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/blocks/latest";
        int initialHeight = getLatestBlockHeight(url);
        for (int i = 0; i < 60; i++) {
            try {
                int currentHeight = getLatestBlockHeight(url);
                if (currentHeight > initialHeight) return;
            } catch (IOException ignored) {
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException("No new block produced within 60 s after snapshot restore");
    }

    private int getLatestBlockHeight(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        // Parse "height": <number> from the JSON response
        int idx = body.indexOf("\"height\":");
        if (idx < 0) return -1;
        int start = idx + 9;
        while (start < body.length() && (body.charAt(start) == ' ' || body.charAt(start) == ':')) start++;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        if (start == end) return -1;
        return Integer.parseInt(body.substring(start, end));
    }

    void applySnapshot() throws IOException, InterruptedException {
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/devnet/snapshot/uverify-base-state/restore"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void waitForHealthy() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            try {
                int status = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://" + getHost() + ":" + getMappedPort(7070) + "/q/health/ready"))
                                .GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status == 200) return;
            } catch (IOException ignored) {
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException("Yano did not become healthy within 30 s after snapshot restore");
    }

    // This is not blockchain inclusion, but gives evidence yaci-store already picked it up
    public void waitForTransactionInclusion(String transactionHash) {
        if (transactionRepository == null) {
            throw new IllegalStateException("Transaction repository not set");
        }

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> transactionRepository.findById(transactionHash).isPresent());
    }

    public void waitForTransaction(String transactionHash) {
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() ->
                        HttpClient.newHttpClient().send(
                                HttpRequest.newBuilder()
                                        .uri(URI.create("http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/txs/" + transactionHash + "/utxos"))
                                        .GET().build(),
                                HttpResponse.BodyHandlers.discarding()).statusCode() == 200);


    }
}
