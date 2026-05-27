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

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.List;

public final class SandboxContainers {

    public static final YanoContainer YANO = new YanoContainer();

    @SuppressWarnings("resource")
    public static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("uverify")
            .withUsername("uverify")
            .withPassword("test_password");

    static {
        Startables.deepStart(List.of(YANO, POSTGRES)).join();
    }

    private static volatile boolean snapshotCaptured = false;

    /**
     * Resets the sandbox to the bundled snapshot state and clears all backend application tables.
     * Call this from a JUnit @BeforeAll via SandboxResetExtension.
     *
     * On the first call the Yaci Store is still syncing from block 0; we wait for it to
     * populate library and bootstrap_datum, then take a DB snapshot.
     * On subsequent calls we restore both the yano chain (snapshot restore) and the DB
     * (from the captured snapshot tables) so we never rely on the Yaci Store rollback to
     * restore the invariant data.
     */
    public static void reset(org.springframework.jdbc.core.JdbcTemplate jdbc) throws Exception {
        if (!snapshotCaptured) {
            // First call: wait for initial Yaci Store sync, then capture snapshot tables.
            waitForInitialSync(jdbc);
            captureDbSnapshot(jdbc);
            snapshotCaptured = true;
        }

        // Reset the yano chain to the snapshot state.
        YANO.restoreSnapshot();

        // Truncate ALL application tables.
        jdbc.execute(
            "TRUNCATE TABLE library, bootstrap_datum, uverify_credential, tadamon_transaction, " +
            "social_hub, connected_good_update, connected_good, uverify_certificate, " +
            "state_datum_update, state_datum, fee_receiver, user_credential CASCADE"
        );

        // Restore the invariant data that was captured after the initial Yaci Store sync.
        restoreDbSnapshot(jdbc);

        // Give Yaci Store a moment to process the chain reset without interfering with our tables.
        Thread.sleep(3000);
    }

    /**
     * Waits for the embedded Yaci Store to populate the snapshot-bundled tables.
     * The snapshot contains exactly one bootstrap datum ("uverify") and at least two
     * library entries (proxy + state contract).
     */
    private static void waitForInitialSync(org.springframework.jdbc.core.JdbcTemplate jdbc)
            throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            try {
                Integer uverifyCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bootstrap_datum WHERE name = 'uverify'", Integer.class);
                Integer libraryCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM library", Integer.class);
                if (uverifyCount != null && uverifyCount >= 1
                        && libraryCount != null && libraryCount >= 2) {
                    return;
                }
            } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        throw new RuntimeException("Yaci Store initial sync did not populate snapshot data within 120 s");
    }

    private static void captureDbSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.execute("DROP TABLE IF EXISTS _bk_library");
        jdbc.execute("CREATE TABLE _bk_library AS SELECT * FROM library");
        jdbc.execute("DROP TABLE IF EXISTS _bk_bootstrap_datum");
        jdbc.execute("CREATE TABLE _bk_bootstrap_datum AS SELECT * FROM bootstrap_datum");
    }

    private static void restoreDbSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.execute("INSERT INTO library SELECT * FROM _bk_library");
        jdbc.execute("INSERT INTO bootstrap_datum SELECT * FROM _bk_bootstrap_datum");
    }

    private SandboxContainers() {}
}
