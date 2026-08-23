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

import io.uverify.backend.extension.entity.AymStripePurchaseEntity;
import io.uverify.backend.extension.repository.AymStripePurchaseRepository;
import io.uverify.backend.extension.entity.AymUserContentEntity;
import io.uverify.backend.extension.dto.aymvision.CheckoutInfo;
import io.uverify.backend.extension.config.AymVisionProperties;
import io.uverify.backend.extension.exception.KeyMismatchException;
import io.uverify.backend.extension.exception.PaymentRequiredException;
import io.uverify.backend.extension.exception.SessionAlreadyUsedException;
import io.uverify.backend.extension.dto.aymvision.RedeemResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@EnabledIf(
        expression = "${extensions.aym-vision.enabled}",
        loadContext = true,
        reason = "AYM Vision extension must be enabled for this test"
)
class AymStripePurchaseServiceTest {

    private static final String PUB_KEY = "cc".repeat(32);
    private static final String PROFILE_ID = "profile-a";
    private static final String SESSION_ID = "cs_test_abc123";
    private static final String PRODUCT_ID = "prod_xyz";
    private static final String CONTENT_ID = "s1e01";

    @Mock AymStripeGateway stripeGateway;
    @Mock AymStripePurchaseRepository purchaseRepo;
    @Mock AymContentService contentService;

    private AymStripePurchaseService service;
    private String validProfileHash;

    @BeforeEach
    void setUp() {
        AymVisionProperties props = new AymVisionProperties();
        props.setUiBaseUrl("https://app.example.com");
        props.getStripe().setProducts(Map.of(PRODUCT_ID, CONTENT_ID));
        service = new AymStripePurchaseService(stripeGateway, purchaseRepo, contentService, props);
        validProfileHash = AymStripePurchaseService.profileHash(PUB_KEY, PROFILE_ID);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void verifyAndGrant_grantsContentAndStoresSession() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "paid", validProfileHash, PRODUCT_ID));
        when(purchaseRepo.existsById(SESSION_ID)).thenReturn(false);
        AymUserContentEntity content = new AymUserContentEntity(PUB_KEY, PROFILE_ID, CONTENT_ID, "STRIPE");
        when(contentService.grantContent(any(), any(), any(), any())).thenReturn(content);
        when(contentService.getContent(PUB_KEY, PROFILE_ID)).thenReturn(List.of(content));
        when(purchaseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedeemResult result = service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.ownedContent()).contains(CONTENT_ID);
        verify(purchaseRepo).save(any(AymStripePurchaseEntity.class));
    }

    // ── failure cases ─────────────────────────────────────────────────────────

    @Test
    void verifyAndGrant_throws402_whenNotPaid() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "unpaid", validProfileHash, PRODUCT_ID));

        assertThatThrownBy(() -> service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID))
                .isInstanceOf(PaymentRequiredException.class);
    }

    @Test
    void verifyAndGrant_throws422_whenClientReferenceIdMismatch() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "paid", "wrong-ref", PRODUCT_ID));

        assertThatThrownBy(() -> service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID))
                .isInstanceOf(KeyMismatchException.class)
                .hasMessageContaining("profileHash");
    }

    @Test
    void verifyAndGrant_throws409_whenSessionAlreadyUsed() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "paid", validProfileHash, PRODUCT_ID));
        when(purchaseRepo.existsById(SESSION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID))
                .isInstanceOf(SessionAlreadyUsedException.class);
    }

    @Test
    void verifyAndGrant_throws422_whenProductUnmapped() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "paid", validProfileHash, "unmapped_product"));
        when(purchaseRepo.existsById(SESSION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID))
                .isInstanceOf(KeyMismatchException.class)
                .hasMessageContaining("Unmapped Stripe product");
    }

    // ── profileHash ───────────────────────────────────────────────────────────

    @Test
    void profileHash_is28BytesHex() {
        String hash = AymStripePurchaseService.profileHash(PUB_KEY, PROFILE_ID);
        assertThat(hash).matches("[0-9a-f]{56}"); // 28 bytes = 56 hex chars
    }

    @Test
    void profileHash_isDeterministic() {
        assertThat(AymStripePurchaseService.profileHash(PUB_KEY, PROFILE_ID))
                .isEqualTo(AymStripePurchaseService.profileHash(PUB_KEY, PROFILE_ID));
    }
}
