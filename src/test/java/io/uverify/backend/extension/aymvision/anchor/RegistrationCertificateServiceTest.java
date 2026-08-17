package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.user.AymUserProfileEntity;
import io.uverify.backend.extension.aymvision.user.AymUserProfileId;
import io.uverify.backend.extension.aymvision.user.AymUserProfileRepository;
import io.uverify.backend.extension.aymvision.voucher.RegistrationCertificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationCertificateServiceTest {

    private static final String PUB_KEY    = "bb".repeat(32);
    private static final String PROFILE_ID = "profile-a";
    private static final String SALT_HEX   = "cc".repeat(32);
    private static final String UI_BASE    = "https://app.example.com";

    @Mock AymUserProfileRepository profileRepo;

    private RegistrationCertificateService service;

    @BeforeEach
    void setUp() {
        AymVisionProperties props = new AymVisionProperties();
        props.setUiBaseUrl(UI_BASE);
        service = new RegistrationCertificateService(profileRepo, props);
    }

    @Test
    void register_hashEqualsExpectedSha256() {
        AymUserProfileEntity profile = new AymUserProfileEntity(PUB_KEY, PROFILE_ID, SALT_HEX, "anyHash");
        when(profileRepo.findById(any())).thenReturn(Optional.of(profile));

        RegistrationCertificate cert = service.register(PUB_KEY, PROFILE_ID);

        String expected = RegistrationCertificateService.sha256Hex(
                (PUB_KEY + PROFILE_ID + SALT_HEX).getBytes(StandardCharsets.UTF_8));
        assertThat(cert.hash()).isEqualTo(expected);
        assertThat(cert.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    void register_verifyUrlContainsAllParams() {
        AymUserProfileEntity profile = new AymUserProfileEntity(PUB_KEY, PROFILE_ID, SALT_HEX, "anyHash");
        when(profileRepo.findById(any())).thenReturn(Optional.of(profile));

        RegistrationCertificate cert = service.register(PUB_KEY, PROFILE_ID);

        assertThat(cert.verifyUrl()).startsWith(UI_BASE + "/verify/");
        assertThat(cert.verifyUrl()).contains("pk=" + PUB_KEY);
        assertThat(cert.verifyUrl()).contains("profileId=" + PROFILE_ID);
        assertThat(cert.verifyUrl()).contains("salt=" + SALT_HEX);
    }

    @Test
    void register_secondCallForSameProfileId_doesNotRegisterAgain() {
        // The service itself is stateless — idempotent by same input
        AymUserProfileEntity profile = new AymUserProfileEntity(PUB_KEY, PROFILE_ID, SALT_HEX, "anyHash");
        when(profileRepo.findById(any())).thenReturn(Optional.of(profile));

        RegistrationCertificate cert1 = service.register(PUB_KEY, PROFILE_ID);
        RegistrationCertificate cert2 = service.register(PUB_KEY, PROFILE_ID);

        assertThat(cert1.hash()).isEqualTo(cert2.hash());
        assertThat(cert1.verifyUrl()).isEqualTo(cert2.verifyUrl());
    }

    @Test
    void register_differentProfileIds_produceSeparateCerts() {
        String saltA = "aa".repeat(32);
        String saltB = "bb".repeat(32);
        AymUserProfileEntity profileA = new AymUserProfileEntity(PUB_KEY, "profile-a", saltA, "h1");
        AymUserProfileEntity profileB = new AymUserProfileEntity(PUB_KEY, "profile-b", saltB, "h2");

        when(profileRepo.findById(new AymUserProfileId(PUB_KEY, "profile-a")))
                .thenReturn(Optional.of(profileA));
        when(profileRepo.findById(new AymUserProfileId(PUB_KEY, "profile-b")))
                .thenReturn(Optional.of(profileB));

        RegistrationCertificate certA = service.register(PUB_KEY, "profile-a");
        RegistrationCertificate certB = service.register(PUB_KEY, "profile-b");

        assertThat(certA.hash()).isNotEqualTo(certB.hash());
        assertThat(certA.verifyUrl()).isNotEqualTo(certB.verifyUrl());
    }
}
