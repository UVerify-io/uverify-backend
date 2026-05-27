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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class YanoContainer extends GenericContainer<YanoContainer> {

    private static final String SNAPSHOT = "uverify-base-state";

    public YanoContainer() {
        super("uverify/sandbox-node:latest");
        // Copy the bundled snapshot into the chainstate directory before starting yano,
        // so the node begins at the pre-bootstrapped state (contracts deployed, faucet funded).
        withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh"));
        withCommand("-c",
            "cp -a /app/snapshots/" + SNAPSHOT + "/checkpoint/. /app/chainstate/ && " +
            "java -Dquarkus.profile=devnet " +
            "-Dyaci.node.tx-evaluation.enabled=true " +
            "-Dyaci.node.block-producer.script-evaluator=scalus " +
            "-jar yano.jar");
        withExposedPorts(7070, 13337);
        waitingFor(Wait.forHttp("/q/health/ready").forPort(7070)
            .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public String getBlockfrostBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/";
    }

    public int getN2NPort() {
        return getMappedPort(13337);
    }

    // Service account address hardcoded in bootstrap.sh and application-devnet.yml.
    // Guaranteed to have UTxOs in the snapshot; used as the Blockfrost readiness probe
    // after a restore (yano rebuilds its address index from RocksDB asynchronously).
    static final String SNAPSHOT_SERVICE_ADDRESS =
        "addr_test1qqgmew8y57fsfc3me40zha3gjplehxv0gwgz7sw3mdpenqgs8flgvgd7y0mwwkk5p96a8hfdptxrawepr2evqhl2aj3sr9vgye";

    public void restoreSnapshot() throws IOException, InterruptedException {
        post("/api/v1/devnet/snapshot/" + SNAPSHOT + "/restore");
        waitForHealthy();
        waitForAddressIndexReady();
    }

    /** Polls the Blockfrost address endpoint for the snapshot service address until it returns UTxOs.
     *  Yano rebuilds its address index from RocksDB after a restore, which can take 60–120 s. */
    private void waitForAddressIndexReady() throws InterruptedException {
        String url = "http://" + getHost() + ":" + getMappedPort(7070)
            + "/api/v1/addresses/" + SNAPSHOT_SERVICE_ADDRESS + "/utxos";
        for (int i = 0; i < 120; i++) {
            try {
                HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && !response.body().equals("[]")) return;
            } catch (IOException ignored) {}
            Thread.sleep(1000);
        }
        throw new RuntimeException(
            "Yano Blockfrost address index not ready within 120 s after snapshot restore");
    }

    public void fundAddress(String address, long lovelaceAmount) throws IOException, InterruptedException {
        long adaAmount = lovelaceAmount / 1_000_000;
        String body = "{\"address\":\"" + address + "\",\"ada\":" + adaAmount + "}";
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://" + getHost() + ":" + getMappedPort(7070) + "/api/v1/devnet/fund"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    void post(String path) throws IOException, InterruptedException {
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://" + getHost() + ":" + getMappedPort(7070) + path))
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
            } catch (IOException ignored) {}
            Thread.sleep(1000);
        }
        throw new RuntimeException("Yano did not become healthy within 30 s after snapshot restore");
    }
}
