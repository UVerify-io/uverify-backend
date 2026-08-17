package io.uverify.backend.extension.aymvision.stripe;

import io.uverify.backend.extension.aymvision.anchor.RegistrationCertificateService;
import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.exception.KeyMismatchException;
import io.uverify.backend.extension.aymvision.exception.PaymentRequiredException;
import io.uverify.backend.extension.aymvision.exception.SessionAlreadyUsedException;
import io.uverify.backend.extension.aymvision.user.*;
import io.uverify.backend.extension.aymvision.voucher.RegistrationCertificate;
import io.uverify.backend.extension.aymvision.voucher.RedeemResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class StripePurchaseServiceTest {

    private static final String PUB_KEY = "cc".repeat(32);
    private static final String PROFILE_ID = "profile-a";
    private static final String SESSION_ID = "cs_test_abc123";
    private static final String PRODUCT_ID = "prod_xyz";
    private static final String CONTENT_ID = "s1e01";

    @Mock StripeGateway stripeGateway;
    @Mock StripePurchaseRepository purchaseRepo;
    @Mock ContentService contentService;
    @Mock AymUserProfileRepository profileRepo;
    @Mock RegistrationCertificateService certService;

    private StripePurchaseService service;
    private String validProfileHash;

    @BeforeEach
    void setUp() {
        AymVisionProperties props = new AymVisionProperties();
        props.setUiBaseUrl("https://app.example.com");
        props.getStripe().setProducts(Map.of(PRODUCT_ID, CONTENT_ID));
        service = new StripePurchaseService(stripeGateway, purchaseRepo, contentService, profileRepo, props, certService);
        validProfileHash = StripePurchaseService.profileHash(PUB_KEY, PROFILE_ID);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void verifyAndGrant_grantsContentAndStoresSession() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "paid", validProfileHash, PRODUCT_ID));
        when(purchaseRepo.existsById(SESSION_ID)).thenReturn(false);
        when(profileRepo.existsById(any())).thenReturn(true); // not first ownership
        AymUserContentEntity content = new AymUserContentEntity(PUB_KEY, PROFILE_ID, CONTENT_ID, "STRIPE");
        when(contentService.grantContent(any(), any(), any(), any())).thenReturn(content);
        when(contentService.getContent(PUB_KEY, PROFILE_ID)).thenReturn(List.of(content));
        when(purchaseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedeemResult result = service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.ownedContent()).contains(CONTENT_ID);
        verify(purchaseRepo).save(any(StripePurchaseEntity.class));
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
        String hash = StripePurchaseService.profileHash(PUB_KEY, PROFILE_ID);
        assertThat(hash).matches("[0-9a-f]{56}"); // 28 bytes = 56 hex chars
    }

    @Test
    void profileHash_isDeterministic() {
        assertThat(StripePurchaseService.profileHash(PUB_KEY, PROFILE_ID))
                .isEqualTo(StripePurchaseService.profileHash(PUB_KEY, PROFILE_ID));
    }

    // ── registration certificate ──────────────────────────────────────────────

    @Test
    void verifyAndGrant_returnsRegistrationCertificate_onFirstOwnership() {
        when(stripeGateway.retrieveSession(SESSION_ID))
                .thenReturn(new CheckoutInfo(SESSION_ID, "paid", validProfileHash, PRODUCT_ID));
        when(purchaseRepo.existsById(SESSION_ID)).thenReturn(false);
        when(profileRepo.existsById(any())).thenReturn(false); // first ownership
        AymUserContentEntity content = new AymUserContentEntity(PUB_KEY, PROFILE_ID, CONTENT_ID, "STRIPE");
        when(contentService.grantContent(any(), any(), any(), any())).thenReturn(content);
        when(contentService.getContent(PUB_KEY, PROFILE_ID)).thenReturn(List.of(content));
        when(purchaseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegistrationCertificate fakeCert = new RegistrationCertificate("a".repeat(64), "dd".repeat(32),
                "https://app.example.com/verify/" + "a".repeat(64));
        when(certService.register(PUB_KEY, PROFILE_ID)).thenReturn(fakeCert);

        RedeemResult result = service.verifyAndGrant(PUB_KEY, PROFILE_ID, SESSION_ID);

        assertThat(result.registrationCertificate()).isNotNull();
        assertThat(result.registrationCertificate().hash()).matches("[0-9a-f]{64}");
    }
}
