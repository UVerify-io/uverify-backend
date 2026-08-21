package io.uverify.backend.extension.aymvision.stripe;

import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.exception.KeyMismatchException;
import io.uverify.backend.extension.aymvision.exception.PaymentRequiredException;
import io.uverify.backend.extension.aymvision.exception.SessionAlreadyUsedException;
import io.uverify.backend.extension.aymvision.user.*;
import io.uverify.backend.extension.aymvision.voucher.RedeemResult;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@Service
public class StripePurchaseService {

    private final StripeGateway stripeGateway;
    private final StripePurchaseRepository purchaseRepo;
    private final ContentService contentService;
    private final AymVisionProperties properties;

    public StripePurchaseService(StripeGateway stripeGateway,
                                 StripePurchaseRepository purchaseRepo,
                                 ContentService contentService,
                                 AymVisionProperties properties) {
        this.stripeGateway = stripeGateway;
        this.purchaseRepo = purchaseRepo;
        this.contentService = contentService;
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

        contentService.grantContent(publicKeyHex, profileId, contentId, "STRIPE");

        purchaseRepo.save(new StripePurchaseEntity(sessionId, publicKeyHex, profileId, contentId));

        List<String> ownedContent = contentService.getContent(publicKeyHex, profileId)
                .stream().map(AymUserContentEntity::getContentId).toList();

        return new RedeemResult(contentId, ownedContent);
    }

    static String profileHash(String publicKeyHex, String profileId) {
        byte[] pubKeyBytes = HexFormat.of().parseHex(publicKeyHex.toLowerCase());
        byte[] profileBytes = profileId.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[pubKeyBytes.length + profileBytes.length];
        System.arraycopy(pubKeyBytes, 0, input, 0, pubKeyBytes.length);
        System.arraycopy(profileBytes, 0, input, pubKeyBytes.length, profileBytes.length);
        Blake2bDigest digest = new Blake2bDigest(224);
        digest.update(input, 0, input.length);
        byte[] result = new byte[28];
        digest.doFinal(result, 0);
        return HexFormat.of().formatHex(result);
    }
}
