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

package io.uverify.backend.extension.service;

import io.uverify.backend.extension.config.AymVisionProperties;
import io.uverify.backend.extension.exception.InvalidHandshakeException;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@EnabledIf(
        expression = "${extensions.aym-vision.enabled}",
        loadContext = true,
        reason = "AYM Vision extension must be enabled for this test"
)
class AymHandshakeServiceTest {

    private AymHandshakeService service;
    private AymVisionProperties properties;

    // In-test Ed25519 keypair
    private Ed25519PrivateKeyParameters privateKey;
    private String publicKeyHex;

    @BeforeEach
    void setUp() {
        properties = new AymVisionProperties();

        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();
        generator.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        Ed25519PublicKeyParameters pubKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
        publicKeyHex = HexFormat.of().formatHex(pubKey.getEncoded());

        service = new AymHandshakeService(properties);
    }

    // ── issueNonce ────────────────────────────────────────────────────────────

    @Test
    void issueNonce_returns64HexChars() {
        String nonce = service.issueNonce(publicKeyHex);
        assertThat(nonce).matches("[0-9a-f]{64}");
    }

    @Test
    void issueNonce_rejectsInvalidPublicKey() {
        assertThatThrownBy(() -> service.issueNonce("not-hex"))
                .isInstanceOf(InvalidHandshakeException.class);
    }

    // ── verify — happy path ───────────────────────────────────────────────────

    @Test
    void verify_acceptsValidSignature() {
        String nonce = service.issueNonce(publicKeyHex);
        String method = "GET";
        String path = "/api/v1/extension/aym-vision/content";
        byte[] body = new byte[0];

        String bodyHash = AymHandshakeService.sha256Hex(body);
        String canonical = "AYM1|" + method + "|" + path + "|" + nonce + "|" + bodyHash;
        String sig = sign(canonical);

        AymHandshakeService.HandshakeResult result = service.verify(publicKeyHex, nonce, sig, method, path, body);
        assertThat(result.publicKeyHex()).isEqualTo(publicKeyHex.toLowerCase());
    }

    @Test
    void verify_acceptsSignatureWithBody() {
        String nonce = service.issueNonce(publicKeyHex);
        String method = "POST";
        String path = "/api/v1/extension/aym-vision/voucher/redeem";
        byte[] body = "{\"voucherId\":\"x\"}".getBytes(StandardCharsets.UTF_8);

        String bodyHash = AymHandshakeService.sha256Hex(body);
        String canonical = "AYM1|" + method + "|" + path + "|" + nonce + "|" + bodyHash;
        String sig = sign(canonical);

        AymHandshakeService.HandshakeResult result = service.verify(publicKeyHex, nonce, sig, method, path, body);
        assertThat(result.publicKeyHex()).isNotBlank();
    }

    // ── verify — failure cases ────────────────────────────────────────────────

    @Test
    void verify_rejectsExpiredOrUnknownNonce() {
        assertThatThrownBy(() -> service.verify(publicKeyHex, "deadbeef", "00".repeat(64), "GET", "/path", new byte[0]))
                .isInstanceOf(InvalidHandshakeException.class)
                .hasMessageContaining("Nonce");
    }

    @Test
    void verify_rejectsReusedNonce() {
        String nonce = service.issueNonce(publicKeyHex);
        String method = "GET";
        String path = "/api/v1/extension/aym-vision/content";
        byte[] body = new byte[0];
        String canonical = "AYM1|GET|" + path + "|" + nonce + "|" + AymHandshakeService.sha256Hex(body);
        String sig = sign(canonical);

        // first use succeeds
        service.verify(publicKeyHex, nonce, sig, method, path, body);

        // second use must fail
        assertThatThrownBy(() -> service.verify(publicKeyHex, nonce, sig, method, path, body))
                .isInstanceOf(InvalidHandshakeException.class);
    }

    @Test
    void verify_rejectsTamperedPayload() {
        String nonce = service.issueNonce(publicKeyHex);
        String method = "GET";
        String path = "/api/v1/extension/aym-vision/content";
        byte[] body = new byte[0];

        // sign with correct payload
        String canonical = "AYM1|GET|" + path + "|" + nonce + "|" + AymHandshakeService.sha256Hex(body);
        String sig = sign(canonical);

        // verify with different path (tampered)
        assertThatThrownBy(() -> service.verify(publicKeyHex, nonce, sig, method, "/tampered", body))
                .isInstanceOf(InvalidHandshakeException.class);
    }

    @Test
    void verify_rejectsMissingHeaders() {
        assertThatThrownBy(() -> service.verify(null, null, null, "GET", "/path", new byte[0]))
                .isInstanceOf(InvalidHandshakeException.class);
    }

    // ── isMasterKey ───────────────────────────────────────────────────────────

    @Test
    void isMasterKey_trueWhenMatchesConfig() {
        properties.setMasterPublicKey(publicKeyHex);
        assertThat(service.isMasterKey(publicKeyHex)).isTrue();
    }

    @Test
    void isMasterKey_caseInsensitive() {
        properties.setMasterPublicKey(publicKeyHex.toUpperCase());
        assertThat(service.isMasterKey(publicKeyHex.toLowerCase())).isTrue();
    }

    @Test
    void isMasterKey_falseForOtherKey() {
        properties.setMasterPublicKey(publicKeyHex);
        String otherKey = "aa".repeat(32);
        assertThat(service.isMasterKey(otherKey)).isFalse();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private String sign(String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(payloadBytes, 0, payloadBytes.length);
        return HexFormat.of().formatHex(signer.generateSignature());
    }
}
