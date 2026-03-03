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

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.dto.FaucetChallengeRequest;
import io.uverify.backend.dto.FaucetChallengeResponse;
import io.uverify.backend.dto.FaucetClaimRequest;
import io.uverify.backend.dto.FaucetClaimResponse;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.util.CardanoUtils;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.cip30.AddressFormat;
import org.cardanofoundation.cip30.CIP30Verifier;
import org.cardanofoundation.cip30.Cip30VerificationResult;
import org.cardanofoundation.cip30.MessageFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Slf4j
@Service
@ConditionalOnProperty(name = "faucet.enabled", havingValue = "true")
public class FaucetService {

    private static final String ACTION_NAME = "FAUCET_REQUEST";

    private final Account faucetAccount;
    private final int utxoCount;
    private final BigInteger lovelacePerUtxo;
    private final long cooldownMs;

    /**
     * Payment-credential-hex → timestamp of last successful claim.
     */
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();

    @Autowired
    private CardanoBlockchainService cardanoBlockchainService;

    @Autowired
    public FaucetService(
            @Value("${faucet.mnemonic:}") String faucetMnemonic,
            @Value("${faucet.utxo-count:3}") int utxoCount,
            @Value("${faucet.utxo-amount-lovelace:10000000}") long utxoAmountLovelace,
            @Value("${faucet.cooldown-ms:120000}") long cooldownMs,
            @Value("${cardano.network}") String network) {

        CardanoNetwork cardanoNetwork = CardanoNetwork.valueOf(network);
        this.utxoCount = utxoCount;
        this.lovelacePerUtxo = BigInteger.valueOf(utxoAmountLovelace);
        this.cooldownMs = cooldownMs;

        if (faucetMnemonic.isEmpty()) {
            log.warn("Faucet mnemonic is not set. Generating a temporary faucet account. " +
                    "This account will have no funds — set FAUCET_MNEMONIC to a pre-funded testnet wallet.");
            this.faucetAccount = new Account(fromCardanoNetwork(cardanoNetwork));
        } else {
            this.faucetAccount = Account.createFromMnemonic(fromCardanoNetwork(cardanoNetwork), faucetMnemonic);
        }

        log.info("Faucet enabled. Faucet address: {}", this.faucetAccount.baseAddress());
    }

    public FaucetChallengeResponse requestChallenge(FaucetChallengeRequest request) {
        String address = request.getAddress();

        if (isInCooldown(address)) {
            return FaucetChallengeResponse.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .error("This address has recently received funds. Please wait before requesting again.")
                    .build();
        }

        long timestamp = System.currentTimeMillis();
        String message = buildMessage(address, timestamp);

        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();
        String signature = HexUtil.encodeHexString(
                edDSASigningProvider.signExtended(message.getBytes(StandardCharsets.UTF_8), faucetAccount.privateKeyBytes()));

        return FaucetChallengeResponse.builder()
                .address(address)
                .message(message)
                .signature(signature)
                .timestamp(timestamp)
                .status(HttpStatus.OK)
                .build();
    }

    public FaucetClaimResponse claimFunds(FaucetClaimRequest request) {
        if (!signaturesAreValid(request)) {
            return FaucetClaimResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .error("The provided signatures are not valid or the request is outdated (older than 10 minutes).")
                    .build();
        }

        if (!hasValidTimeframe(request.getTimestamp())) {
            return FaucetClaimResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .error("The request is outdated. Please request a new challenge.")
                    .build();
        }

        if (isInCooldown(request.getAddress())) {
            return FaucetClaimResponse.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .error("This address has recently received funds. Please wait before requesting again.")
                    .build();
        }

        // Record cooldown before submitting to prevent race conditions
        String credentialHex = HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(request.getAddress()));
        cooldownMap.put(credentialHex, System.currentTimeMillis());

        try {
            Result<String> result = cardanoBlockchainService.sendAda(
                    faucetAccount, request.getAddress(), utxoCount, lovelacePerUtxo);

            if (!result.isSuccessful()) {
                // Remove cooldown entry so the user can retry
                cooldownMap.remove(credentialHex);
                log.error("Faucet transaction failed: {}", result.getResponse());
                return FaucetClaimResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .error("Failed to submit faucet transaction: " + result.getResponse())
                        .build();
            }

            log.info("Faucet sent {} UTXOs of {} lovelace to {} (tx: {})",
                    utxoCount, lovelacePerUtxo, request.getAddress(), result.getValue());

            return FaucetClaimResponse.builder()
                    .txHash(result.getValue())
                    .status(HttpStatus.OK)
                    .build();

        } catch (CborSerializationException | ApiException e) {
            cooldownMap.remove(credentialHex);
            log.error("Faucet transaction error for address {}", request.getAddress(), e);
            return FaucetClaimResponse.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .error("Failed to submit faucet transaction.")
                    .build();
        }
    }

    private boolean isInCooldown(String address) {
        String credentialHex;
        try {
            credentialHex = HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(address));
        } catch (IllegalArgumentException e) {
            return false;
        }

        Long lastClaim = cooldownMap.get(credentialHex);
        if (lastClaim == null) {
            return false;
        }

        if (System.currentTimeMillis() - lastClaim >= cooldownMs) {
            cooldownMap.remove(credentialHex);
            return false;
        }

        return true;
    }

    private boolean signaturesAreValid(FaucetClaimRequest request) {
        String expectedMessage = buildMessage(request.getAddress(), request.getTimestamp());
        if (!expectedMessage.equals(request.getMessage())) {
            return false;
        }

        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();
        boolean backendSignatureValid = edDSASigningProvider.verify(
                HexUtil.decodeHexString(request.getSignature()),
                request.getMessage().getBytes(StandardCharsets.UTF_8),
                faucetAccount.publicKeyBytes());

        if (!backendSignatureValid) {
            return false;
        }

        CIP30Verifier cip30Verifier = new CIP30Verifier(request.getUserSignature(), request.getUserPublicKey());
        Cip30VerificationResult result = cip30Verifier.verify();
        Optional<String> optionalUserAddress = result.getAddress(AddressFormat.TEXT);

        if (optionalUserAddress.isEmpty()) {
            return false;
        }

        return result.isValid()
                && expectedMessage.equals(result.getMessage(MessageFormat.TEXT))
                && request.getAddress().equals(optionalUserAddress.get());
    }

    private boolean hasValidTimeframe(long timestamp) {
        return Math.abs(System.currentTimeMillis() - timestamp) <= 600_000;
    }

    private String buildMessage(String address, long timestamp) {
        return "[" + ACTION_NAME + "@" + timestamp + "] Please sign this message with your private key to verify, " +
                "that you are the owner of the address " + address + ".";
    }
}
