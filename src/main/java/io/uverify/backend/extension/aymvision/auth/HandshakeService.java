package io.uverify.backend.extension.aymvision.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.exception.InvalidHandshakeException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class HandshakeService {

    private static final int NONCE_TTL_SECONDS = 120;
    private static final int PUBLIC_KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 64;

    /** key=nonce hex, value=publicKeyHex — auto-expires after 120s */
    private final Cache<String, String> nonceCache = Caffeine.newBuilder()
            .expireAfterWrite(NONCE_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    /** Tracks used nonces briefly to prevent replay within TTL window */
    private final Cache<String, Boolean> usedNonces = Caffeine.newBuilder()
            .expireAfterWrite(NONCE_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    private final AymVisionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public HandshakeService(AymVisionProperties properties) {
        this.properties = properties;
    }

    /**
     * Issues a single-use nonce bound to the given public key.
     * @return 32-byte nonce as lower-case hex
     */
    public String issueNonce(String publicKeyHex) {
        validatePublicKeyFormat(publicKeyHex);
        byte[] nonceBytes = new byte[32];
        secureRandom.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        nonceCache.put(nonce, publicKeyHex.toLowerCase());
        return nonce;
    }

    /**
     * Verifies the Ed25519 handshake signature.
     * Canonical payload: "AYM1|{method}|{path}|{nonce}|{sha256Hex(body)}"
     *
     * @throws InvalidHandshakeException on any verification failure
     */
    public HandshakeResult verify(String publicKeyHex, String nonce, String signatureHex,
                                  String method, String path, byte[] body) {
        if (publicKeyHex == null || nonce == null || signatureHex == null) {
            throw new InvalidHandshakeException("Missing handshake headers");
        }

        String pubKeyLower = publicKeyHex.toLowerCase();

        // 1. Check nonce exists and belongs to this public key
        String boundPublicKey = nonceCache.getIfPresent(nonce);
        if (boundPublicKey == null) {
            throw new InvalidHandshakeException("Nonce expired or unknown");
        }
        if (!boundPublicKey.equals(pubKeyLower)) {
            throw new InvalidHandshakeException("Nonce bound to different public key");
        }

        // 2. Check nonce not already used (replay protection)
        if (Boolean.TRUE.equals(usedNonces.getIfPresent(nonce))) {
            throw new InvalidHandshakeException("Nonce already used");
        }

        // 3. Build canonical payload and verify signature
        String bodyHash = sha256Hex(body != null ? body : new byte[0]);
        String canonicalPayload = "AYM1|" + method.toUpperCase() + "|" + path + "|" + nonce + "|" + bodyHash;
        byte[] payloadBytes = canonicalPayload.getBytes(StandardCharsets.UTF_8);

        boolean valid;
        try {
            byte[] pubKeyBytes = HexFormat.of().parseHex(pubKeyLower);
            byte[] sigBytes = HexFormat.of().parseHex(signatureHex.toLowerCase());
            if (pubKeyBytes.length != PUBLIC_KEY_BYTES) {
                throw new InvalidHandshakeException("Invalid public key length");
            }
            if (sigBytes.length != SIGNATURE_BYTES) {
                throw new InvalidHandshakeException("Invalid signature length");
            }
            valid = verifyEd25519(pubKeyBytes, payloadBytes, sigBytes);
        } catch (IllegalArgumentException e) {
            throw new InvalidHandshakeException("Malformed hex in handshake: " + e.getMessage());
        }

        if (!valid) {
            throw new InvalidHandshakeException("Signature verification failed");
        }

        // 4. Mark nonce as used (invalidate from active store)
        nonceCache.invalidate(nonce);
        usedNonces.put(nonce, Boolean.TRUE);

        return new HandshakeResult(pubKeyLower);
    }

    /** Returns true if the given public key matches the configured master key. */
    public boolean isMasterKey(String publicKeyHex) {
        if (publicKeyHex == null || properties.getMasterPublicKey() == null) return false;
        return properties.getMasterPublicKey().equalsIgnoreCase(publicKeyHex);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean verifyEd25519(byte[] publicKey, byte[] payload, byte[] signature) {
        var verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
        verifier.update(payload, 0, payload.length);
        return verifier.verifySignature(signature);
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void validatePublicKeyFormat(String publicKeyHex) {
        if (publicKeyHex == null || !publicKeyHex.matches("[0-9a-fA-F]{64}")) {
            throw new InvalidHandshakeException("publicKey must be 32 bytes (64 hex chars)");
        }
    }

    public record HandshakeResult(String publicKeyHex) {}
}
