package io.uverify.backend.extension.aymvision.voucher;

import io.uverify.backend.extension.aymvision.auth.HandshakeService;
import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.exception.VoucherNotFoundException;
import io.uverify.backend.extension.aymvision.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final RedeemedVoucherRepository redeemedRepo;
    private final ContentService contentService;
    private final AymUserProfileRepository profileRepo;
    private final AymVisionProperties properties;

    public VoucherService(VoucherRepository voucherRepo,
                          RedeemedVoucherRepository redeemedRepo,
                          ContentService contentService,
                          AymUserProfileRepository profileRepo,
                          AymVisionProperties properties) {
        this.voucherRepo = voucherRepo;
        this.redeemedRepo = redeemedRepo;
        this.contentService = contentService;
        this.profileRepo = profileRepo;
        this.properties = properties;
    }

    public List<VoucherEntity> create(String contentId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> voucherRepo.save(new VoucherEntity(contentId)))
                .toList();
    }

    @Transactional
    public RedeemResult redeem(String publicKeyHex, String profileId, UUID voucherId) {
        VoucherEntity voucher = voucherRepo.findById(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));

        boolean isFirstOwnership = !profileRepo.existsById(new AymUserProfileId(publicKeyHex, profileId));

        // throws AlreadyOwnedException (→409) if already owned — rolls back, voucher survives
        contentService.grantContent(publicKeyHex, profileId, voucher.getContentId(), "VOUCHER");

        // only reached on success
        voucherRepo.delete(voucher);
        redeemedRepo.save(new RedeemedVoucherEntity(voucherId, voucher.getContentId(), publicKeyHex, profileId));

        List<String> ownedContent = contentService.getContent(publicKeyHex, profileId)
                .stream().map(AymUserContentEntity::getContentId).toList();

        RegistrationCertificate regCert = null;
        if (isFirstOwnership) {
            profileRepo.findById(new AymUserProfileId(publicKeyHex, profileId))
                    .ifPresent(p -> {});  // ensure profile is loaded
            var profile = profileRepo.findById(new AymUserProfileId(publicKeyHex, profileId)).orElseThrow();
            regCert = buildRegistrationCertificate(publicKeyHex, profileId, profile.getSalt());
        }

        return new RedeemResult(voucher.getContentId(), ownedContent, regCert);
    }

    private RegistrationCertificate buildRegistrationCertificate(String publicKeyHex,
                                                                  String profileId,
                                                                  String saltHex) {
        String raw = publicKeyHex + profileId + saltHex;
        String hash = sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
        String verifyUrl = properties.getUiBaseUrl() + "/verify/" + hash
                + "?pk=" + publicKeyHex + "&profileId=" + profileId + "&salt=" + saltHex;
        return new RegistrationCertificate(hash, saltHex, verifyUrl);
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
