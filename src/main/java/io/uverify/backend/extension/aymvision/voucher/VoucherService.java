package io.uverify.backend.extension.aymvision.voucher;

import io.uverify.backend.extension.aymvision.anchor.RegistrationCertificateService;
import io.uverify.backend.extension.aymvision.exception.VoucherNotFoundException;
import io.uverify.backend.extension.aymvision.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final RedeemedVoucherRepository redeemedRepo;
    private final ContentService contentService;
    private final AymUserProfileRepository profileRepo;
    private final RegistrationCertificateService certService;

    public VoucherService(VoucherRepository voucherRepo,
                          RedeemedVoucherRepository redeemedRepo,
                          ContentService contentService,
                          AymUserProfileRepository profileRepo,
                          RegistrationCertificateService certService) {
        this.voucherRepo = voucherRepo;
        this.redeemedRepo = redeemedRepo;
        this.contentService = contentService;
        this.profileRepo = profileRepo;
        this.certService = certService;
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
            regCert = certService.register(publicKeyHex, profileId);
        }

        return new RedeemResult(voucher.getContentId(), ownedContent, regCert);
    }
}
