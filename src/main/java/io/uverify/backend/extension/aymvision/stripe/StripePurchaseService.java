package io.uverify.backend.extension.aymvision.stripe;

import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.exception.KeyMismatchException;
import io.uverify.backend.extension.aymvision.exception.PaymentRequiredException;
import io.uverify.backend.extension.aymvision.exception.SessionAlreadyUsedException;
import io.uverify.backend.extension.aymvision.user.*;
import io.uverify.backend.extension.aymvision.voucher.RegistrationCertificate;
import io.uverify.backend.extension.aymvision.voucher.RedeemResult;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class StripePurchaseService {

    private final StripeGateway stripeGateway;
    private final StripePurchaseRepository purchaseRepo;
    private final ContentService contentService;
    private final AymUserProfileRepository profileRepo;
    private final AymVisionProperties properties;

    public StripePurchaseService(StripeGateway stripeGateway,
                                 StripePurchaseRepository purchaseRepo,
                                 ContentService contentService,
                                 AymUserProfileRepository profileRepo,
                                 AymVisionProperties properties) {
        this.stripeGateway = stripeGateway;
        this.purchaseRepo = purchaseRepo;
        this.contentService = contentService;
        this.profileRepo = profileRepo;
        this.properties = properties;
    }

    @Transactional
    public RedeemResult verifyAndGrant(String publicKeyHex, String profileId, String sessionId) {
        CheckoutInfo info = stripeGateway.retrieveSession(sessionId);

        if (!"paid".equals(info.paymentStatus())) {
            throw new PaymentRequiredException(sessionId);
        }

        String expectedRef = profileHash(publicKeyHex, profileId);
        if (!expectedRef.equals(info.clientReferenceId())) {
            throw new KeyMismatchException(
                    "client_reference_id does not match caller's profileHash");
        }

        if (purchaseRepo.existsById(sessionId)) {
            throw new SessionAlreadyUsedException(sessionId);
        }

        String contentId = properties.getStripe().getProducts().get(info.productId());
        if (contentId == null) {
            throw new KeyMismatchException("Unmapped Stripe product: " + info.productId());
        }

        boolean isFirstOwnership = !profileRepo.existsById(new AymUserProfileId(publicKeyHex, profileId));

        contentService.grantContent(publicKeyHex, profileId, contentId, "STRIPE");

        purchaseRepo.save(new StripePurchaseEntity(sessionId, publicKeyHex, profileId, contentId));

        List<String> ownedContent = contentService.getContent(publicKeyHex, profileId)
                .stream().map(AymUserContentEntity::getContentId).toList();

        RegistrationCertificate regCert = null;
        if (isFirstOwnership) {
            var profile = profileRepo.findById(new AymUserProfileId(publicKeyHex, profileId)).orElseThrow();
            regCert = buildRegistrationCertificate(publicKeyHex, profileId, profile.getSalt());
        }

        return new RedeemResult(contentId, ownedContent, regCert);
    }

    static String profileHash(String publicKeyHex, String profileId) {
        byte[] input = (publicKeyHex + profileId).getBytes(StandardCharsets.UTF_8);
        Blake2bDigest digest = new Blake2bDigest(224);
        digest.update(input, 0, input.length);
        byte[] result = new byte[28];
        digest.doFinal(result, 0);
        return HexFormat.of().formatHex(result);
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private RegistrationCertificate buildRegistrationCertificate(String publicKeyHex,
                                                                  String profileId,
                                                                  String saltHex) {
        String hash = sha256Hex((publicKeyHex + profileId + saltHex).getBytes(StandardCharsets.UTF_8));
        String verifyUrl = properties.getUiBaseUrl() + "/verify/" + hash
                + "?pk=" + publicKeyHex + "&profileId=" + profileId + "&salt=" + saltHex;
        return new RegistrationCertificate(hash, saltHex, verifyUrl);
    }
}
