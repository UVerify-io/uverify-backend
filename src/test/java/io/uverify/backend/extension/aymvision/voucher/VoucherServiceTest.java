package io.uverify.backend.extension.aymvision.voucher;

import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.exception.VoucherNotFoundException;
import io.uverify.backend.extension.aymvision.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    private static final String PUB_KEY = "bb".repeat(32);
    private static final String PROFILE_A = "profile-a";
    private static final String PROFILE_B = "profile-b";
    private static final String CONTENT_ID = "s1e01";

    @Mock VoucherRepository voucherRepo;
    @Mock RedeemedVoucherRepository redeemedRepo;
    @Mock ContentService contentService;
    @Mock AymUserProfileRepository profileRepo;

    private VoucherService service;

    @BeforeEach
    void setUp() {
        AymVisionProperties props = new AymVisionProperties();
        props.setUiBaseUrl("https://app.example.com");
        service = new VoucherService(voucherRepo, redeemedRepo, contentService, profileRepo, props);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_persistsRequestedCountWithContentId() {
        when(voucherRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VoucherEntity> vouchers = service.create(CONTENT_ID, 3);

        assertThat(vouchers).hasSize(3);
        assertThat(vouchers).allMatch(v -> CONTENT_ID.equals(v.getContentId()));
        assertThat(vouchers).allMatch(v -> v.getId() != null);
        verify(voucherRepo, times(3)).save(any());
    }

    @Test
    void create_defaultsToOneVoucher() {
        when(voucherRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VoucherEntity> vouchers = service.create(CONTENT_ID, 1);

        assertThat(vouchers).hasSize(1);
    }

    // ── redeem — happy path ───────────────────────────────────────────────────

    @Test
    void redeem_movesRowAndGrantsContent() {
        UUID vid = UUID.randomUUID();
        VoucherEntity voucher = new VoucherEntity(CONTENT_ID);
        when(voucherRepo.findById(vid)).thenReturn(Optional.of(voucher));
        when(profileRepo.existsById(any())).thenReturn(true); // not first ownership
        AymUserContentEntity contentEntity = new AymUserContentEntity(PUB_KEY, PROFILE_A, CONTENT_ID, "VOUCHER");
        when(contentService.getContent(PUB_KEY, PROFILE_A)).thenReturn(List.of(contentEntity));
        when(contentService.grantContent(any(), any(), any(), any())).thenReturn(contentEntity);
        when(redeemedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedeemResult result = service.redeem(PUB_KEY, PROFILE_A, vid);

        verify(voucherRepo).delete(voucher);
        verify(redeemedRepo).save(any(RedeemedVoucherEntity.class));
        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.ownedContent()).contains(CONTENT_ID);
    }

    @Test
    void redeem_throwsVoucherNotFound_whenMissing() {
        UUID vid = UUID.randomUUID();
        when(voucherRepo.findById(vid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(PUB_KEY, PROFILE_A, vid))
                .isInstanceOf(VoucherNotFoundException.class);

        verify(voucherRepo, never()).delete(any());
        verify(contentService, never()).grantContent(any(), any(), any(), any());
    }

    @Test
    void redeem_throwsAlreadyOwned_andVoucherSurvives() {
        UUID vid = UUID.randomUUID();
        VoucherEntity voucher = new VoucherEntity(CONTENT_ID);
        when(voucherRepo.findById(vid)).thenReturn(Optional.of(voucher));
        when(profileRepo.existsById(any())).thenReturn(true);
        doThrow(new AlreadyOwnedException(CONTENT_ID, PROFILE_A))
                .when(contentService).grantContent(PUB_KEY, PROFILE_A, CONTENT_ID, "VOUCHER");

        assertThatThrownBy(() -> service.redeem(PUB_KEY, PROFILE_A, vid))
                .isInstanceOf(AlreadyOwnedException.class);

        // voucher was NOT deleted
        verify(voucherRepo, never()).delete(any());
        verify(redeemedRepo, never()).save(any());
    }

    // ── sibling profiles ──────────────────────────────────────────────────────

    @Test
    void redeem_twoVouchersForSameContent_siblingProfiles_bothSucceed() {
        UUID vidA = UUID.randomUUID();
        UUID vidB = UUID.randomUUID();
        VoucherEntity vA = new VoucherEntity(CONTENT_ID);
        VoucherEntity vB = new VoucherEntity(CONTENT_ID);

        when(voucherRepo.findById(vidA)).thenReturn(Optional.of(vA));
        when(voucherRepo.findById(vidB)).thenReturn(Optional.of(vB));
        when(profileRepo.existsById(new AymUserProfileId(PUB_KEY, PROFILE_A))).thenReturn(true);
        when(profileRepo.existsById(new AymUserProfileId(PUB_KEY, PROFILE_B))).thenReturn(true);

        AymUserContentEntity contentA = new AymUserContentEntity(PUB_KEY, PROFILE_A, CONTENT_ID, "VOUCHER");
        AymUserContentEntity contentB = new AymUserContentEntity(PUB_KEY, PROFILE_B, CONTENT_ID, "VOUCHER");
        when(contentService.grantContent(eq(PUB_KEY), eq(PROFILE_A), eq(CONTENT_ID), any()))
                .thenReturn(contentA);
        when(contentService.grantContent(eq(PUB_KEY), eq(PROFILE_B), eq(CONTENT_ID), any()))
                .thenReturn(contentB);
        when(contentService.getContent(PUB_KEY, PROFILE_A)).thenReturn(List.of(contentA));
        when(contentService.getContent(PUB_KEY, PROFILE_B)).thenReturn(List.of(contentB));
        when(redeemedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedeemResult resultA = service.redeem(PUB_KEY, PROFILE_A, vidA);
        RedeemResult resultB = service.redeem(PUB_KEY, PROFILE_B, vidB);

        assertThat(resultA.contentId()).isEqualTo(CONTENT_ID);
        assertThat(resultB.contentId()).isEqualTo(CONTENT_ID);
        verify(voucherRepo).delete(vA);
        verify(voucherRepo).delete(vB);
    }

    // ── registrationCertificate ───────────────────────────────────────────────

    @Test
    void redeem_returnsRegistrationCertificate_onFirstOwnership() {
        UUID vid = UUID.randomUUID();
        VoucherEntity voucher = new VoucherEntity(CONTENT_ID);
        when(voucherRepo.findById(vid)).thenReturn(Optional.of(voucher));
        when(profileRepo.existsById(any())).thenReturn(false); // first ownership

        AymUserProfileEntity profile = new AymUserProfileEntity(PUB_KEY, PROFILE_A, "cc".repeat(32), "aa".repeat(28));
        when(profileRepo.findById(any())).thenReturn(Optional.of(profile));

        AymUserContentEntity contentEntity = new AymUserContentEntity(PUB_KEY, PROFILE_A, CONTENT_ID, "VOUCHER");
        when(contentService.grantContent(any(), any(), any(), any())).thenReturn(contentEntity);
        when(contentService.getContent(PUB_KEY, PROFILE_A)).thenReturn(List.of(contentEntity));
        when(redeemedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedeemResult result = service.redeem(PUB_KEY, PROFILE_A, vid);

        assertThat(result.registrationCertificate()).isNotNull();
        assertThat(result.registrationCertificate().hash()).matches("[0-9a-f]{64}");
        assertThat(result.registrationCertificate().verifyUrl()).contains("https://app.example.com/verify/");
    }

    @Test
    void redeem_noRegistrationCertificate_whenNotFirstOwnership() {
        UUID vid = UUID.randomUUID();
        VoucherEntity voucher = new VoucherEntity(CONTENT_ID);
        when(voucherRepo.findById(vid)).thenReturn(Optional.of(voucher));
        when(profileRepo.existsById(any())).thenReturn(true); // not first ownership

        AymUserContentEntity contentEntity = new AymUserContentEntity(PUB_KEY, PROFILE_A, CONTENT_ID, "VOUCHER");
        when(contentService.grantContent(any(), any(), any(), any())).thenReturn(contentEntity);
        when(contentService.getContent(PUB_KEY, PROFILE_A)).thenReturn(List.of(contentEntity));
        when(redeemedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedeemResult result = service.redeem(PUB_KEY, PROFILE_A, vid);

        assertThat(result.registrationCertificate()).isNull();
    }
}
