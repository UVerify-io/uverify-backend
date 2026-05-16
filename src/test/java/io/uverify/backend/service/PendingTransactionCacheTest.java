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

package io.uverify.backend.service;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PendingTransactionCacheTest {

    private PendingTransactionCache cache;

    @BeforeEach
    void setUp() {
        cache = new PendingTransactionCache(Duration.ofMinutes(15));
    }

    @Test
    void isWalletUtxoLocked_returnsFalseForUnknownUtxo() {
        assertFalse(cache.isWalletUtxoLocked("abc123", 0));
    }

    @Test
    void isWalletUtxoLocked_returnsTrueAfterLock() {
        cache.lockWalletUtxo("abc123", 0);
        assertTrue(cache.isWalletUtxoLocked("abc123", 0));
    }

    @Test
    void isWalletUtxoLocked_returnsFalseAfterTtlExpiry() throws InterruptedException {
        PendingTransactionCache shortLived = new PendingTransactionCache(Duration.ofMillis(50));
        shortLived.lockWalletUtxo("abc123", 0);
        assertTrue(shortLived.isWalletUtxoLocked("abc123", 0));
        Thread.sleep(100);
        assertFalse(shortLived.isWalletUtxoLocked("abc123", 0));
    }

    @Test
    void lockWalletUtxo_differentOutputIndicesAreIndependent() {
        cache.lockWalletUtxo("abc123", 0);
        cache.lockWalletUtxo("abc123", 1);
        assertTrue(cache.isWalletUtxoLocked("abc123", 0));
        assertTrue(cache.isWalletUtxoLocked("abc123", 1));
        assertFalse(cache.isWalletUtxoLocked("abc123", 2));
    }

    @Test
    void lockWalletUtxo_differentTxHashesAreIndependent() {
        cache.lockWalletUtxo("hash1", 0);
        assertTrue(cache.isWalletUtxoLocked("hash1", 0));
        assertFalse(cache.isWalletUtxoLocked("hash2", 0));
    }

    @Test
    void getPendingStateUtxo_returnsEmptyForUnknownUnit() {
        assertTrue(cache.getPendingStateUtxo("unknown_unit").isEmpty());
    }

    @Test
    void getPendingStateUtxo_returnsValueAfterPut() {
        Utxo utxo = Utxo.builder()
                .txHash("txhash1")
                .outputIndex(0)
                .address("addr_test1...")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000))))
                .build();
        cache.putPendingStateUtxo("unit1", utxo);

        Optional<Utxo> result = cache.getPendingStateUtxo("unit1");
        assertTrue(result.isPresent());
        assertEquals("txhash1", result.get().getTxHash());
        assertEquals(0, result.get().getOutputIndex());
    }

    @Test
    void getPendingStateUtxo_returnsEmptyAfterTtlExpiry() throws InterruptedException {
        PendingTransactionCache shortLived = new PendingTransactionCache(Duration.ofMillis(50));
        Utxo utxo = Utxo.builder()
                .txHash("txhash1")
                .outputIndex(0)
                .address("addr_test1...")
                .amount(List.of())
                .build();
        shortLived.putPendingStateUtxo("unit1", utxo);
        assertTrue(shortLived.getPendingStateUtxo("unit1").isPresent());

        Thread.sleep(100);
        assertTrue(shortLived.getPendingStateUtxo("unit1").isEmpty());
    }

    @Test
    void putPendingStateUtxo_overwritesPreviousEntryForSameUnit() {
        Utxo first = Utxo.builder().txHash("first").outputIndex(0).address("a").amount(List.of()).build();
        Utxo second = Utxo.builder().txHash("second").outputIndex(1).address("a").amount(List.of()).build();

        cache.putPendingStateUtxo("unit1", first);
        cache.putPendingStateUtxo("unit1", second);

        Optional<Utxo> result = cache.getPendingStateUtxo("unit1");
        assertTrue(result.isPresent());
        assertEquals("second", result.get().getTxHash());
    }

    @Test
    void clearWalletLocks_removesAllLocks() {
        cache.lockWalletUtxo("hash1", 0);
        cache.lockWalletUtxo("hash2", 1);
        assertTrue(cache.isWalletUtxoLocked("hash1", 0));
        assertTrue(cache.isWalletUtxoLocked("hash2", 1));

        cache.clearWalletLocks();

        assertFalse(cache.isWalletUtxoLocked("hash1", 0));
        assertFalse(cache.isWalletUtxoLocked("hash2", 1));
    }

    @Test
    void isCollateralUtxoLocked_returnsFalseForUnknownUtxo() {
        assertFalse(cache.isCollateralUtxoLocked("abc123", 0));
    }

    @Test
    void isCollateralUtxoLocked_returnsTrueAfterLock() {
        cache.lockCollateralUtxo("abc123", 0);
        assertTrue(cache.isCollateralUtxoLocked("abc123", 0));
    }

    @Test
    void isCollateralUtxoLocked_returnsFalseAfterTtlExpiry() throws InterruptedException {
        PendingTransactionCache shortLived = new PendingTransactionCache(Duration.ofMillis(50));
        shortLived.lockCollateralUtxo("abc123", 0);
        assertTrue(shortLived.isCollateralUtxoLocked("abc123", 0));
        Thread.sleep(100);
        assertFalse(shortLived.isCollateralUtxoLocked("abc123", 0));
    }

    @Test
    void lockCollateralUtxo_independentFromWalletLock() {
        cache.lockWalletUtxo("hash1", 0);
        assertFalse(cache.isCollateralUtxoLocked("hash1", 0));

        cache.lockCollateralUtxo("hash2", 0);
        assertFalse(cache.isWalletUtxoLocked("hash2", 0));
    }

    @Test
    void clearLocksForTransaction_removesWalletCollateralAndPendingState() {
        String txHash = "txhash1";
        cache.lockWalletUtxo(txHash, 0);
        cache.lockCollateralUtxo(txHash, 1);
        Utxo utxo = Utxo.builder().txHash(txHash).outputIndex(0).address("a").amount(List.of()).build();
        cache.putPendingStateUtxo("unit1", utxo);

        assertTrue(cache.isWalletUtxoLocked(txHash, 0));
        assertTrue(cache.isCollateralUtxoLocked(txHash, 1));
        assertTrue(cache.getPendingStateUtxo("unit1").isPresent());

        cache.clearLocksForTransaction(txHash);

        assertFalse(cache.isWalletUtxoLocked(txHash, 0));
        assertFalse(cache.isCollateralUtxoLocked(txHash, 1));
        assertTrue(cache.getPendingStateUtxo("unit1").isEmpty());
    }

    @Test
    void clearLocksForTransaction_doesNotAffectOtherTransactions() {
        cache.lockWalletUtxo("txA", 0);
        cache.lockCollateralUtxo("txB", 0);

        cache.clearLocksForTransaction("txA");

        assertFalse(cache.isWalletUtxoLocked("txA", 0));
        assertTrue(cache.isCollateralUtxoLocked("txB", 0));
    }

    @Test
    void clearCollateralLocks_removesAllCollateralLocks() {
        cache.lockCollateralUtxo("hash1", 0);
        cache.lockCollateralUtxo("hash2", 1);
        assertTrue(cache.isCollateralUtxoLocked("hash1", 0));
        assertTrue(cache.isCollateralUtxoLocked("hash2", 1));

        cache.clearCollateralLocks();

        assertFalse(cache.isCollateralUtxoLocked("hash1", 0));
        assertFalse(cache.isCollateralUtxoLocked("hash2", 1));
    }

    @Test
    void differentUnitsAreTrackedIndependently() {
        Utxo utxoA = Utxo.builder().txHash("hashA").outputIndex(0).address("a").amount(List.of()).build();
        Utxo utxoB = Utxo.builder().txHash("hashB").outputIndex(0).address("a").amount(List.of()).build();

        cache.putPendingStateUtxo("unitA", utxoA);
        cache.putPendingStateUtxo("unitB", utxoB);

        assertEquals("hashA", cache.getPendingStateUtxo("unitA").orElseThrow().getTxHash());
        assertEquals("hashB", cache.getPendingStateUtxo("unitB").orElseThrow().getTxHash());
        assertTrue(cache.getPendingStateUtxo("unitC").isEmpty());
    }
}
