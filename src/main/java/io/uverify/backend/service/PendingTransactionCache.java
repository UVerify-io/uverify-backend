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
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of UTxOs that have been consumed or created in pending (unconfirmed) transactions.
 *
 * Rapid consecutive build requests from the same user hit the same on-chain UTxO set before any
 * transaction confirms. This cache lets subsequent builds "chain" off the output of an in-flight
 * transaction instead of querying stale chain state.
 *
 * Two concerns are tracked:
 *  - State token UTxOs: the specific UTxO holding the user's state token, which the contract
 *    requires to be spent-and-recreated on every certificate update.
 *  - Wallet UTxOs: regular ADA inputs consumed during a fork (new state datum creation), which
 *    must not be reused across concurrent forks.
 *
 * Entries expire after TTL_MINUTES. The TTL matches the transaction validity window
 * (validTo = currentSlot + 600, ~10 minutes), with a small buffer.
 */
@Component
@Slf4j
public class PendingTransactionCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private final Duration ttl;

    public PendingTransactionCache() {
        this.ttl = DEFAULT_TTL;
    }

    // Package-private: allows unit tests to inject a short TTL without modifying production behaviour.
    PendingTransactionCache(Duration ttl) {
        this.ttl = ttl;
    }

    private record PendingEntry<T>(T value, Instant expiry) {
        boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }

    private final ConcurrentHashMap<String, PendingEntry<Utxo>> pendingStateUtxos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockedWalletUtxos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockedCollateralUtxos = new ConcurrentHashMap<>();

    /**
     * Returns the chained state token UTxO for the given unit if a pending transaction
     * will produce it. Returns empty if no pending entry exists or it has expired.
     */
    public Optional<Utxo> getPendingStateUtxo(String unit) {
        PendingEntry<Utxo> entry = pendingStateUtxos.get(unit);
        if (entry == null) return Optional.empty();
        if (entry.isExpired()) {
            pendingStateUtxos.remove(unit);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void putPendingStateUtxo(String unit, Utxo utxo) {
        pendingStateUtxos.put(unit, new PendingEntry<>(utxo, Instant.now().plus(ttl)));
        log.debug("Cached pending state UTxO for unit {} → {}:{}", unit, utxo.getTxHash(), utxo.getOutputIndex());
    }

    public void lockWalletUtxo(String txHash, int index) {
        lockedWalletUtxos.put(walletKey(txHash, index), Instant.now().plus(ttl));
    }

    public boolean isWalletUtxoLocked(String txHash, int index) {
        Instant expiry = lockedWalletUtxos.get(walletKey(txHash, index));
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            lockedWalletUtxos.remove(walletKey(txHash, index));
            return false;
        }
        return true;
    }

    public void lockCollateralUtxo(String txHash, int index) {
        lockedCollateralUtxos.put(walletKey(txHash, index), Instant.now().plus(ttl));
    }

    public boolean isCollateralUtxoLocked(String txHash, int index) {
        Instant expiry = lockedCollateralUtxos.get(walletKey(txHash, index));
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            lockedCollateralUtxos.remove(walletKey(txHash, index));
            return false;
        }
        return true;
    }

    public void clearLocksForTransaction(String txHash) {
        String prefix = txHash + ":";
        lockedWalletUtxos.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        lockedCollateralUtxos.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        pendingStateUtxos.entrySet().removeIf(e -> e.getValue().value().getTxHash().equalsIgnoreCase(txHash));
    }

    /**
     * Inspects a freshly-built unsigned transaction and populates the cache.
     *
     * All regular inputs are locked so they won't be reused in a concurrent fork.
     * Any output going to {@code proxyScriptAddress} that carries a multi-asset under
     * {@code proxyScriptHash} is stored as the chained state token UTxO.
     *
     * @param transaction      The unsigned transaction returned by QuickTxBuilder.build()
     * @param proxyScriptAddress Bech32 address of the proxy contract (used to locate the output)
     * @param proxyScriptHash  Policy-id hex of the proxy contract (identifies the state token)
     */
    public void populate(Transaction transaction, String proxyScriptAddress, String proxyScriptHash) {
        String txHash;
        try {
            txHash = TransactionUtil.getTxHash(transaction);
        } catch (Exception e) {
            log.warn("Could not compute tx hash for pending cache population: {}", e.getMessage());
            return;
        }

        if (transaction.getBody().getInputs() != null) {
            for (TransactionInput input : transaction.getBody().getInputs()) {
                lockWalletUtxo(input.getTransactionId(), input.getIndex());
            }
        }

        if (transaction.getBody().getCollateral() != null) {
            for (TransactionInput collateral : transaction.getBody().getCollateral()) {
                lockCollateralUtxo(collateral.getTransactionId(), collateral.getIndex());
            }
        }

        if (transaction.getBody().getOutputs() == null) return;

        List<com.bloxbean.cardano.client.transaction.spec.TransactionOutput> outputs =
                transaction.getBody().getOutputs();

        for (int i = 0; i < outputs.size(); i++) {
            com.bloxbean.cardano.client.transaction.spec.TransactionOutput output = outputs.get(i);
            if (output.getValue() == null || output.getValue().getMultiAssets() == null) continue;

            Optional<MultiAsset> maybeStateAsset = output.getValue().getMultiAssets().stream()
                    .filter(ma -> ma.getPolicyId().equalsIgnoreCase(proxyScriptHash))
                    .findFirst();
            if (maybeStateAsset.isEmpty()) continue;

            List<Amount> amounts = buildAmounts(output);

            String inlineDatumHex = null;
            if (output.getInlineDatum() != null) {
                try {
                    inlineDatumHex = output.getInlineDatum().serializeToHex();
                } catch (Exception e) {
                    log.warn("Could not serialize inline datum for pending cache: {}", e.getMessage());
                }
            }

            Utxo chainedUtxo = Utxo.builder()
                    .txHash(txHash)
                    .outputIndex(i)
                    .address(proxyScriptAddress)
                    .amount(amounts)
                    .inlineDatum(inlineDatumHex)
                    .build();

            for (com.bloxbean.cardano.client.transaction.spec.Asset asset : maybeStateAsset.get().getAssets()) {
                String rawNameHex = asset.getNameAsHex();
                String nameHex = (rawNameHex != null && rawNameHex.startsWith("0x"))
                        ? rawNameHex.substring(2) : rawNameHex;
                String unit = proxyScriptHash + nameHex;
                putPendingStateUtxo(unit, chainedUtxo);
            }
            break;
        }
    }

    private List<Amount> buildAmounts(com.bloxbean.cardano.client.transaction.spec.TransactionOutput output) {
        List<Amount> amounts = new ArrayList<>();
        if (output.getValue().getCoin() != null) {
            amounts.add(Amount.lovelace(output.getValue().getCoin()));
        }
        if (output.getValue().getMultiAssets() == null) return amounts;
        for (MultiAsset ma : output.getValue().getMultiAssets()) {
            for (com.bloxbean.cardano.client.transaction.spec.Asset asset : ma.getAssets()) {
                String rawNameHex = asset.getNameAsHex();
                String nameHex = (rawNameHex != null && rawNameHex.startsWith("0x"))
                        ? rawNameHex.substring(2) : rawNameHex;
                amounts.add(Amount.asset(ma.getPolicyId() + nameHex, asset.getValue()));
            }
        }
        return amounts;
    }

    void clearWalletLocks() {
        lockedWalletUtxos.clear();
    }

    void clearCollateralLocks() {
        lockedCollateralUtxos.clear();
    }

    private String walletKey(String txHash, int index) {
        return txHash + ":" + index;
    }
}
